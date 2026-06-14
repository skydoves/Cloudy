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

/**
 * Bundled [ShaderRecipe] values for the open [Modifier.shaderEffect] mechanism.
 *
 * These are ready-to-use recipes built on the shared preamble (see [ShaderRecipe] for the author
 * contract). [Specular] reproduces the liquid-glass bevel glint as a standalone recipe; [Chromatic]
 * is a fresh thin-film iridescence authored purely as a recipe, demonstrating that a new effect
 * needs no core/preamble changes. Both are `object` singletons, so passing them to `shaderEffect`
 * never reallocates the modifier across recompositions.
 */
@ExperimentalShaderEffect
public object ShaderRecipes {

  /**
   * The liquid-glass specular glint (moving focal hotspot + Blinn rim) as an open recipe.
   *
   * The shader body is the built-in specular formula repackaged as a self-contained `main`; the
   * uniform values below are the historical [GlowTuning] defaults (= [LiquidGlassDefaults.Glow]),
   * so applying this recipe over the default lens framing reproduces the built-in glint.
   */
  public val Specular: ShaderRecipe = ShaderRecipe(
    agsl = SPECULAR_AGSL,
    sksl = SPECULAR_SKSL,
    inputMode = ShaderInputMode.ContentFilter,
    bindUniforms = {
      // GlowTuning defaults (LiquidGlass.kt:148-161); same literals so the glint matches the
      // built-in liquidGlass look. Every declared uniform is written each draw (Skia throws on a
      // missing uniform; AGSL would read garbage).
      uniform("specStrength", LiquidGlassDefaults.GLOW_INTENSITY) // 0.7
      uniform("specPower", LiquidGlassDefaults.GLOW_SHARPNESS) // 10.0
      uniform("specRimMix", 0.4f)
      uniform("specWidthPx", 12.0f)
      uniform("specLightZ", 0.55f)
      uniform("specDomeFrac", 1.15f)
      uniform("specBodyPower", 2.5f)
      uniform("specBodyGain", 0.6f)
      uniform("specFocalK", 0.55f)
      uniform("specPoolFrac", 0.7f)
      uniform("specPoolGain", 1.3f)
    },
  )

  /**
   * A thin-film (Newton's-rings) iridescence — a fresh effect authored entirely as a recipe.
   *
   * Single "Iridescent" look: per-channel cosine interference driven by the lens bevel thickness,
   * with a metal floor and a pastel wash-out. Named multi-presets (oil-slick, soap-bubble, …) are a
   * follow-up; this proves one recipe value drives a brand-new effect with no core changes.
   */
  public val Chromatic: ShaderRecipe = ShaderRecipe(
    agsl = CHROMATIC_AGSL,
    sksl = CHROMATIC_SKSL,
    inputMode = ShaderInputMode.ContentFilter,
    bindUniforms = {
      uniform("chromaticIntensity", 0.6f)
      uniform("chromaticGain", 3.5f)
    },
  )
}
