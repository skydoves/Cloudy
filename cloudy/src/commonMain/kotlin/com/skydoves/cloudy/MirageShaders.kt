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
@file:OptIn(ExperimentalMirage::class)

package com.skydoves.cloudy

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.skydoves.cloudy.edsl.Float2
import com.skydoves.cloudy.edsl.Half4
import com.skydoves.cloudy.edsl.If
import com.skydoves.cloudy.edsl.a
import com.skydoves.cloudy.edsl.abs
import com.skydoves.cloudy.edsl.and
import com.skydoves.cloudy.edsl.boxRoundedSDF
import com.skydoves.cloudy.edsl.clamp
import com.skydoves.cloudy.edsl.cos
import com.skydoves.cloudy.edsl.div
import com.skydoves.cloudy.edsl.dot
import com.skydoves.cloudy.edsl.exp
import com.skydoves.cloudy.edsl.float1
import com.skydoves.cloudy.edsl.float2
import com.skydoves.cloudy.edsl.float3
import com.skydoves.cloudy.edsl.float4
import com.skydoves.cloudy.edsl.floor
import com.skydoves.cloudy.edsl.foilHash
import com.skydoves.cloudy.edsl.fract
import com.skydoves.cloudy.edsl.greaterThan
import com.skydoves.cloudy.edsl.greaterThanEqual
import com.skydoves.cloudy.edsl.guard
import com.skydoves.cloudy.edsl.half
import com.skydoves.cloudy.edsl.half3
import com.skydoves.cloudy.edsl.half4
import com.skydoves.cloudy.edsl.length
import com.skydoves.cloudy.edsl.lensNormalDirection
import com.skydoves.cloudy.edsl.lessThanEqual
import com.skydoves.cloudy.edsl.local
import com.skydoves.cloudy.edsl.luma
import com.skydoves.cloudy.edsl.max
import com.skydoves.cloudy.edsl.min
import com.skydoves.cloudy.edsl.minus
import com.skydoves.cloudy.edsl.mirageTime
import com.skydoves.cloudy.edsl.mix
import com.skydoves.cloudy.edsl.normalize
import com.skydoves.cloudy.edsl.plus
import com.skydoves.cloudy.edsl.pow
import com.skydoves.cloudy.edsl.processColor
import com.skydoves.cloudy.edsl.rgb
import com.skydoves.cloudy.edsl.sampleContent
import com.skydoves.cloudy.edsl.signSelect
import com.skydoves.cloudy.edsl.sin
import com.skydoves.cloudy.edsl.smoothstep
import com.skydoves.cloudy.edsl.sqrt
import com.skydoves.cloudy.edsl.step
import com.skydoves.cloudy.edsl.times
import com.skydoves.cloudy.edsl.unaryMinus
import com.skydoves.cloudy.edsl.x
import com.skydoves.cloudy.edsl.xyz
import com.skydoves.cloudy.edsl.y

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
   * The Chromatic thin-film body: a superellipse-bevel Newton's-rings tint, alpha-branch blended
   * (multiply on transparent, screen glow on opaque), masked to the lens. Held as a lambda so
   * [chromaticKernel] can trace + emit it once and every look shares that one kernel text; the body
   * depends only on the uniform *schema* (slots), never on a look's default values.
   *
   * Declared first (above [chromaticKernel], its sole reader): a Kotlin `object` initializes its
   * properties top-to-bottom, so a `val` read before its own declaration sees an uninitialized field.
   */
  private val chromaticBody: ChromaticParams.(Float2) -> Half4 = { xy ->
    val smoothEdgePx = 1.5f // SMOOTH_EDGE_PX
    val chromaOpdBase = 0.10f
    val chromaThickMix = 0.55f
    val chromaRimPow = 3.0f
    val chromaSePow = 4.0f

    val halfDim = lensSize * 0.5f
    val r = min(cornerRadius, min(halfDim.x, halfDim.y))
    val p = xy - lensCenter
    val sdf = boxRoundedSDF(p, halfDim, r)

    guard(sdf greaterThan smoothEdgePx) { sampleContent(xy) }

    var pixel by local(sampleContent(xy))

    val minHalf = min(halfDim.x, halfDim.y)
    val cLightVec = normalize(iLight)
    val q = abs(p) / float2(max(halfDim.x, 1f), max(halfDim.y, 1f))
    val s2 = float2(signSelect(p.x), signSelect(p.y))
    val f = pow(pow(q.x, chromaSePow) + pow(q.y, chromaSePow), 1f / chromaSePow)
    val cDir = normalize(
      s2 * float2(
        chromaSePow * pow(q.x, chromaSePow - 1f) / max(halfDim.x, 1f),
        chromaSePow * pow(q.y, chromaSePow - 1f) / max(halfDim.y, 1f),
      ) + float2(1.0e-4f, 1.0e-4f),
    )
    val t = clamp(f, 0f, 1f)
    val nCos = 1f - t
    val nSin = sqrt(max(1f - nCos * nCos, 0f))
    val cN = normalize(float3(cDir * nCos, nSin + 1.0e-3f))
    val cL = normalize(float3(cLightVec, 0.55f))

    val cosT = clamp(dot(cN, cL), 0f, 1f)
    val thick = 1f - nCos
    val ringTerm = thick / max(1f - 0.6f * cosT, 1.0e-2f)
    val opdDrive = mix(cosT, ringTerm, chromaThickMix)
    val opd = opdDrive * chromaticGain + chromaOpdBase
    val interf =
      float3(0.5f, 0.5f, 0.5f) +
        float3(0.5f, 0.5f, 0.5f) * cos(6.28318530718f * opd * chromaticKRGB.xyz)
    val metalRGB = float3(chromaticFloor) + (1f - chromaticFloor) * interf
    val sat = exp(-opd * chromaticWashout)
    val thinFilm = mix(float3(1f, 1f, 1f), metalRGB, clamp(sat, 0f, 1f))
    val rimBoost = chromaticRimBoost * pow(clamp(thick, 0f, 1f), chromaRimPow)
    val chromaRGB = mix(thinFilm, float3(1f, 1f, 1f), clamp(rimBoost, 0f, 1f))

    val cFocal = cLightVec * (minHalf * 0.55f)
    val cPoolR = max(minHalf * chromaticPoolFrac, 1f)
    val cPool = 1f - smoothstep(0f, cPoolR, length(p - cFocal))
    val poolNorm = clamp(cPool * cPool, 0f, 1f)
    val chroma = chromaticIntensity * mix(1f, poolNorm, clamp(chromaticModulate, 0f, 1f))

    val cChroma = half(clamp(chroma, 0f, 1f))
    val cChromaRGB = half3(chromaRGB) * cChroma
    val cOnWhite = half3(chromaRGB)
    val pixelBeforeBlend = pixel
    val cOnSrc = half3(1f) - (half3(1f) - pixelBeforeBlend.rgb) * (half3(1f) - cChromaRGB)
    pixel = half4(mix(cOnWhite, cOnSrc, pixelBeforeBlend.a), max(pixelBeforeBlend.a, cChroma))

    val alpha = 1f - smoothstep(-smoothEdgePx * 0.5f, smoothEdgePx * 0.5f, sdf)
    val bg = sampleContent(xy)
    mix(bg, pixel, alpha)
  }

  /**
   * The Chromatic kernel text, emitted **once** from [chromaticBody] and shared by every named look
   * ([Chromatic], [OilSlick], [SoapBubble], [MetallicFoil], [Pearl]) — the kernel source depends only
   * on the params *schema* (uniform slots), never on a look's default values, so all five looks
   * compile to one GPU program (the raster tests assert `oil.source == soap.source == ...`). Tracing
   * the body once and reusing the string keeps that guarantee free of any emit-determinism assumption.
   *
   * Declared first in this object: [chromatic] (called while initializing [Chromatic]/[OilSlick]/etc.
   * below) reads this eagerly, and a Kotlin `object`'s properties initialize top-to-bottom — a `val`
   * declared after its first reader sees an uninitialized backing field (an NPE, not a compile error),
   * so this can't simply live next to [chromatic] where it's used.
   */
  private val chromaticKernel: String =
    MirageShader.composite("chromatic", {
      ChromaticParams(0f, 0f, floatArrayOf(0f, 0f, 0f, 0f), 0f, 0f, 0f, 0f)
    }, chromaticBody).agsl

  /**
   * The liquid-glass specular glint (moving focal hotspot + Blinn rim). A [CompositeShader] because it
   * shares intermediates (SDF, bevel normal) across the refraction and specular terms. Its defaults
   * are the `GlowTuning` values, so applying it over the default lens framing reproduces the built-in
   * `liquidGlass` glint bit-for-bit.
   *
   * Authored as an eDSL body lambda — a mutable `pixel` ([local]) reassigned from a `content.eval`
   * fallback and again inside a non-exiting [If], a `&&`-gated highlight, and multiple
   * [sampleContent] taps.
   */
  public val Specular: CompositeShader<SpecularParams> =
    MirageShader.composite("specular", ::SpecularParams) { xy ->
      val smoothEdgePx = 1.5f // SMOOTH_EDGE_PX, the preamble's shared edge-blend constant
      val specSePow = 4.0f // SPEC_SE_POW

      val halfDim = lensSize * 0.5f
      val r = min(cornerRadius, min(halfDim.x, halfDim.y))
      val p = xy - lensCenter
      val sdf = boxRoundedSDF(p, halfDim, r)

      guard(sdf greaterThan smoothEdgePx) { sampleContent(xy) }

      val normal = lensNormalDirection(p, halfDim, r)

      // The `{ ... }` scratch block computing sampleXY: its locals (minDim/depth/curvature/bend) are
      // scoped to that block in the source but never read outside it, so tracing them as ordinary
      // vals here is observationally identical — nothing after this reads them.
      val minDim = min(halfDim.x, halfDim.y)
      val depth = clamp(-sdf / (minDim * 0.25f), 0f, 1f)
      val curvature = 1f - depth
      val bend = 1f - sqrt(1f - curvature * curvature)
      val sampleXY = xy - normal * (bend * 0.25f * minDim)

      var pixel by local(sampleContent(sampleXY))
      If(pixel.a lessThanEqual 0f) { pixel = sampleContent(xy) }
      pixel = half4(processColor(pixel.rgb, 1f, 1f, float4(0f, 0f, 0f, 0f)), pixel.a)

      val edge = float1(0.2f)

      If((edge greaterThan 0f) and (specStrength greaterThan 0f)) {
        val lightVec = normalize(iLight)
        val minHalf = min(halfDim.x, halfDim.y)
        val q = abs(p) / float2(max(halfDim.x, 1f), max(halfDim.y, 1f))
        val s2 = float2(signSelect(p.x), signSelect(p.y))
        val seF = pow(pow(q.x, specSePow) + pow(q.y, specSePow), 1f / specSePow)
        val specDir2 = normalize(
          s2 * float2(
            specSePow * pow(q.x, specSePow - 1f) / max(halfDim.x, 1f),
            specSePow * pow(q.y, specSePow - 1f) / max(halfDim.y, 1f),
          ) + float2(1.0e-4f, 1.0e-4f),
        )

        val t = clamp(seF / max(specDomeFrac, 1.0e-2f), 0f, 1f)
        val nCos = 1f - t
        val nSin = sqrt(max(1f - nCos * nCos, 0f))
        val nn = normalize(float3(specDir2 * nCos, nSin + 1.0e-3f))

        val ll = normalize(float3(lightVec, specLightZ))
        val vv = float3(0f, 0f, 1f)

        val focal = lightVec * (minHalf * specFocalK)
        val poolR = max(minHalf * specPoolFrac, 1f)
        val poolD = length(p - focal)
        val pool = 1f - smoothstep(0f, poolR, poolD)
        val inside = 1f - smoothstep(-6f, 0f, sdf)
        val focalPool = pool * pool * specStrength * specPoolGain * inside

        val ndl = max(dot(nn, ll), 0f)
        val bodySheen = pow(ndl, specBodyPower) * specStrength * specBodyGain

        val hh = normalize(ll + vv)
        val rimBand = smoothstep(-max(specWidthPx, 1f), 0f, sdf)
        val glint = pow(max(dot(nn, hh), 0f), specPower) * specStrength
        val rim = glint * rimBand

        val lb = normalize(float3(-lightVec, specLightZ))
        val back = pow(max(dot(nn, lb), 0f), specPower) * specStrength * rimBand * 0.25f

        val hp = fract((p / minHalf) * 0.5f + 0.5f)
        val dn = fract(sin(dot(hp, float2(12.9898f, 78.233f))) * 43758.5453f) - 0.5f

        val body = focalPool + bodySheen + dn * (1f / 255f) * specStrength
        val rimMix = clamp(specRimMix, 0f, 1f)
        val highlight = body * (1f - rimMix) + (rim + back) * rimMix

        pixel = half4(
          pixel.rgb + (half3(1f) - pixel.rgb) * clamp(highlight, 0f, 1f),
          pixel.a,
        )
      }

      val alpha = 1f - smoothstep(-smoothEdgePx * 0.5f, smoothEdgePx * 0.5f, sdf)
      val bg = sampleContent(xy)
      mix(bg, pixel, alpha)
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
   * filter result; its [mirageTime] reference lets the clock drive the sparkle shimmer.
   *
   * Authored as an eDSL body lambda — a Generate `main(float2 xy)` with an early-return [guard] and a
   * user-defined helper ([foilHash]).
   */
  public val Foil: GeneratorShader<FoilParams> =
    MirageShader.generate("foil", ::FoilParams) { xy ->
      val smoothEdgePx = 1.5f // SMOOTH_EDGE_PX

      val halfDim = lensSize * 0.5f
      val r = min(cornerRadius, min(halfDim.x, halfDim.y))
      val p = xy - lensCenter
      val sdf = boxRoundedSDF(p, halfDim, r)

      guard(sdf greaterThan smoothEdgePx) { half4(0f) }

      val minHalf = min(halfDim.x, halfDim.y)
      val cLightVec = normalize(iLight)
      val pNorm = p / minHalf
      val t = clamp(max(-sdf, 0f) / max(minHalf, 1f), 0f, 1f)

      val along = dot(pNorm, cLightVec)
      val glare = smoothstep(0.2f, 1f, along) * (1f - t)
      val dome = (1f - smoothstep(0f, 1f, length(pNorm))) * 0.5f

      val hueF = fract(along * foilBands + foilPhase + 0.05f * mirageTime)
      val hsv = clamp(
        abs(fract(float3(hueF) + float3(0f, 2f / 3f, 1f / 3f)) * 6f - 3f) - 1f,
        0f,
        1f,
      )
      val opd = (0.5f + 0.5f * t) * chromaticGain
      val film =
        float3(0.5f, 0.5f, 0.5f) +
          float3(0.5f, 0.5f, 0.5f) * cos(6.28318530718f * opd * float3(1f, 1.18f, 1.42f))
      val rainbow = mix(hsv, film, 0.4f)

      val cell = floor(pNorm * sparkleDensity)
      val h = foilHash(cell)
      val cellUv = fract(pNorm * sparkleDensity) - float2(0.5f, 0.5f)
      val d = length(cellUv)
      val aa = clamp(sparkleDensity / max(minHalf, 1f), 0.02f, 0.25f)
      val dot0 = 1f - smoothstep(0.18f - aa, 0.18f + aa, d)
      val twinkle = 0.5f + 0.5f * sin(6.2831853f * (h + 0.3f * mirageTime))
      val spark = step(0.78f, h) * dot0 * twinkle * sparkleAmplitude

      val lum = clamp(glare + dome, 0f, 1f)
      val rgb = rainbow * lum + float3(spark)
      val a = clamp(lum + spark, 0f, 1f)
      val mask = 1f - smoothstep(-smoothEdgePx * 0.5f, smoothEdgePx * 0.5f, sdf)

      half4(half3(rgb) * half(mask), half(a * mask))
    }

  /**
   * A point-wise duotone grade: maps luminance onto a [shadow][DuotoneParams.shadow] →
   * [highlight][DuotoneParams.highlight] gradient and cross-fades by [amount][DuotoneParams.amount].
   * A [ColorizeShader], so it fuses cheaply and needs no lens framing. The defaults are a warm
   * split-tone (deep indigo shadows, cream highlights).
   *
   * Authored as an eDSL body lambda (a point-wise `kernel(float2 p, half4 src)`), emitted once and
   * reused for both dialects (AGSL and SkSL share this authoring surface).
   */
  public val Duotone: ColorizeShader<DuotoneParams> =
    MirageShader.colorize("duotone", ::DuotoneParams) { src ->
      val g = luma(src.rgb)
      val dz = mix(shadow.rgb, highlight.rgb, g)
      half4(mix(src.rgb, dz, amount), src.a)
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
