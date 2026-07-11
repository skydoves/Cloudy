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

package com.skydoves.cloudy.internal.edsl

import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.UFloat
import com.skydoves.cloudy.UOffset
import com.skydoves.cloudy.USize
import com.skydoves.cloudy.UVec4

/**
 * Traces the Chromatic body: a superellipse-bevel thin-film (Newton's-rings) tint, alpha-branch
 * blended (multiply on transparent, screen glow on opaque), masked to the lens. One kernel expresses
 * every named look ([com.skydoves.cloudy.MirageShaders.OilSlick] etc.) purely through the uniform
 * defaults [com.skydoves.cloudy.MirageShaders.chromatic] passes in — this trace runs once regardless
 * of which look's [com.skydoves.cloudy.ChromaticParams] instance provides the handles, since the
 * kernel source depends only on the *schema* (slots), not the per-look default values. Ports
 * [com.skydoves.cloudy.internal.CHROMATIC_KERNEL_AGSL] statement-for-statement.
 */
internal fun traceChromatic(
  lensCenter: UOffset,
  lensSize: USize,
  cornerRadius: UFloat,
  iLight: UOffset,
  chromaticIntensity: UFloat,
  chromaticGain: UFloat,
  chromaticKRGB: UVec4,
  chromaticFloor: UFloat,
  chromaticWashout: UFloat,
  chromaticModulate: UFloat,
  chromaticRimBoost: UFloat,
  chromaticPoolFrac: UFloat,
): ShaderModule {
  val scope = MirageShaderScope()
  val xy = Float2(Argument("xy", ShaderType.Float2))
  val smoothEdgePx = float1(1.5f) // SMOOTH_EDGE_PX
  val chromaOpdBase = float1(0.10f)
  val chromaThickMix = float1(0.55f)
  val chromaRimPow = float1(3f)
  val chromaSePow = float1(4f)

  val halfDim = scope.let("halfDim", lensSize.lift() * float1(0.5f))
  val r = scope.let("r", min(cornerRadius.lift(), min(halfDim.x, halfDim.y)))
  val p = scope.let("p", xy - lensCenter.lift())
  val sdf = scope.let("sdf", Float1(Call("boxRoundedSDF", listOf(p.e, halfDim.e, r.e), ShaderType.Float1)))

  scope.guard(sdf gt smoothEdgePx, sampleContent(xy))

  val firstTap = scope.letMutable("pixel", sampleContent(xy))

  val minHalf = scope.let("minHalf", min(halfDim.x, halfDim.y))
  val cLightVec = scope.let("cLightVec", normalize(iLight.lift()))
  val q = scope.let("q", abs(p) / float2(max(halfDim.x, float1(1f)), max(halfDim.y, float1(1f))))
  val s2 = scope.let("s2", float2(signSelect(p.x), signSelect(p.y)))
  val f = scope.let("f", pow(pow(q.x, chromaSePow) + pow(q.y, chromaSePow), float1(1f) / chromaSePow))
  val cDir = scope.let(
    "cDir",
    normalize(
      s2 * float2(
        chromaSePow * pow(q.x, chromaSePow - float1(1f)) / max(halfDim.x, float1(1f)),
        chromaSePow * pow(q.y, chromaSePow - float1(1f)) / max(halfDim.y, float1(1f)),
      ) + float2(1.0e-4f, 1.0e-4f),
    ),
  )
  val t = scope.let("t", clamp(f, float1(0f), float1(1f)))
  val nCos = scope.let("n_cos", float1(1f) - t)
  val nSin = scope.let("n_sin", sqrt(max(float1(1f) - nCos * nCos, float1(0f))))
  val cN = scope.let("cN", normalize(float3(cDir * nCos, nSin + float1(1.0e-3f))))
  val cL = scope.let("cL", normalize(float3(cLightVec, float1(0.55f))))

  val cosT = scope.let("cosT", clamp(dot(cN, cL), float1(0f), float1(1f)))
  val thick = scope.let("thick", float1(1f) - nCos)
  val ringTerm = scope.let("ringTerm", thick / max(float1(1f) - float1(0.6f) * cosT, float1(1.0e-2f)))
  val opdDrive = scope.let("opdDrive", mix(cosT, ringTerm, chromaThickMix))
  val opd = scope.let("opd", opdDrive * chromaticGain.lift() + chromaOpdBase)
  val interf = scope.let(
    "interf",
    float3(0.5f, 0.5f, 0.5f) + float3(0.5f, 0.5f, 0.5f) * cos(float1(6.28318530718f) * opd * chromaticKRGB.lift().xyz),
  )
  val metalRGB = scope.let(
    "metalRGB",
    float3(chromaticFloor.lift()) + (float1(1f) - chromaticFloor.lift()) * interf,
  )
  val sat = scope.let("sat", exp(-opd * chromaticWashout.lift()))
  val thinFilm = scope.let("thinFilm", mix(float3(1f, 1f, 1f), metalRGB, clamp(sat, float1(0f), float1(1f))))
  val rimBoost = scope.let(
    "rimBoost",
    chromaticRimBoost.lift() * pow(clamp(thick, float1(0f), float1(1f)), chromaRimPow),
  )
  val chromaRGB = scope.let(
    "chromaRGB",
    mix(thinFilm, float3(1f, 1f, 1f), clamp(rimBoost, float1(0f), float1(1f))),
  )

  val cFocal = scope.let("cFocal", cLightVec * (minHalf * float1(0.55f)))
  val cPoolR = scope.let("cPoolR", max(minHalf * chromaticPoolFrac.lift(), float1(1f)))
  val cPool = scope.let("cPool", float1(1f) - smoothstep(float1(0f), cPoolR, length(p - cFocal)))
  val poolNorm = scope.let("poolNorm", clamp(cPool * cPool, float1(0f), float1(1f)))
  val chroma = scope.let(
    "chroma",
    chromaticIntensity.lift() * mix(float1(1f), poolNorm, clamp(chromaticModulate.lift(), float1(0f), float1(1f))),
  )

  val cChroma = scope.let("cChroma", half(clamp(chroma, float1(0f), float1(1f))))
  val cChromaRGB = scope.let("cChromaRGB", half3(chromaRGB) * cChroma)
  val cOnWhite = scope.let("cOnWhite", half3(chromaRGB))
  val pixelBeforeBlend = firstTap.get()
  val cOnSrc = scope.let(
    "cOnSrc",
    half3(1f) - (half3(1f) - pixelBeforeBlend.rgb) * (half3(1f) - cChromaRGB),
  )
  firstTap.set(half4(mix(cOnWhite, cOnSrc, pixelBeforeBlend.a), max(pixelBeforeBlend.a, cChroma)))

  val alpha = scope.let("alpha", float1(1f) - smoothstep(-smoothEdgePx * float1(0.5f), smoothEdgePx * float1(0.5f), sdf))
  val bg = scope.let("bg", sampleContent(xy))
  val result = mix(bg, firstTap.get(), alpha)
  return scope.build(result)
}
