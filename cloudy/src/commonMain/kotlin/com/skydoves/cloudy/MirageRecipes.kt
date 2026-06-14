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
 * Bundled [MirageRecipe] values for the open [Modifier.mirage] mechanism.
 *
 * Ready-to-use recipes built on the shared preamble (see [MirageRecipe] for the author contract):
 * - [Specular] reproduces the liquid-glass bevel glint as a standalone [MirageInputMode.ContentFilter]
 *   recipe (bit-exact with the built-in `liquidGlass` glint).
 * - [Chromatic] and the four named looks ([OilSlick], [SoapBubble], [MetallicFoil], [Pearl]) are
 *   thin-film iridescence recipes — all the same shader body driven by different uniform values via
 *   the [chromatic] factory. They are `ContentFilter` (they read and re-tint the content).
 * - [Foil] is a [MirageInputMode.Overlay] recipe (a content-free generator drawn *over* the content).
 *
 * Every value is a singleton (`object` / top-level `val`), so passing one to `mirage` never
 * reallocates the modifier across recompositions.
 *
 * ## Chaining ContentFilter and Overlay recipes
 * Compose modifiers wrap left-to-right (the leftmost is the outermost draw). Put an [Overlay] recipe
 * to the **left** (outside) of a [MirageInputMode.ContentFilter] recipe so the filter transforms the
 * content first and the overlay is painted on top of the result:
 * `Modifier.mirage(Foil).mirage(OilSlick)` — `OilSlick` refracts the content, then `Foil`
 * is drawn over it. Reversing the order (`Foil` inside) would feed `Foil`'s overlay through the
 * refraction too. There is no compile-time guard for this ordering — see [Modifier.mirage].
 */
@ExperimentalMirage
public object MirageRecipes {

  /**
   * The liquid-glass specular glint (moving focal hotspot + Blinn rim) as an open recipe.
   *
   * The shader body is the built-in specular formula repackaged as a self-contained `main`; the
   * uniform values below are the historical [GlowTuning] defaults (= [LiquidGlassDefaults.Glow]),
   * so applying this recipe over the default lens framing reproduces the built-in glint.
   */
  public val Specular: MirageRecipe = MirageRecipe(
    agsl = SPECULAR_AGSL,
    sksl = SPECULAR_SKSL,
    inputMode = MirageInputMode.ContentFilter,
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
   * The default thin-film iridescence look — the parameterized [chromatic] factory at its defaults
   * (= the M1 single "Iridescent" recipe). Kept as a named value for discoverability and so callers
   * can use it without invoking the factory.
   */
  public val Chromatic: MirageRecipe = chromatic()

  /**
   * Oil-slick: high band count, wide RGB spread, near-zero metal floor — a saturated, dark-based
   * rainbow with little wash-out. (#124 look, reproduced through uniforms only.)
   */
  public val OilSlick: MirageRecipe = chromatic(
    gain = 5.5f,
    krgb = Triple(1f, 1.30f, 1.72f),
    floor = 0.05f,
    washout = 0.07f,
    modulate = 0.75f,
  )

  /**
   * Soap-bubble: few, wide bands with a high floor and strong wash-out — pale, pastel iridescence.
   */
  public val SoapBubble: MirageRecipe = chromatic(
    gain = 1.7f,
    krgb = Triple(1f, 1.11f, 1.26f),
    floor = 0.22f,
    washout = 0.50f,
    modulate = 0.22f,
  )

  /**
   * Metallic foil: dark floor + a Fresnel rim boost toward white at the edge — a sharp metallic sheen.
   */
  public val MetallicFoil: MirageRecipe = chromatic(
    gain = 3.6f,
    krgb = Triple(1f, 1.26f, 1.62f),
    floor = 0.03f,
    washout = 0.05f,
    modulate = 0.82f,
    rimBoost = 0.45f,
  )

  /**
   * Pearl: high floor + strong wash-out + a rim boost — a soft, luminous, low-saturation lustre.
   */
  public val Pearl: MirageRecipe = chromatic(
    gain = 2.4f,
    krgb = Triple(1f, 1.07f, 1.18f),
    floor = 0.46f,
    washout = 0.58f,
    modulate = 0.20f,
    rimBoost = 0.45f,
  )

  /**
   * A foil overlay — a content-free [MirageInputMode.Overlay] generator (glare + flowing rainbow +
   * anti-aliased sparkle) drawn on top of the unmodified content. Place it to the left (outside) of
   * any [MirageInputMode.ContentFilter] recipe when chaining (see the class KDoc).
   */
  public val Foil: MirageRecipe = MirageRecipe(
    agsl = FOIL_AGSL,
    sksl = FOIL_SKSL,
    inputMode = MirageInputMode.Overlay,
    bindUniforms = {
      uniform("foilBands", 5f)
      uniform("foilPhase", 0f)
      uniform("chromaticGain", 3.6f)
      uniform("sparkleDensity", 16f)
      uniform("sparkleAmplitude", 0.3f)
    },
  )
}

/**
 * Builds a thin-film (Newton's-rings) iridescence [MirageRecipe] from its per-look parameters.
 *
 * One shader body ([CHROMATIC_AGSL] / [CHROMATIC_SKSL]) expresses every named look purely through
 * uniform values — there is no in-shader mode branch. The defaults reproduce the M1 single
 * "Iridescent" look (= [MirageRecipes.Chromatic]); the bundled named looks ([MirageRecipes.OilSlick]
 * etc.) are this factory at different arguments.
 *
 * Declared as a non-`inline` extension on purpose: it references module-internal shader-text consts
 * and the internal recipe constructor, which an `inline public` function may not expose under
 * `-Xexplicit-api=strict`.
 *
 * @param intensity Overall effect strength; `0` disables it. Maps to `chromaticIntensity`.
 * @param gain Newton-ring band count (optical-path-difference scale). Maps to `chromaticGain`.
 * @param krgb Per-channel wavenumber ratios (r, g, b). Maps to `chromaticKRGB` (written as a 4-arg
 *   uniform with `w` ignored, since the shader reads only `.xyz`).
 * @param floor Metal floor; lower = higher contrast (off-zero keeps it metallic, not neon).
 *   Maps to `chromaticFloor`.
 * @param washout Higher-order wash-out rate toward silver. Maps to `chromaticWashout`.
 * @param modulate `0..1` focal-pool follow strength. Maps to `chromaticModulate`.
 * @param rimBoost Fresnel rim gain toward the lens rim (`0` = off). Maps to `chromaticRimBoost`.
 */
@ExperimentalMirage
public fun MirageRecipes.chromatic(
  intensity: Float = 0.6f,
  gain: Float = 3.0f,
  krgb: Triple<Float, Float, Float> = Triple(1f, 1.18f, 1.42f),
  floor: Float = 0.12f,
  washout: Float = 0.16f,
  modulate: Float = 1f,
  rimBoost: Float = 0f,
): MirageRecipe = MirageRecipe(
  agsl = CHROMATIC_AGSL,
  sksl = CHROMATIC_SKSL,
  inputMode = MirageInputMode.ContentFilter,
  bindUniforms = {
    // All 7 uniforms are written every draw: Skia throws on an unset uniform and AGSL would read
    // garbage. chromaticKRGB is a float3 fed via the 4-arg overload (the shader reads only .xyz).
    uniform("chromaticIntensity", intensity)
    uniform("chromaticGain", gain)
    uniform("chromaticKRGB", krgb.first, krgb.second, krgb.third, 0f)
    uniform("chromaticFloor", floor)
    uniform("chromaticWashout", washout)
    uniform("chromaticModulate", modulate)
    uniform("chromaticRimBoost", rimBoost)
  },
)
