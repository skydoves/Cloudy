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
import com.skydoves.cloudy.internal.edsl.emitColorizeKernel
import com.skydoves.cloudy.internal.edsl.emitCompositeOrGenerateMain
import com.skydoves.cloudy.internal.edsl.traceChromatic
import com.skydoves.cloudy.internal.edsl.traceDuotone
import com.skydoves.cloudy.internal.edsl.traceFoil
import com.skydoves.cloudy.internal.edsl.traceSpecular

/**
 * Bundled [MirageShader] presets — the catalog of ready-to-apply looks.
 *
 * A preset is a kernel plus a default parameter set: the visual look lives entirely in the params'
 * declared defaults, never as a hard-coded shader constant, so every value is animatable and value
 * changes never recompile (the program cache is keyed on the kernel source, not the uniform values).
 * The named thin-film looks ([Chromatic] / [OilSlick] / [SoapBubble] / [MetallicFoil] / [Pearl]) are
 * the same [chromatic] factory at different defaults — one GPU program, five looks.
 *
 * Each value is a process-wide singleton, so applying one in a pipeline never reallocates the shader.
 *
 * Shared lens geometry ([MirageLensParams.lensCenter] / [lensSize][MirageLensParams.lensSize])
 * defaults to **auto framing**: left unspecified, it resolves at bind time to the node's center and
 * full size, so a preset applied with no `params` block covers the node it decorates — content or
 * backdrop — instead of pinning a fixed lens at the origin. [cornerRadius]
 * [MirageLensParams.cornerRadius] and the specular light ([MirageLensParams.iLight]) keep the built-in
 * liquid-glass values. Override any of them per draw from a `filter { … }` / `overlay { … }` block
 * (e.g. a pointer-tracked `lensCenter` with a fixed `lensSize` for an interactive lens, or feed
 * `iLight` a `rememberGyroLightSource` direction for motion lighting).
 */
@ExperimentalMirage
public object MirageShaders {

  /**
   * The Chromatic kernel text, traced through the eDSL ([com.skydoves.cloudy.internal.edsl.
   * traceChromatic]) once and shared by every named look ([Chromatic], [OilSlick], [SoapBubble],
   * [MetallicFoil], [Pearl]) — the kernel source depends only on the params *schema* (uniform slots),
   * never on a look's default values, so tracing it 5 times (once per [chromatic] call) would emit the
   * same text 5 times over. A probe [ChromaticParams] at its own defaults supplies that schema.
   *
   * Declared first in this object: [chromatic] (called while initializing [Chromatic]/[OilSlick]/etc.
   * below) reads this eagerly, and a Kotlin `object`'s properties initialize top-to-bottom — a `val`
   * declared after its first reader sees an uninitialized backing field (an NPE, not a compile error),
   * so this can't simply live next to [chromatic] where it's used.
   */
  private val chromaticKernel: String = run {
    val probe = ChromaticParams(0f, 0f, floatArrayOf(0f, 0f, 0f, 0f), 0f, 0f, 0f, 0f)
    val uniformNames = probe.schemaEntries.map { it.name }
    val module = traceChromatic(
      lensCenter = probe.lensCenter,
      lensSize = probe.lensSize,
      cornerRadius = probe.cornerRadius,
      iLight = probe.iLight,
      chromaticIntensity = probe.chromaticIntensity,
      chromaticGain = probe.chromaticGain,
      chromaticKRGB = probe.chromaticKRGB,
      chromaticFloor = probe.chromaticFloor,
      chromaticWashout = probe.chromaticWashout,
      chromaticModulate = probe.chromaticModulate,
      chromaticRimBoost = probe.chromaticRimBoost,
      chromaticPoolFrac = probe.chromaticPoolFrac,
    )
    emitCompositeOrGenerateMain(module, uniformNames)
  }

  /**
   * The liquid-glass specular glint (moving focal hotspot + Blinn rim). A [CompositeShader] because it
   * shares intermediates (SDF, bevel normal) across the refraction and specular terms. Its defaults
   * are the `GlowTuning` values, so applying it over the default lens framing reproduces the built-in
   * `liquidGlass` glint bit-for-bit.
   *
   * Traced through the eDSL ([com.skydoves.cloudy.internal.edsl.traceSpecular]) rather than
   * hand-written AGSL/SkSL strings — the first ported kernel with a mutable local ([MutableLocal],
   * `pixel`), a non-exiting conditional block ([IfBlock]), and free `content.eval` sampling
   * ([SampleContent]) via multiple taps.
   */
  public val Specular: CompositeShader<SpecularParams> = run {
    val probe = SpecularParams()
    val uniformNames = probe.schemaEntries.map { it.name }
    val module = traceSpecular(
      lensCenter = probe.lensCenter,
      lensSize = probe.lensSize,
      cornerRadius = probe.cornerRadius,
      iLight = probe.iLight,
      specStrength = probe.specStrength,
      specPower = probe.specPower,
      specRimMix = probe.specRimMix,
      specWidthPx = probe.specWidthPx,
      specLightZ = probe.specLightZ,
      specDomeFrac = probe.specDomeFrac,
      specBodyPower = probe.specBodyPower,
      specBodyGain = probe.specBodyGain,
      specFocalK = probe.specFocalK,
      specPoolFrac = probe.specPoolFrac,
      specPoolGain = probe.specPoolGain,
    )
    val kernel = emitCompositeOrGenerateMain(module, uniformNames)
    MirageShader.composite(
      name = "specular",
      paramsFactory = ::SpecularParams,
      agsl = kernel,
      sksl = kernel,
    )
  }

  /** The default thin-film iridescence look — the [chromatic] factory at its defaults. */
  public val Chromatic: CompositeShader<ChromaticParams> = chromatic()

  /**
   * Oil-slick: high band count, wide RGB spread, near-zero metal floor — a saturated, dark-based
   * rainbow with little wash-out.
   */
  public val OilSlick: CompositeShader<ChromaticParams> = chromatic(
    gain = 5.5f,
    krgb = floatArrayOf(1f, 1.30f, 1.72f, 0f),
    floor = 0.05f,
    washout = 0.07f,
    modulate = 0.75f,
  )

  /** Soap-bubble: few, wide bands with a high floor and strong wash-out — pale, pastel iridescence. */
  public val SoapBubble: CompositeShader<ChromaticParams> = chromatic(
    gain = 1.7f,
    krgb = floatArrayOf(1f, 1.11f, 1.26f, 0f),
    floor = 0.22f,
    washout = 0.50f,
    modulate = 0.22f,
  )

  /** Metallic foil: dark floor + a Fresnel rim boost toward white at the edge — a sharp metallic sheen. */
  public val MetallicFoil: CompositeShader<ChromaticParams> = chromatic(
    gain = 3.6f,
    krgb = floatArrayOf(1f, 1.26f, 1.62f, 0f),
    floor = 0.03f,
    washout = 0.05f,
    modulate = 0.82f,
    rimBoost = 0.45f,
  )

  /** Pearl: high floor + strong wash-out + a rim boost — a soft, luminous, low-saturation lustre. */
  public val Pearl: CompositeShader<ChromaticParams> = chromatic(
    gain = 2.4f,
    krgb = floatArrayOf(1f, 1.07f, 1.18f, 0f),
    floor = 0.46f,
    washout = 0.58f,
    modulate = 0.20f,
    rimBoost = 0.45f,
  )

  /**
   * A foil overlay — a content-free [GeneratorShader] (glare + flowing rainbow + anti-aliased sparkle)
   * drawn over the content. Declare it via `overlay(MirageShaders.Foil)` so it composites on top of any
   * filter result; its `mirageTime` reference lets the clock drive the sparkle shimmer.
   *
   * Traced through the eDSL ([com.skydoves.cloudy.internal.edsl.traceFoil]) rather than hand-written
   * AGSL/SkSL strings — the first Generate-category, control-flow-bearing (early-return guard) kernel
   * ported, proving the eDSL beyond the point-wise, control-flow-free Duotone MVP.
   */
  public val Foil: GeneratorShader<FoilParams> = run {
    val probe = FoilParams()
    val uniformNames = probe.schemaEntries.map { it.name }
    val module = traceFoil(
      lensCenter = probe.lensCenter,
      lensSize = probe.lensSize,
      cornerRadius = probe.cornerRadius,
      iLight = probe.iLight,
      foilBands = probe.foilBands,
      foilPhase = probe.foilPhase,
      chromaticGain = probe.chromaticGain,
      sparkleDensity = probe.sparkleDensity,
      sparkleAmplitude = probe.sparkleAmplitude,
    )
    val kernel = emitCompositeOrGenerateMain(module, uniformNames)
    MirageShader.generate(
      name = "foil",
      paramsFactory = ::FoilParams,
      agsl = kernel,
      sksl = kernel,
    )
  }

  /**
   * A point-wise duotone grade: maps luminance onto a [shadow][DuotoneParams.shadow] →
   * [highlight][DuotoneParams.highlight] gradient and cross-fades by [amount][DuotoneParams.amount].
   * A [ColorizeShader], so it fuses cheaply and needs no lens framing. The defaults are a warm
   * split-tone (deep indigo shadows, cream highlights).
   *
   * The kernel body is authored once in [com.skydoves.cloudy.internal.edsl.traceDuotone] (the eDSL,
   * not a hand-written AGSL/SkSL string pair) and emitted here for both dialects — see
   * mirage-edsl-design.md for why one emitted text is valid for both.
   */
  public val Duotone: ColorizeShader<DuotoneParams> = run {
    val probe = DuotoneParams()
    val uniformNames = probe.schemaEntries.map { it.name }
    val module = traceDuotone(probe.shadow, probe.highlight, probe.amount)
    val kernel = emitColorizeKernel(module, uniformNames)
    MirageShader.colorize(
      name = "duotone",
      paramsFactory = ::DuotoneParams,
      agsl = kernel,
      sksl = kernel,
    )
  }

  /**
   * Builds a thin-film (Newton's-rings) iridescence [CompositeShader] from its per-look parameters. It
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
  ): CompositeShader<ChromaticParams> = MirageShader.composite(
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
    agsl = chromaticKernel,
    sksl = chromaticKernel,
  )
}

/**
 * Lens-geometry + specular-light uniforms shared by the lens-shaped presets ([MirageShaders.Specular],
 * the thin-film family, [MirageShaders.Foil]). The property names *are* the uniform identifiers the
 * kernels read.
 *
 * Lens framing defaults to **auto**: an [Offset.Unspecified] [lensCenter] / [Size.Unspecified]
 * [lensSize] resolves at bind time to the node's center / full size, so a bare preset frames the node
 * it is attached to. A fixed default would instead pin the lens at the content origin, leaving
 * everything outside it as the kernels' `content.eval(xy)` passthrough — on a backdrop card that reads
 * as "the effect draws behind the content".
 *
 * @property lensCenter the lens center in local px. Default [Offset.Unspecified] (auto: the node
 *   center at draw time — an interactive lens should feed a pointer-tracked value).
 * @property lensSize the lens size in px. Default [Size.Unspecified] (auto: the node size, so the
 *   lens covers the node full-bleed).
 * @property cornerRadius the lens corner radius in px. Default `50`.
 * @property iLight the specular light direction (unnormalized). Default `(-1, -1)`.
 */
@ExperimentalMirage
public abstract class MirageLensParams : MirageParams() {
  public val lensCenter: UOffset by uniform(Offset.Unspecified)
  public val lensSize: USize by uniform(Size.Unspecified)
  public val cornerRadius: UFloat by uniform(50f)
  public val iLight: UOffset by uniform(Offset(-1f, -1f))
}

/**
 * Params for [MirageShaders.Specular]. The 11 `spec*` defaults are the `GlowTuning` values (= the
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
 * Params for the thin-film [chromatic][MirageShaders.chromatic] presets. Built with per-look defaults
 * so one kernel expresses every named look; the constructor arguments define each [MirageShaders] look
 * (e.g. [MirageShaders.OilSlick]'s `gain == 5.5`).
 *
 * `chromaticKRGB` is declared `float4` because the shader reads `.xyz` and the backends require a
 * write arity that matches the declared size; the `.w` component is unused.
 *
 * `chromaticPoolFrac` scales the light focal pool radius as a fraction of the lens' half-min
 * dimension (the same basis as [SpecularParams.specPoolFrac]). The default `0.7` matches the
 * specular pool; raise it (e.g. `1.5`–`2`) so the pool spans a wide lens whose short side would
 * otherwise confine the rainbow to a small patch. It shapes the pool that
 * [chromaticModulate][ChromaticParams.chromaticModulate] blends in, so it has no effect at
 * `modulate = 0`.
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
  public val chromaticPoolFrac: UFloat by uniform(0.7f)
}

/** Params for [MirageShaders.Foil] — the 5 foil/sparkle uniforms plus the shared lens framing. */
@ExperimentalMirage
public class FoilParams : MirageLensParams() {
  public val foilBands: UFloat by uniform(5f)
  public val foilPhase: UFloat by uniform(0f)
  public val chromaticGain: UFloat by uniform(3.6f)
  public val sparkleDensity: UFloat by uniform(16f)
  public val sparkleAmplitude: UFloat by uniform(0.3f)
}

/**
 * Params for [MirageShaders.Duotone] — the two grade endpoints plus the blend amount. Point-wise, so
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
