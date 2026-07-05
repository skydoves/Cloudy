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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.skydoves.cloudy.internal.CHROMATIC_KERNEL_AGSL
import com.skydoves.cloudy.internal.CHROMATIC_KERNEL_SKSL
import com.skydoves.cloudy.internal.DUOTONE_KERNEL_AGSL
import com.skydoves.cloudy.internal.DUOTONE_KERNEL_SKSL
import com.skydoves.cloudy.internal.FOIL_KERNEL_AGSL
import com.skydoves.cloudy.internal.FOIL_KERNEL_SKSL
import com.skydoves.cloudy.internal.SPECULAR_KERNEL_AGSL
import com.skydoves.cloudy.internal.SPECULAR_KERNEL_SKSL

/**
 * Bundled [Optic] presets — the catalog of ready-to-apply looks.
 *
 * A preset is a kernel plus a default parameter set: the visual look lives entirely in the params'
 * declared defaults, never as a hard-coded shader constant, so every value is animatable and value
 * changes never recompile (the program cache is keyed on the kernel source, not the uniform values).
 * The named thin-film looks ([Chromatic] / [OilSlick] / [SoapBubble] / [MetallicFoil] / [Pearl]) are
 * the same [chromatic] factory at different defaults — one GPU program, five looks.
 *
 * Each value is a process-wide singleton, so applying one in a plan never reallocates the optic.
 *
 * Shared lens geometry ([MirageLensParams.lensCenter] / [lensSize][MirageLensParams.lensSize] /
 * [cornerRadius][MirageLensParams.cornerRadius]) and the specular light ([MirageLensParams.iLight])
 * default to the built-in liquid-glass framing, so a preset applied with no `params` block reproduces
 * that built-in look. Override them per draw from a `filter { … }` / `overlay { … }` block (e.g.
 * feed `iLight` a `rememberGyroLightSource` direction for motion lighting).
 */
@ExperimentalMirage
public object MirageOptics {

  /**
   * The liquid-glass specular glint (moving focal hotspot + Blinn rim). A [CompositeOptic] because it
   * shares intermediates (SDF, bevel normal) across the refraction and specular terms. Its defaults
   * are the `GlowTuning` values, so applying it over the default lens framing reproduces the built-in
   * `liquidGlass` glint bit-for-bit.
   */
  public val Specular: CompositeOptic<SpecularParams> = Optic.composite(
    name = "specular",
    paramsFactory = ::SpecularParams,
    agsl = SPECULAR_KERNEL_AGSL,
    sksl = SPECULAR_KERNEL_SKSL,
  )

  /** The default thin-film iridescence look — the [chromatic] factory at its defaults. */
  public val Chromatic: CompositeOptic<ChromaticParams> = chromatic()

  /**
   * Oil-slick: high band count, wide RGB spread, near-zero metal floor — a saturated, dark-based
   * rainbow with little wash-out.
   */
  public val OilSlick: CompositeOptic<ChromaticParams> = chromatic(
    gain = 5.5f,
    krgb = floatArrayOf(1f, 1.30f, 1.72f, 0f),
    floor = 0.05f,
    washout = 0.07f,
    modulate = 0.75f,
  )

  /** Soap-bubble: few, wide bands with a high floor and strong wash-out — pale, pastel iridescence. */
  public val SoapBubble: CompositeOptic<ChromaticParams> = chromatic(
    gain = 1.7f,
    krgb = floatArrayOf(1f, 1.11f, 1.26f, 0f),
    floor = 0.22f,
    washout = 0.50f,
    modulate = 0.22f,
  )

  /** Metallic foil: dark floor + a Fresnel rim boost toward white at the edge — a sharp metallic sheen. */
  public val MetallicFoil: CompositeOptic<ChromaticParams> = chromatic(
    gain = 3.6f,
    krgb = floatArrayOf(1f, 1.26f, 1.62f, 0f),
    floor = 0.03f,
    washout = 0.05f,
    modulate = 0.82f,
    rimBoost = 0.45f,
  )

  /** Pearl: high floor + strong wash-out + a rim boost — a soft, luminous, low-saturation lustre. */
  public val Pearl: CompositeOptic<ChromaticParams> = chromatic(
    gain = 2.4f,
    krgb = floatArrayOf(1f, 1.07f, 1.18f, 0f),
    floor = 0.46f,
    washout = 0.58f,
    modulate = 0.20f,
    rimBoost = 0.45f,
  )

  /**
   * A foil overlay — a content-free [GenerateOptic] (glare + flowing rainbow + anti-aliased sparkle)
   * drawn over the content. Declare it via `overlay(MirageOptics.Foil)` so it composites on top of any
   * filter result; its `mirageTime` reference lets the clock drive the sparkle shimmer.
   */
  public val Foil: GenerateOptic<FoilParams> = Optic.generate(
    name = "foil",
    paramsFactory = ::FoilParams,
    agsl = FOIL_KERNEL_AGSL,
    sksl = FOIL_KERNEL_SKSL,
  )

  /**
   * A point-wise duotone grade: maps luminance onto a [shadow][DuotoneParams.shadow] →
   * [highlight][DuotoneParams.highlight] gradient and cross-fades by [amount][DuotoneParams.amount].
   * A [ColorizeOptic], so it fuses cheaply and needs no lens framing. The defaults are a warm
   * split-tone (deep indigo shadows, cream highlights).
   */
  public val Duotone: ColorizeOptic<DuotoneParams> = Optic.colorize(
    name = "duotone",
    paramsFactory = ::DuotoneParams,
    agsl = DUOTONE_KERNEL_AGSL,
    sksl = DUOTONE_KERNEL_SKSL,
  )

  /**
   * Builds a thin-film (Newton's-rings) iridescence [CompositeOptic] from its per-look parameters. It
   * is a Composite (not a point-wise Colorize) because the kernel samples the content freely to tint
   * it. One kernel expresses every named look purely through uniform defaults — there is no in-shader
   * mode branch. The defaults reproduce [Chromatic]; the named looks ([OilSlick] etc.) are this factory
   * at different arguments.
   *
   * @param intensity overall effect strength; `0` disables it. Default `0.6`.
   * @param gain Newton-ring band count (optical-path-difference scale). Default `3.0`.
   * @param krgb per-channel wavenumber ratios in `.xyz` (`.w` ignored); a length-4 array. Default
   *   `[1, 1.18, 1.42, 0]`.
   * @param floor metal floor; lower = higher contrast. Default `0.12`.
   * @param washout higher-order wash-out rate toward silver. Default `0.16`.
   * @param modulate `0..1` focal-pool follow strength. Default `1`.
   * @param rimBoost Fresnel rim gain toward the lens rim (`0` = off). Default `0`.
   */
  private fun chromatic(
    intensity: Float = 0.6f,
    gain: Float = 3.0f,
    krgb: FloatArray = floatArrayOf(1f, 1.18f, 1.42f, 0f),
    floor: Float = 0.12f,
    washout: Float = 0.16f,
    modulate: Float = 1f,
    rimBoost: Float = 0f,
  ): CompositeOptic<ChromaticParams> = Optic.composite(
    name = "chromatic",
    paramsFactory = {
      ChromaticParams(
        intensity = intensity,
        gain = gain,
        krgb = krgb,
        floor = floor,
        washout = washout,
        modulate = modulate,
        rimBoost = rimBoost,
      )
    },
    agsl = CHROMATIC_KERNEL_AGSL,
    sksl = CHROMATIC_KERNEL_SKSL,
  )
}

/**
 * Lens-geometry + specular-light uniforms shared by the lens-shaped presets ([MirageOptics.Specular],
 * the thin-film family, [MirageOptics.Foil]). The property names *are* the uniform identifiers the
 * kernels read; defaults mirror the built-in liquid-glass framing.
 *
 * @property lensCenter the lens center in local px. Default [Offset.Zero] (the content origin — an
 *   interactive lens should feed a pointer-tracked value).
 * @property lensSize the lens size in px. Default `350 x 350` (the liquid-glass default).
 * @property cornerRadius the lens corner radius in px. Default `50`.
 * @property iLight the specular light direction (unnormalized). Default `(-1, -1)`.
 */
@ExperimentalMirage
public abstract class MirageLensParams : MirageParams() {
  public val lensCenter: UOffset by uniform(Offset.Zero)
  public val lensSize: USize by uniform(Size(350f, 350f))
  public val cornerRadius: UFloat by uniform(50f)
  public val iLight: UOffset by uniform(Offset(-1f, -1f))
}

/**
 * Params for [MirageOptics.Specular]. The 11 `spec*` defaults are the `GlowTuning` values (= the
 * built-in `liquidGlass` glint), so the schema defines the look: changing a default here changes the
 * visual result.
 */
@ExperimentalMirage
public class SpecularParams : MirageLensParams() {
  public val specStrength: UFloat by uniform(0.7f)
  public val specPower: UFloat by uniform(10.0f)
  public val specRimMix: UFloat by uniform(0.4f)
  public val specWidthPx: UFloat by uniform(12.0f)
  public val specLightZ: UFloat by uniform(0.55f)
  public val specDomeFrac: UFloat by uniform(1.15f)
  public val specBodyPower: UFloat by uniform(2.5f)
  public val specBodyGain: UFloat by uniform(0.6f)
  public val specFocalK: UFloat by uniform(0.55f)
  public val specPoolFrac: UFloat by uniform(0.7f)
  public val specPoolGain: UFloat by uniform(1.3f)
}

/**
 * Params for the thin-film [chromatic][MirageOptics.chromatic] presets. Built with per-look defaults
 * so one kernel expresses every named look; the constructor arguments define each [MirageOptics] look
 * (e.g. [MirageOptics.OilSlick]'s `gain == 5.5`).
 *
 * `chromaticKRGB` is declared `float4` because the shader reads `.xyz` and the backends require a
 * write arity that matches the declared size; the `.w` component is unused.
 */
@ExperimentalMirage
public class ChromaticParams internal constructor(
  intensity: Float,
  gain: Float,
  krgb: FloatArray,
  floor: Float,
  washout: Float,
  modulate: Float,
  rimBoost: Float,
) : MirageLensParams() {
  public val chromaticIntensity: UFloat by uniform(intensity)
  public val chromaticGain: UFloat by uniform(gain)
  public val chromaticKRGB: UVec4 by uniform4(krgb)
  public val chromaticFloor: UFloat by uniform(floor)
  public val chromaticWashout: UFloat by uniform(washout)
  public val chromaticModulate: UFloat by uniform(modulate)
  public val chromaticRimBoost: UFloat by uniform(rimBoost)
}

/** Params for [MirageOptics.Foil] — the 5 foil/sparkle uniforms plus the shared lens framing. */
@ExperimentalMirage
public class FoilParams : MirageLensParams() {
  public val foilBands: UFloat by uniform(5f)
  public val foilPhase: UFloat by uniform(0f)
  public val chromaticGain: UFloat by uniform(3.6f)
  public val sparkleDensity: UFloat by uniform(16f)
  public val sparkleAmplitude: UFloat by uniform(0.3f)
}

/**
 * Params for [MirageOptics.Duotone] — the two grade endpoints plus the blend amount. Point-wise, so
 * it carries no lens framing.
 *
 * @property shadow the color mapped to the darkest luminance. Default a deep indigo.
 * @property highlight the color mapped to the brightest luminance. Default a warm cream.
 * @property amount `0..1` cross-fade from the original toward the graded duotone. Default `1`.
 */
@ExperimentalMirage
public class DuotoneParams : MirageParams() {
  public val shadow: UColor by uniformColor(Color(0xFF1B1B3A))
  public val highlight: UColor by uniformColor(Color(0xFFFFE8C7))
  public val amount: UFloat by uniform(1f)
}
