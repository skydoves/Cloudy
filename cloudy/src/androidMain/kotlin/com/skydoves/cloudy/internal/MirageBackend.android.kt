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

import android.graphics.BitmapShader
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb

/**
 * Android backend program — wraps a single [RuntimeShader]. Only ever constructed on API 33+ (the
 * factory below gates it), so the `@RequiresApi` on the wrapped field is satisfied by construction.
 */
internal actual class MirageBackendProgram @RequiresApi(Build.VERSION_CODES.TIRAMISU) constructor(
  val shader: RuntimeShader,
)

/**
 * Compiles [compiled] into a [RuntimeShader]. `RuntimeShader` requires API 33+; below that the whole
 * optic is unsupported, so this returns `null` and the caller no-ops. On 33+ a source that fails to
 * compile throws from the `RuntimeShader` constructor — surfaced, not swallowed.
 */
internal actual fun createBackendProgram(compiled: CompiledProgram): MirageBackendProgram? {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
  return MirageBackendProgram(RuntimeShader(compiled.source))
}

internal actual fun MirageBackendProgram.uniformSink(): UniformSink = AndroidUniformSink(shader)

// Both application paths are only reached on API 33+: the program is built by createBackendProgram,
// which returns null (and the node no-ops) below TIRAMISU, so a non-null program guarantees the API.
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun MirageBackendProgram.asContentRenderEffect(): RenderEffect =
  AndroidRenderEffect
    .createRuntimeShaderEffect(shader, "content")
    .asComposeRenderEffect()

// RuntimeShader extends android.graphics.Shader, so it drives a ShaderBrush directly; its uniforms
// are read live at draw time (no rebuild needed, unlike skiko).
internal actual fun MirageBackendProgram.asShaderBrush(): ShaderBrush = ShaderBrush(shader)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidUniformSink(private val shader: RuntimeShader) : UniformSink {

  override fun float(name: String, v: Float): Unit = shader.setFloatUniform(name, v)

  override fun float2(name: String, x: Float, y: Float): Unit = shader.setFloatUniform(name, x, y)

  override fun float4(name: String, x: Float, y: Float, z: Float, w: Float): Unit =
    shader.setFloatUniform(name, x, y, z, w)

  override fun int(name: String, v: Int): Unit = shader.setIntUniform(name, v)

  override fun floatArray(name: String, v: FloatArray): Unit = shader.setFloatUniform(name, v)

  // Android's setColorUniform(name, sRGB-int) converts the color into the shader's working color
  // space for us — no manual conversion (contrast the skiko actual, which has no color-aware setter).
  // This is the one platform where layout(color) is safe. toArgb() is the KMP-common sRGB bridge.
  override fun color(name: String, c: Color): Unit =
    shader.setColorUniform(name, c.toArgb())

  override fun texture(name: String, img: ImageBitmap?, tileMode: TileMode) {
    if (img == null) return
    val tile = tileMode.toAndroidTileMode()
    val bitmapShader = BitmapShader(img.asAndroidBitmap(), tile, tile)
    shader.setInputShader(name, bitmapShader)
  }
}

// TileMode is a Compose type; map it to the framework enum the BitmapShader wants. Decal is API 31+,
// which is always satisfied here (this sink only exists on API 33+).
private fun TileMode.toAndroidTileMode(): Shader.TileMode = when (this) {
  TileMode.Repeated -> Shader.TileMode.REPEAT
  TileMode.Mirror -> Shader.TileMode.MIRROR
  TileMode.Decal -> Shader.TileMode.DECAL
  else -> Shader.TileMode.CLAMP
}
