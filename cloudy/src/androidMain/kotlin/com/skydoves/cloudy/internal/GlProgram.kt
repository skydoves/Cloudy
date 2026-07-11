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
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A compiled + linked GLES 3.0 program for one mirage optic, run on [GlEnv]'s GL thread. Holds the GL
 * program id, the fullscreen-quad VBO, and the content texture; created lazily on first render (the GL
 * thread is the only place GL objects may be made) and reused across frames.
 *
 * The fragment shader is [MirageGlslEs.translate]'s output; the vertex shader is a trivial fullscreen
 * quad. Content is uploaded as a normal 2D texture (NOT via `glEGLImageTargetTexture2DOES`, which has
 * no Java binding — that omission is exactly why the GLES backend uploads a `Bitmap` instead of
 * sharing the layer's HardwareBuffer).
 */
internal class GlProgram(private val fragmentSource: String) {

  private var program = 0
  private var quadVbo = 0
  private var contentTex = 0
  private var initialized = false

  /**
   * A [UniformSink] backed by a fresh per-draw list of GL uniform writes. GL writes must run on the GL
   * thread inside glUseProgram, but the shared binder runs on the (main) capture thread, so the sink
   * records closures the caller then hands to [render] for replay.
   *
   * Each call returns an independent list. This [GlProgram] is process-wide shared (the program cache
   * keys on source), so multiple nodes / overlapping frames use it concurrently — a shared mutable
   * recording field would race the main-thread binder against a background render() mid-iteration. The
   * caller pairs one [uniformSink] with one [render]; the list never crosses that pair.
   */
  fun uniformSink(): Pair<UniformSink, List<(Int) -> Unit>> {
    val writes = ArrayList<(Int) -> Unit>()
    return GlRecordingSink(writes) to writes
  }

  /**
   * Renders [content] through this optic at [content]'s size and returns the result bitmap (via
   * [GlEnv]), or `null` on GL failure. [writes] are the uniform closures recorded by the paired
   * [uniformSink], replayed on the GL thread.
   */
  fun render(content: Bitmap, writes: List<(Int) -> Unit>): Bitmap? {
    val w = content.width
    val h = content.height
    return GlEnv.render(w, h) {
      ensureInitialized()
      GLES30.glUseProgram(program)

      uploadContent(content)
      GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, contentTex)
      GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "content"), 0)
      GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uResolution"), w.toFloat(), h.toFloat())

      for (write in writes) write(program)

      drawQuad()
    }
  }

  private fun ensureInitialized() {
    if (initialized) return
    program = link(VERTEX_SOURCE, fragmentSource)
    quadVbo = createQuad()
    contentTex = createTexture()
    initialized = true
  }

  private fun uploadContent(content: Bitmap) {
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, contentTex)
    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, content, 0)
  }

  private fun drawQuad() {
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
    val posLoc = GLES30.glGetAttribLocation(program, "aPos")
    GLES30.glEnableVertexAttribArray(posLoc)
    // 2 floats per vertex, tightly packed.
    GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 0, 0)
    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    GLES30.glDisableVertexAttribArray(posLoc)
  }

  private fun createQuad(): Int {
    // Clip-space fullscreen quad as a triangle strip. gl_FragCoord is derived by the rasterizer; the
    // shader handles the Y-flip itself (MirageGlslEs), so no UV attribute is needed.
    val verts = floatArrayOf(
      -1f, -1f,
      1f, -1f,
      -1f, 1f,
      1f, 1f,
    )
    val buffer = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder())
      .asFloatBuffer().apply { put(verts); position(0) }
    val ids = IntArray(1)
    GLES30.glGenBuffers(1, ids, 0)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, buffer, GLES30.GL_STATIC_DRAW)
    return ids[0]
  }

  private fun createTexture(): Int {
    val ids = IntArray(1)
    GLES30.glGenTextures(1, ids, 0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
    // CLAMP + LINEAR: content.eval outside [0,1] clamps (matches AGSL child-shader default clamp), and
    // the effects sample sub-pixel offsets, so bilinear.
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
    return ids[0]
  }

  private companion object {
    const val VERTEX_SOURCE =
      "#version 300 es\n" +
        "in vec2 aPos;\n" +
        "void main() { gl_Position = vec4(aPos, 0.0, 1.0); }\n"

    fun link(vertexSource: String, fragmentSource: String): Int {
      val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
      val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
      val program = GLES30.glCreateProgram()
      GLES30.glAttachShader(program, vs)
      GLES30.glAttachShader(program, fs)
      GLES30.glLinkProgram(program)
      val status = IntArray(1)
      GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
      check(status[0] != 0) { "program link failed: ${GLES30.glGetProgramInfoLog(program)}" }
      // Shaders can be deleted once linked; the program keeps the compiled binaries.
      GLES30.glDeleteShader(vs)
      GLES30.glDeleteShader(fs)
      return program
    }

    fun compile(type: Int, source: String): Int {
      val shader = GLES30.glCreateShader(type)
      GLES30.glShaderSource(shader, source)
      GLES30.glCompileShader(shader)
      val status = IntArray(1)
      GLES30.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
      check(status[0] != 0) {
        "shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}\n--- source ---\n$source"
      }
      return shader
    }
  }
}

/**
 * [UniformSink] that *records* each write as a closure over the program id; [GlProgram.render] replays
 * them on the GL thread (where uniform locations resolve and `glUniform*` is legal). Keys by name like
 * the AGSL/skiko sinks. A `layout(color)` uniform arrives as sRGB float4 (the translator dropped the
 * color layout), matching how skiko converts colors by hand. A texture child other than `content` is
 * unsupported on GLES (no built-in optic declares one), so it is dropped.
 */
private class GlRecordingSink(private val out: MutableList<(Int) -> Unit>) : UniformSink {

  override fun float(name: String, v: Float) {
    out += { p -> GLES30.glUniform1f(GLES30.glGetUniformLocation(p, name), v) }
  }

  override fun float2(name: String, x: Float, y: Float) {
    out += { p -> GLES30.glUniform2f(GLES30.glGetUniformLocation(p, name), x, y) }
  }

  override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) {
    out += { p -> GLES30.glUniform4f(GLES30.glGetUniformLocation(p, name), x, y, z, w) }
  }

  override fun int(name: String, v: Int) {
    out += { p -> GLES30.glUniform1i(GLES30.glGetUniformLocation(p, name), v) }
  }

  override fun floatArray(name: String, v: FloatArray) {
    val copy = v.copyOf() // the handle reuses its array across draws; snapshot for the deferred replay
    out += { p ->
      val loc = GLES30.glGetUniformLocation(p, name)
      when (copy.size) {
        2 -> GLES30.glUniform2fv(loc, 1, copy, 0)
        3 -> GLES30.glUniform3fv(loc, 1, copy, 0)
        4 -> GLES30.glUniform4fv(loc, 1, copy, 0)
        else -> GLES30.glUniform1fv(loc, copy.size, copy, 0)
      }
    }
  }

  /** GLES has no color-aware setter; the translator made this a plain vec4, so write sRGB float4. */
  override fun color(name: String, c: androidx.compose.ui.graphics.Color) {
    val s = c.convert(androidx.compose.ui.graphics.colorspace.ColorSpaces.Srgb)
    out += { p -> GLES30.glUniform4f(GLES30.glGetUniformLocation(p, name), s.red, s.green, s.blue, s.alpha) }
  }

  override fun texture(
    name: String,
    img: androidx.compose.ui.graphics.ImageBitmap?,
    tileMode: androidx.compose.ui.graphics.TileMode,
  ) = Unit
}
