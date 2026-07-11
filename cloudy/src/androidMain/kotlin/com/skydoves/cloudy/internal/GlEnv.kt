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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

/**
 * Process-wide GLES 3.0 environment for the API 29-32 mirage backend: one dedicated GL thread owning
 * one EGL 3.0 context, plus per-size [ImageReader] render targets. Every GL call runs on the GL thread
 * (an EGL context is single-thread-affine), so callers hand work in via [render], which suspends until
 * the GL thread finishes.
 *
 * The GL thread is a single-thread coroutine dispatcher, not a HandlerThread: the callers are already
 * coroutines ([MirageGlesBackdrop]), so [render] suspends instead of blocking a pool thread on a latch.
 * [glDispatcher] is a one-thread executor whose single thread is the EGL affinity anchor; all GL work
 * runs in [glScope] (bound to it), so the context is never touched off its owning thread (UB). [render]
 * awaits that work under a timeout, so a driver-wedged GL thread bounds the caller (not the GL job,
 * which native calls make uninterruptible) — see [render].
 *
 * ## Zero-copy readback (confirmed on API 30)
 * The context renders into an `ImageReader.getSurface()` window surface; `eglSwapBuffers` pushes the
 * frame into the reader, whose [ImageReader.OnImageAvailableListener] then yields a `HardwareBuffer`
 * that `Bitmap.wrapHardwareBuffer` wraps with no CPU copy. `acquireLatestImage()` polled without the
 * listener returns null, so the listener bridge ([awaitImage]) is required, not optional.
 */
internal object GlEnv {

  private val glDispatcher =
    Executors.newSingleThreadExecutor { r -> Thread(r, "mirage-gl") }.asCoroutineDispatcher()

  // GL work runs in this scope (on glDispatcher), decoupled from the caller's coroutine so a timed-out
  // caller can abandon a render without cancelling the GL job — a GL/EGL call in native code cannot be
  // interrupted anyway. SupervisorJob so one render's failure never tears the scope down for the next.
  private val glScope = CoroutineScope(glDispatcher + SupervisorJob())

  // The listener bridge runs on its own looper, not the GL thread: while a render suspends on
  // awaitImage the GL thread is released back to glDispatcher, and the listener's resume() re-dispatches
  // the continuation onto it — but setOnImageAvailableListener wants a Handler, which a coroutine
  // dispatcher does not provide, so one Handler thread serves every reader.
  private val readerThread = HandlerThread("mirage-gl-reader").apply { start() }
  private val readerHandler = Handler(readerThread.looper)

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
  }

  private var target: Target? = null

  /**
   * Runs [block] on the GL thread with the shared context current on a render target of [width]x
   * [height], and returns the rendered frame read back as a [Bitmap] (or `null` if the readback failed
   * — the caller then no-ops that frame). [block] issues the draw calls (bind program, set uniforms,
   * draw the quad); this owns context/surface setup, swap, and readback.
   */
  suspend fun render(width: Int, height: Int, block: () -> Unit): Bitmap? {
    if (width <= 0 || height <= 0) return null
    // Run the GL work in glScope (glDispatcher pins it to the single GL thread for EGL affinity), then
    // await it under a timeout from the *caller's* context. The timeout bounds the caller only:
    // Deferred.await() cancels promptly at 2s (caller gets null) WITHOUT cancelling the GL job, because
    // a GL/EGL/glFinish call hung in the driver cannot be interrupted from Kotlin. The abandoned job
    // keeps running on the GL thread; whatever frame it eventually swaps is cleaned up by the next
    // render's entry drain(). If the driver wedges permanently the GL thread stays pinned and later
    // renders also time out to null — a degradation back to the band's original no-op, not a new hang.
    val deferred = glScope.async {
      try {
        renderOnGlThread(width, height, block)
      } catch (e: CancellationException) {
        // Never reached by the caller's timeout (that cancels the awaiter, not this job); rethrow so a
        // real scope cancellation is not masked as a degraded frame.
        throw e
      } catch (e: RuntimeException) {
        // GL / EGL failure (lost context, unsupported format, a failed `check()`): degrade to no-op.
        // This frame passes through; the band's original no-op is preserved, so it is not a regression.
        // Narrow to RuntimeException so an Error (e.g. OOM) still propagates and is never masked.
        null
      }
    }
    return withTimeoutOrNull(2_000) { deferred.await() }
  }

  private suspend fun renderOnGlThread(width: Int, height: Int, block: () -> Unit): Bitmap? {
    ensureContext()
    val t = ensureTarget(width, height)

    // Drain any image a previous call left in the reader. A timed-out call returns before acquiring its
    // frame, so the swap it posted lands here unclosed; without draining, those pile up and once
    // maxImages (2) are held un-closed, acquireLatestImage throws IllegalStateException (AOSP
    // ImageReader#acquireLatestImage), wedging every later frame into the catch as a permanent no-op.
    // Draining also clears a stale image that would fire this call's listener for the wrong frame.
    drain(t.reader)

    // Register the frame-available signal BEFORE the swap: setOnImageAvailableListener fires only for
    // frames that arrive after registration, and glFinish + eglSwapBuffers queue the frame synchronously
    // here, so a listener registered after the swap would miss an already-queued image and wait forever
    // (the 2s timeout). A CompletableDeferred (not suspendCancellableCoroutine) holds the signal even if
    // the listener fires before await() below, so there is no lost-signal race with the swap.
    val ready = CompletableDeferred<Unit>()
    t.reader.setOnImageAvailableListener({ ready.complete(Unit) }, readerHandler)

    check(EGL14.eglMakeCurrent(display, t.surface, t.surface, context)) {
      "eglMakeCurrent failed: 0x${Integer.toHexString(EGL14.eglGetError())}"
    }

    GLES30.glViewport(0, 0, width, height)
    block()
    GLES30.glFinish() // glFinish before swap is the sync that makes the frame readable.
    check(EGL14.eglSwapBuffers(display, t.surface)) {
      "eglSwapBuffers failed: 0x${Integer.toHexString(EGL14.eglGetError())}"
    }

    try {
      ready.await()
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
    } finally {
      // Detach on every exit (success, timeout-cancel, GL failure) so a later frame never fires this
      // reader's listener for the wrong render; drain (next render) is the only leftover consumer.
      t.reader.setOnImageAvailableListener(null, null)
    }
  }

  /** Acquires and closes every queued image, tolerating the full-queue IllegalStateException. */
  private fun drain(reader: ImageReader) {
    while (true) {
      val image = try {
        reader.acquireLatestImage()
      } catch (_: IllegalStateException) {
        // maxImages already held un-closed elsewhere: nothing this call can free, so stop draining.
        return
      } ?: return
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
  }
}
