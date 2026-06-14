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

import androidx.compose.runtime.Immutable

/**
 * The uniform-writing surface handed to a [ShaderRecipe]'s `bindUniforms` block each draw.
 *
 * A recipe receives this scope and pushes the per-draw values for every uniform its shader
 * declares. The three overloads cover the scalar / 2-component / 4-component uniform shapes used by
 * AGSL and SKSL (`float`, `float2`, `float4`); a 3-component uniform is written via the 4-arg
 * overload with `w` ignored by the shader.
 *
 * This is a `public` interface — not an internal one — because [ShaderRecipe.bindUniforms] is a
 * publicly stored lambda whose receiver type is part of the stable API; under
 * `-Xexplicit-api=strict` the receiver of a public lambda parameter may not be `internal`. The
 * concrete platform binding (which wraps a `RuntimeShader` / Skia `RuntimeEffect`) implements this
 * interface internally, so the GPU-side types never leak into the recipe contract.
 */
@ExperimentalShaderEffect
public interface ShaderEffectScope {
  /** Writes a scalar `float` uniform named [name]. */
  public fun uniform(name: String, value: Float)

  /** Writes a 2-component `float2` uniform named [name]. */
  public fun uniform(name: String, x: Float, y: Float)

  /** Writes a 4-component `float4` uniform named [name]. (Use for `float3` with [w] ignored.) */
  public fun uniform(name: String, x: Float, y: Float, z: Float, w: Float)
}

/**
 * How a [ShaderRecipe]'s shader consumes the composable content it is attached to.
 *
 * Sealed so the binding can exhaustively branch on the mode (and so new modes can be added without
 * breaking callers that only construct the provided objects).
 */
@ExperimentalShaderEffect
public sealed interface ShaderInputMode {
  /**
   * The shader filters the content itself: the content is supplied as the shader's input image
   * (the `content` shader input) and the shader's output replaces it. Use for lens/refraction style
   * effects that read and transform the pixels beneath them.
   */
  // RequiresOptIn does not propagate to nested members, so each nested object carries the marker
  // explicitly to stay out of the stable ABI (otherwise BCV dumps it as a public object).
  @ExperimentalShaderEffect
  public object ContentFilter : ShaderInputMode {
    // Clean name (not the default Object identity string) so it appears legibly in
    // ShaderRecipe.toString() and any debug/log output.
    override fun toString(): String = "ContentFilter"
  }

  /**
   * The shader draws on top of the unmodified content: the content renders first, then the shader
   * output is composited over it. Use for additive glints/overlays that do not need to read the
   * underlying pixels.
   */
  @ExperimentalShaderEffect
  public object Overlay : ShaderInputMode {
    override fun toString(): String = "Overlay"
  }
}

/**
 * An open, caller-supplied shader effect: a pair of platform shader bodies ([agsl] / [sksl]), an
 * [inputMode], and a [bindUniforms] block that writes the shader's uniforms each draw.
 *
 * Apply a recipe to any composable with [Modifier.shaderEffect]. Build the provided effects with
 * the recipe factories (e.g. the specular / chromatic recipes), or author your own by following the
 * invariants below.
 *
 * ## Recipe author contract (G-1 invariants)
 * The library concatenates a fixed **preamble** (all built-in uniform declarations, helper
 * functions such as `boxRoundedSDF`, and shared `const`s) in front of [agsl] / [sksl] before
 * compiling. A recipe body must therefore:
 *
 * 1. **Contain exactly one `half4 main(float2 xy)`** — the preamble owns every uniform, helper, and
 *    const, and declares **no** `main`. The recipe supplies the single entry point and nothing else
 *    at file scope beyond that `main` (plus any local helpers it does not share with the preamble).
 * 2. **Only call preamble-provided helpers** (`boxRoundedSDF`, `lensNormalDirection`, …) — do not
 *    redeclare them or forward-reference helpers the preamble does not provide. A redeclaration
 *    collides with the preamble and fails compilation.
 * 3. **Write every declared uniform on every draw** from [bindUniforms]. Skia's `RuntimeEffect`
 *    throws if a declared uniform is left unset, so a uniform that exists in the preamble must
 *    receive a value each draw (use a constant if it never changes).
 * 4. **Never redeclare a uniform, helper, or `main`.** Any duplicate declaration against the
 *    preamble is a compile error on that platform.
 *
 * Caller performance note: pass a recipe value that is stable across recompositions (hoist it into
 * a `remember(recipe)` or a top-level/`object` constant) so the modifier is not reallocated every
 * frame. This is a recommendation, not a hard requirement.
 *
 * This is intentionally **not** a `data class`: it holds a function-typed field ([bindUniforms]),
 * for which a generated structural `equals` is meaningless (two distinct lambdas with identical
 * behavior are never `==`), and an `internal` primary constructor lets new fields be added later
 * without breaking the generated-component / `copy` ABI of a data class.
 *
 * @property agsl The Android (AGSL) shader body — see the invariants above.
 * @property sksl The Skia (SKSL) shader body for iOS / macOS / Desktop / Wasm.
 * @property inputMode How the shader consumes the content it is attached to.
 */
@ExperimentalShaderEffect
@Immutable
public class ShaderRecipe internal constructor(
  public val agsl: String,
  public val sksl: String,
  public val inputMode: ShaderInputMode,
  internal val bindUniforms: ShaderEffectScope.() -> Unit,
) {
  // The cache key is the source text + input mode only. bindUniforms is deliberately excluded:
  // it is a lambda (never structurally comparable) and it merely *feeds* values into the shader
  // identified by the source — two recipes with identical AGSL/SKSL/inputMode compile to the same
  // GPU program and so must be treated as equal for shader-caching/dedup purposes.
  override fun equals(other: Any?): Boolean = this === other ||
    (
      other is ShaderRecipe &&
        agsl == other.agsl &&
        sksl == other.sksl &&
        inputMode == other.inputMode
      )

  override fun hashCode(): Int {
    var result = agsl.hashCode()
    result = result * 31 + sksl.hashCode()
    result = result * 31 + inputMode.hashCode()
    return result
  }

  override fun toString(): String =
    "ShaderRecipe(agsl=${agsl.length} chars, sksl=${sksl.length} chars, inputMode=$inputMode)"

  // The companion is a separate ABI class (ShaderRecipe$Companion); the marker on the outer class
  // does not reach it, so it carries its own to stay out of the stable dumps.
  @ExperimentalShaderEffect
  public companion object {
    /**
     * Creates a [ShaderRecipe] from its two platform shader bodies and a uniform-binding block.
     *
     * Exposed as a companion `invoke` rather than a same-named top-level factory: the primary
     * constructor is `internal` with the identical `(String, String, ShaderInputMode, lambda)`
     * signature, so a top-level `fun ShaderRecipe(...)` would be a conflicting overload (same
     * precedent as `LiquidGlassGlow`). `invoke` keeps the intended `ShaderRecipe(...)` call syntax
     * while leaving the internal constructor free for future field additions (ABI stability).
     *
     * @param agsl The Android (AGSL) shader body. See [ShaderRecipe] for the author invariants.
     * @param sksl The Skia (SKSL) shader body for the skiko platforms.
     * @param inputMode How the shader consumes its content. Default: [ShaderInputMode.ContentFilter].
     * @param bindUniforms Block invoked each draw to write the shader's uniforms via
     *   [ShaderEffectScope]. Default: no uniforms (only valid if the shader declares none beyond the
     *   built-ins).
     */
    public operator fun invoke(
      agsl: String,
      sksl: String,
      inputMode: ShaderInputMode = ShaderInputMode.ContentFilter,
      bindUniforms: ShaderEffectScope.() -> Unit = {},
    ): ShaderRecipe = ShaderRecipe(agsl, sksl, inputMode, bindUniforms)
  }
}
