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
package com.skydoves.cloudy

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
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Spike B-1 for the API 29-32 GLES mirage backend (option b: ImageReader-Surface).
 *
 * Proves the *whole* zero-copy readback path works on this device WITHOUT the JNI-only
 * `glEGLImageTargetTexture2DOES`:
 *   ImageReader.getSurface() -> EGL window surface -> render a solid color to the default framebuffer
 *   -> eglSwapBuffers -> acquireLatestImage().hardwareBuffer -> Bitmap.wrapHardwareBuffer -> read a
 *   pixel and assert it is the color drawn.
 *
 * If this passes on an API 30/31 emulator, option (b) is confirmed and the M3 pipeline is buildable in
 * pure Kotlin. If it fails, the GLES band stays a no-op and M3 falls back to option (a) NDK.
 */
@RunWith(AndroidJUnit4::class)
public class GlesRoundtripSpikeTest {

  @Test
  public fun imageReaderSurfaceRoundtripYieldsTheRenderedColor() {
    // API 29+ is required for wrapHardwareBuffer; the whole GLES band is 29-32, so guard just in case
    // the test host is older (it will not be, but keep the assertion honest).
    assertTrue("wrapHardwareBuffer needs API 29+", Build.VERSION.SDK_INT >= 29)

    val w = 16
    val h = 16
    // USAGE the design specifies: GPU writes the color (window-surface render target), CPU reads it
    // back (wrapHardwareBuffer path). GPU_SAMPLED_IMAGE lets a later frame sample it as a texture.
    val usage = HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
      HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
      HardwareBuffer.USAGE_CPU_READ_RARELY
    val reader = ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2, usage)

    // Drive the BufferQueue via a listener on its own thread — acquireLatestImage() polled from the
    // test thread can miss the frame, so wait for the onImageAvailable callback (the documented
    // ImageReader consumption pattern).
    val readerThread = HandlerThread("spike-reader").apply { start() }
    val available = CountDownLatch(1)
    reader.setOnImageAvailableListener({ available.countDown() }, Handler(readerThread.looper))

    val egl = EglWindow(reader.surface, w, h)
    try {
      // Draw a known solid color (orange-ish: R=255, G=128, B=0, A=255). Clear is enough to prove the
      // render-to-window-surface -> readback path; a full shader/quad is exercised in M3 proper.
      egl.makeCurrent()
      GLES30.glClearColor(1f, 0.5f, 0f, 1f)
      GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
      // glFinish before swap: the spike question is whether swap/acquire need explicit sync. Keeping it
      // here answers "with glFinish it works"; M3 can then test dropping it.
      GLES30.glFinish()
      egl.swapBuffers()

      assertTrue(
        "onImageAvailable never fired after swapBuffers",
        available.await(2, TimeUnit.SECONDS),
      )
      val image: android.media.Image? = reader.acquireLatestImage()
      assertNotNull("acquireLatestImage returned null after onImageAvailable", image)

      val hb: HardwareBuffer = image!!.hardwareBuffer!!
      assertTrue(
        "buffer usage lost GPU_COLOR_OUTPUT",
        hb.usage and HardwareBuffer.USAGE_GPU_COLOR_OUTPUT != 0L,
      )

      val bitmap = Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB))
      assertNotNull("wrapHardwareBuffer returned null", bitmap)

      // A HARDWARE bitmap has no direct pixel access; copy to ARGB_8888 to sample.
      val readable = bitmap!!.copy(Bitmap.Config.ARGB_8888, false)
      val px = readable.getPixel(w / 2, h / 2)
      val r = (px shr 16) and 0xFF
      val g = (px shr 8) and 0xFF
      val b = px and 0xFF
      // Allow generous slack for sRGB/premul rounding on SwiftShader; the point is "the drawn color
      // came back", not exact bytes.
      assertEquals("red channel", 255f, r.toFloat(), 8f)
      assertEquals("green channel", 128f, g.toFloat(), 12f)
      assertEquals("blue channel", 0f, b.toFloat(), 8f)

      hb.close()
      image!!.close()
      bitmap.recycle()
      readable.recycle()
    } finally {
      egl.release()
      reader.close()
      readerThread.quitSafely()
    }
  }
}

/**
 * Minimal offscreen EGL 3.0 context bound to an ImageReader [surface] as its window surface. Not the
 * production [GlEnv] — a spike-local helper to prove the roundtrip.
 */
private class EglWindow(surface: android.view.Surface, width: Int, height: Int) {
  private val display: EGLDisplay
  private val context: EGLContext
  private val eglSurface: EGLSurface

  init {
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
    ) {
      "eglChooseConfig found no config"
    }
    val config = configs[0]!!

    val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
    context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
    check(context != EGL14.EGL_NO_CONTEXT) {
      "eglCreateContext failed: 0x${Integer.toHexString(EGL14.eglGetError())}"
    }

    // The ImageReader Surface is the render target; eglSwapBuffers pushes each frame into the reader.
    eglSurface =
      EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
    check(eglSurface != EGL14.EGL_NO_SURFACE) {
      "eglCreateWindowSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}"
    }
  }

  fun makeCurrent() {
    check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
      "eglMakeCurrent failed: 0x${Integer.toHexString(EGL14.eglGetError())}"
    }
  }

  fun swapBuffers() {
    check(EGL14.eglSwapBuffers(display, eglSurface)) {
      "eglSwapBuffers failed: 0x${Integer.toHexString(EGL14.eglGetError())}"
    }
  }

  fun release() {
    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    EGL14.eglDestroySurface(display, eglSurface)
    EGL14.eglDestroyContext(display, context)
    EGL14.eglTerminate(display)
  }
}
