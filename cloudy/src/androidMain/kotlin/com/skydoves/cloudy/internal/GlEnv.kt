/*
 * Designed and developed by 2022 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skydoves.cloudy.internal

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Process-wide GLES 3.0 environment for the API 29-32 mirage backend: one dedicated GL thread owning
 * one EGL 3.0 context, plus per-size [ImageReader] render targets. Every GL call runs on the GL thread
 * (an EGL context is single-thread-affine), so callers hand work in via [run] which blocks until the
 * GL thread finishes.
 *
 * Why a thread of its own (spike #2): `GraphicsLayer.toImageBitmap()` is `suspend` — the capture can't
 * happen inside `ContentDrawScope.draw`, so the whole GLES path is already off the draw thread. A
 * dedicated GL thread keeps the EGL context stable across those async captures.
 *
 * ## Zero-copy readback (spike B-1, confirmed on API 30)
 * The context renders into an `ImageReader.getSurface()` window surface; `eglSwapBuffers` pushes the
 * frame into the reader, whose [ImageReader.OnImageAvailableListener] then yields a `HardwareBuffer`
 * that `Bitmap.wrapHardwareBuffer` wraps with no CPU copy. `acquireLatestImage()` polled without the
 * listener returns null, so the listener + latch is required, not optional.
 */
internal object GlEnv {

  private val thread = HandlerThread("mirage-gl").apply { start() }
  private val handler = Handler(thread.looper)

  // Lazily created on the GL thread on first use; guarded by the single-thread affinity (only the GL
  // thread ever touches these).
  private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
  private var context: EGLContext = EGL14.EGL_NO_CONTEXT
  private var config: EGLConfig? = null

  /** Per-size render target (ImageReader + its window surface), reused across frames of that size. */
  private class Target(val width: Int, val height: Int) {
    val reader: ImageReader = ImageReader.newInstance(
      width,
      height,
      android.graphics.PixelFormat.RGBA_8888,
      2,
      HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
        HardwareBuffer.USAGE_CPU_READ_RARELY,
    )
    var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    val readerThread = HandlerThread("mirage-gl-reader").apply { start() }
    val readerHandler = Handler(readerThread.looper)
  }

  private var target: Target? = null

  /**
   * Runs [block] on the GL thread with the shared context current on a render target of [width]x
   * [height], and returns the rendered frame read back as a [Bitmap] (or `null` if the readback failed
   * — the caller then no-ops that frame). [block] issues the draw calls (bind program, set uniforms,
   * draw the quad); this owns context/surface setup, swap, and readback.
   */
  fun render(width: Int, height: Int, block: () -> Unit): Bitmap? {
    if (width <= 0 || height <= 0) return null
    var result: Bitmap? = null
    val done = CountDownLatch(1)
    handler.post {
      try {
        result = renderOnGlThread(width, height, block)
      } catch (e: RuntimeException) {
        // GL / EGL failure (lost context, unsupported format, a failed `check()`): degrade to no-op.
        // This frame passes through; the band's original no-op is preserved, so it is not a regression.
        // Narrow to RuntimeException so an Error (e.g. OOM) still propagates and is never masked. This
        // runs on the GL HandlerThread, not a coroutine, so no CancellationException flows here.
        result = null
      } finally {
        done.countDown()
      }
    }
    // Bounded wait: a wedged GL thread must not hang the caller's capture coroutine forever.
    done.await(2, TimeUnit.SECONDS)
    return result
  }

  private fun renderOnGlThread(width: Int, height: Int, block: () -> Unit): Bitmap? {
    ensureContext()
    val t = ensureTarget(width, height)

    val available = CountDownLatch(1)
    t.reader.setOnImageAvailableListener({ available.countDown() }, t.readerHandler)

    check(EGL14.eglMakeCurrent(display, t.surface, t.surface, context)) {
      "eglMakeCurrent failed: 0x${Integer.toHexString(EGL14.eglGetError())}"
    }

    GLES30.glViewport(0, 0, width, height)
    block()
    GLES30.glFinish() // spike B-1: glFinish before swap is the sync that makes the frame readable.
    check(EGL14.eglSwapBuffers(display, t.surface)) {
      "eglSwapBuffers failed: 0x${Integer.toHexString(EGL14.eglGetError())}"
    }

    if (!available.await(2, TimeUnit.SECONDS)) return null
    val image = t.reader.acquireLatestImage() ?: return null
    return try {
      val hb = image.hardwareBuffer ?: return null
      // wrapHardwareBuffer yields a HARDWARE bitmap sharing the buffer (zero-copy). It stays valid only
      // while the buffer/image live; the caller copies to a software bitmap before we close them.
      val wrapped =
        Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB)) ?: return null
      val copy = wrapped.copy(Bitmap.Config.ARGB_8888, false)
      wrapped.recycle()
      hb.close()
      copy
    } finally {
      image.close()
    }
  }

  private fun ensureContext() {
    if (context != EGL14.EGL_NO_CONTEXT) return
    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    check(display != EGL14.EGL_NO_DISPLAY) { "no EGL display" }
    val version = IntArray(2)
    check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

    val configAttribs = intArrayOf(
      EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, // ES3 contexts advertise ES2_BIT here
      EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
      EGL14.EGL_RED_SIZE, 8,
      EGL14.EGL_GREEN_SIZE, 8,
      EGL14.EGL_BLUE_SIZE, 8,
      EGL14.EGL_ALPHA_SIZE, 8,
      EGL14.EGL_NONE,
    )
    val configs = arrayOfNulls<EGLConfig>(1)
    val numConfigs = IntArray(1)
    check(
      EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) &&
        numConfigs[0] > 0,
    ) { "eglChooseConfig found no config" }
    config = configs[0]

    val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
    context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
    check(context != EGL14.EGL_NO_CONTEXT) {
      "eglCreateContext failed: 0x${Integer.toHexString(EGL14.eglGetError())}"
    }
  }

  private fun ensureTarget(width: Int, height: Int): Target {
    val existing = target
    if (existing != null && existing.width == width && existing.height == height) return existing

    existing?.let { releaseTarget(it) }
    val t = Target(width, height)
    t.surface = EGL14.eglCreateWindowSurface(
      display,
      config,
      t.reader.surface,
      intArrayOf(EGL14.EGL_NONE),
      0,
    )
    check(t.surface != EGL14.EGL_NO_SURFACE) {
      "eglCreateWindowSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}"
    }
    target = t
    return t
  }

  private fun releaseTarget(t: Target) {
    if (t.surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, t.surface)
    t.reader.close()
    t.readerThread.quitSafely()
  }
}
