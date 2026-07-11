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

/**
 * The three real codegen categories a mirage shader compiles into. Each fixes the kernel signature
 * the front-end wraps and how the content input is wired, so the compiler can branch exhaustively.
 */
internal enum class ShaderCategory {
  Colorize,
  Composite,
  Generate,
}

/**
 * The platform shading language a program is emitted in. Android API 33+ runs AGSL; every skiko
 * target (iOS / macOS / Desktop / Wasm) runs SKSL; Android API 29-32 runs [GlslEs] (GLES 3.0), where
 * the AGSL kernel is translated to `#version 300 es` GLSL and run through an offscreen FBO.
 */
internal enum class Dialect {
  Agsl,
  Sksl,
  GlslEs,
}

/**
 * One declared uniform slot in a [UniformSchema], in declaration order.
 *
 * @property name The property name that declared this uniform (the codegen uniform identifier).
 * @property glslType The shader type token (`float`, `float2`, `int`, `float4`, `shader`, …).
 * @property default The declared default, kept as [Any] because slots span `Float`/`Offset`/`Size`/
 *   `Int`/`FloatArray`/`Color`/`ImageBitmap?` and the schema is type-erased over them.
 * @property isColor Whether this slot is a `layout(color)` uniform (drives color-space conversion).
 * @property isTexture Whether this slot is a `uniform shader` child rather than a value uniform.
 */
internal class UniformEntry(
  val name: String,
  val glslType: String,
  val default: Any?,
  val isColor: Boolean,
  val isTexture: Boolean,
)

/**
 * The ordered uniform declarations of an shader's params, captured from `by uniform(...)` delegate
 * registration. Registration order equals declaration order equals uniform binding order, so the
 * list index is the deterministic bind slot.
 */
internal class UniformSchema(val entries: List<UniformEntry>) {

  // Texture children take a distinct binding path (setInputShader / child shader) from value
  // uniforms, so the binding side asks once instead of scanning entries per draw.
  fun hasTexture(): Boolean = entries.any { it.isTexture }
}

/**
 * A front-end kernel compiled into a full, ready-to-run shader program for one [Dialect].
 *
 * @property source The complete shader source (preamble + generated uniform declarations + kernel).
 * @property schema The uniform layout the binder writes each draw.
 * @property usesContent Whether the kernel samples the attached content (false for pure generators).
 * @property usesResolution Whether the kernel references the resolution standard uniform.
 * @property usesTime Whether the kernel references the mirage clock (drives redraw scheduling).
 * @property usesDensity Whether the kernel references the density standard uniform.
 * @property category The codegen category this program was emitted from.
 * @property isRaw Whether the shader is a raw escape-hatch ([com.skydoves.cloudy.MirageShader.raw]) whose
 *   source is authored verbatim. The GLSL ES backend cannot mechanically translate a raw AGSL body
 *   (it has no known assembled structure), so a raw optic is declined on that band.
 */
internal class CompiledProgram(
  val source: String,
  val schema: UniformSchema,
  val usesContent: Boolean,
  val usesResolution: Boolean,
  val usesTime: Boolean,
  val usesDensity: Boolean,
  val category: ShaderCategory,
  val isRaw: Boolean = false,
)
