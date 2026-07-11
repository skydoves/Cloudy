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

/**
 * `foilHash(c)`: a bounded-input hash so `sin()` never blows up at scale — the same formula
 * [MirageKernels.FOIL_KERNEL_AGSL] declares as a top-level helper function ahead of `main`.
 */
private fun MirageShaderScope.foilHash(c: Float2): Float1 =
  Float1(Call("foilHash", listOf(c.e), ShaderType.Float1))

private val foilHashHelper = HelperFunction(
  name = "foilHash",
  paramName = "c",
  paramType = ShaderType.Float2,
  body = fract(sin(dot(Float2(Argument("c", ShaderType.Float2)), float2(float1(127.1f), float1(311.7f)))) * float1(43758.5453f)).e,
)

/**
 * Traces the Foil overlay body: glare + thin-film rainbow + anti-aliased sparkle, masked to the lens.
 * Ports [MirageKernels.FOIL_KERNEL_AGSL] statement-for-statement (same intermediate names) so the two
 * are diffable line-by-line; the raster-parity test is what proves the port, not the resemblance.
 */
internal fun traceFoil(
  lensCenter: UOffset,
  lensSize: USize,
  cornerRadius: UFloat,
  iLight: UOffset,
  foilBands: UFloat,
  foilPhase: UFloat,
  chromaticGain: UFloat,
  sparkleDensity: UFloat,
  sparkleAmplitude: UFloat,
): ShaderModule {
  val scope = MirageShaderScope()
  val xy = Float2(Argument("xy", ShaderType.Float2))
  val smoothEdgePx = float1(1.5f) // SMOOTH_EDGE_PX, the preamble's shared edge-blend constant

  val halfDim = scope.let("halfDim", lensSize.lift() * float1(0.5f))
  val r = scope.let("r", min(cornerRadius.lift(), min(halfDim.x, halfDim.y)))
  val p = scope.let("p", xy - lensCenter.lift())
  val sdf = scope.let("sdf", Float1(Call("boxRoundedSDF", listOf(p.e, halfDim.e, r.e), ShaderType.Float1)))

  scope.guard(sdf gt smoothEdgePx, half4(0f))

  val minHalf = scope.let("minHalf", min(halfDim.x, halfDim.y))
  val cLightVec = scope.let("cLightVec", normalize(iLight.lift()))
  val pNorm = scope.let("pNorm", p / minHalf)
  val t = scope.let(
    "t",
    clamp(max(-sdf, float1(0f)) / max(minHalf, float1(1f)), float1(0f), float1(1f)),
  )

  val along = scope.let("along", dot(pNorm, cLightVec))
  val glare = scope.let("glare", smoothstep(float1(0.2f), float1(1f), along) * (float1(1f) - t))
  val dome = scope.let(
    "dome",
    (float1(1f) - smoothstep(float1(0f), float1(1f), length(pNorm))) * float1(0.5f),
  )

  val hueF = scope.let(
    "hueF",
    fract(along * foilBands.lift() + foilPhase.lift() + float1(0.05f) * scope.mirageTime),
  )
  val hsv = scope.let(
    "hsv",
    clamp(
      abs(fract(float3(hueF) + float3(0f, 2f / 3f, 1f / 3f)) * float1(6f) - float1(3f)) - float1(1f),
      float1(0f),
      float1(1f),
    ),
  )
  val opd = scope.let("opd", (float1(0.5f) + float1(0.5f) * t) * chromaticGain.lift())
  val film = scope.let(
    "film",
    float3(0.5f, 0.5f, 0.5f) + float3(0.5f, 0.5f, 0.5f) * cos(float1(6.28318530718f) * opd * float3(1f, 1.18f, 1.42f)),
  )
  val rainbow = scope.let("rainbow", mix(hsv, film, float1(0.4f)))

  val cell = scope.let("cell", floor(pNorm * sparkleDensity.lift()))
  val h = scope.let("h", scope.foilHash(cell))
  val cellUv = scope.let("cellUv", fract(pNorm * sparkleDensity.lift()) - float2(float1(0.5f), float1(0.5f)))
  val d = scope.let("d", length(cellUv))
  val aa = scope.let("aa", clamp(sparkleDensity.lift() / max(minHalf, float1(1f)), float1(0.02f), float1(0.25f)))
  val dot0 = scope.let("dot0", float1(1f) - smoothstep(float1(0.18f) - aa, float1(0.18f) + aa, d))
  val twinkle = scope.let(
    "twinkle",
    float1(0.5f) + float1(0.5f) * sin(float1(6.2831853f) * (h + float1(0.3f) * scope.mirageTime)),
  )
  val spark = scope.let("spark", step(float1(0.78f), h) * dot0 * twinkle * sparkleAmplitude.lift())

  val lum = scope.let("lum", clamp(glare + dome, float1(0f), float1(1f)))
  val rgb = scope.let("rgb", rainbow * lum + float3(spark))
  val a = scope.let("a", clamp(lum + spark, float1(0f), float1(1f)))
  val mask = scope.let("mask", float1(1f) - smoothstep(-smoothEdgePx * float1(0.5f), smoothEdgePx * float1(0.5f), sdf))

  val result = half4(half3(rgb) * half(mask), half(a * mask))
  return scope.build(result, helpers = listOf(foilHashHelper))
}
