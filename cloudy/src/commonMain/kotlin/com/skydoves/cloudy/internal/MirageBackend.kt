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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import kotlin.jvm.JvmInline
import androidx.compose.ui.graphics.ColorFilter as ComposeColorFilter

/**
 * Opaque per-platform compiled program handle. Wraps whatever the platform runtime shader object is
 * (Android `RuntimeShader`, skiko `RuntimeShaderBuilder` + its `RuntimeEffect`). Callers hold it via
 * [CachedProgram][MirageProgramCache] and never touch its internals directly — every per-draw write
 * goes through [uniformSink].
 */
internal expect class MirageBackendProgram

/**
 * Compiles [compiled]'s generated dialect source into a platform program.
 *
 * Returns `null` when the platform cannot support it right now (Android API < 33 — `RuntimeShader`
 * is unavailable), in which case the whole optic is a draw-time no-op. On a supporting platform a
 * source that fails to compile throws (surfacing the shader error) rather than returning `null`.
 */
internal expect fun createBackendProgram(compiled: CompiledProgram): MirageBackendProgram?

/**
 * Per-draw typed uniform writer. The cache/node walks the [UniformSchema] in declaration order and
 * pushes each handle's current value through the matching method. The `name` is the codegen uniform
 * identifier (the schema entry name), so writes stay name-keyed rather than positional — the backend
 * decides how to resolve it (Android/skiko both key uniforms by name).
 *
 * There is deliberately **no** `vec3` for a raw `float3` value even though [UVec3] exists: no handle
 * uses a float3 preset and both backends already expose float2/float3/float4 through
 * [floatArray] (skiko `uniform(name, FloatArray)`, Android `setFloatUniform(name, FloatArray)`), so a
 * separate arity method would be dead surface. A float3 handle binds through [floatArray] with a
 * length-3 array.
 */
internal interface UniformSink {
  fun float(name: String, v: Float)

  fun float2(name: String, x: Float, y: Float)

  fun float4(name: String, x: Float, y: Float, z: Float, w: Float)

  fun int(name: String, v: Int)

  fun floatArray(name: String, v: FloatArray)

  /**
   * Writes a `layout(color)` uniform. Android converts [c] into the shader's working color space via
   * `setColorUniform` (an official platform guarantee); skiko has no color-aware setter, so its actual
   * converts [c] to sRGB unpremultiplied `float4` by hand — see the skiko actual's KDoc for the
   * cross-platform color-fidelity caveat this introduces.
   */
  fun color(name: String, c: Color)

  /** Binds a `uniform shader` texture child. A `null` [img] leaves the child unset (caller ensures
   *  the schema default or a later write fills it before draw). */
  fun texture(name: String, img: ImageBitmap?, tileMode: TileMode)
}

/** Returns a [UniformSink] bound to this backend program's live uniforms. */
internal expect fun MirageBackendProgram.uniformSink(): UniformSink

/**
 * How a filter backend transforms a stage's recorded content into its output. Sealed so the chain's
 * draw loop branches exhaustively over the application shapes a band can take.
 *
 * - [Effect] : the backend is a content-bound [RenderEffect] set on the stage's layer, the GPU running
 *   it as the layer draws. The only shape skiko and Android API 33+ AGSL ever use.
 * - [ColorFilter] : the backend is a per-pixel [ComposeColorFilter] set on the stage's layer (applied
 *   in the layer paint on API 23+, so it needs no `RenderEffect`, which is API 31+). Used by the
 *   Android ColorGrade band to reproduce a Colorize optic as an affine grade.
 * - [Blit] : the backend reads the stage's recorded pixels as an [ImageBitmap], transforms them off
 *   the layer render-effect path, and returns the result. Used by the Android GLES band, whose FBO
 *   round-trip cannot be a `RenderEffect`. The readback itself is not synchronous in draw (Compose's
 *   `GraphicsLayer.toImageBitmap()` is `suspend`), so the capture runs off the draw pass and the chain
 *   branches on this shape to feed the GLES pipeline.
 */
internal sealed interface FilterApplication {
  @JvmInline
  value class Effect(val renderEffect: RenderEffect) : FilterApplication

  @JvmInline
  value class ColorFilter(val colorFilter: ComposeColorFilter) : FilterApplication

  @JvmInline
  value class Blit(val apply: (ImageBitmap) -> ImageBitmap) : FilterApplication
}

/**
 * Returns how this backend applies to a stage's content. skiko is always [FilterApplication.Effect];
 * Android returns [FilterApplication.Blit] for the GLES / ColorGrade leaves and [Effect] for AGSL.
 *
 * Call *after* the per-draw [uniformSink] writes, since an [Effect] captures the program's current
 * uniforms (same ordering contract as [asContentRenderEffect]).
 */
internal expect fun MirageBackendProgram.filterApplication(): FilterApplication

/**
 * Prepares an [FilterApplication.Blit]-style transform for the GLES backend with this draw's uniforms
 * bound into a **fresh** per-draw recording (no shared mutable state — the process-wide GLES program is
 * used by many nodes / overlapping frames, so a shared record would race). Returns `null` for any
 * backend that is not GLES (skiko always; Android AGSL/ColorGrade), i.e. those that apply via
 * [filterApplication] instead.
 *
 * The returned closure runs the GL round-trip off the draw thread; the backdrop node owns the async
 * capture around it (see [MirageGlesBackdrop]).
 */
internal expect fun MirageBackendProgram.prepareGlesBlit(
  cached: CachedProgram,
  params: com.skydoves.cloudy.MirageParams,
  paramsBlock: (com.skydoves.cloudy.MirageParams.() -> Unit)?,
  width: Float,
  height: Float,
  density: Float,
  time: Float,
): ((ImageBitmap) -> ImageBitmap)?

/**
 * Builds a [RenderEffect] that runs this program over the layer's content, binding the content as the
 * `content` shader child. This is the **filter** application path (`usesContent = true`): the node
 * sets it as `graphicsLayer { renderEffect = … }` so the program's output replaces the content. Call
 * *after* the per-draw [uniformSink] writes, since the effect captures the program's current uniforms.
 */
internal expect fun MirageBackendProgram.asContentRenderEffect(): RenderEffect

/**
 * Builds a [ShaderBrush] over this program with its current uniforms, for the **overlay** application
 * path (a [Generate][OpticCategory.Generate] optic drawn over the content). The node fills the draw
 * area with it under a caller-chosen blend mode. Call *after* the per-draw [uniformSink] writes:
 * skiko bakes uniforms at shader-build time, so this must observe the latest values.
 */
internal expect fun MirageBackendProgram.asShaderBrush(): ShaderBrush
