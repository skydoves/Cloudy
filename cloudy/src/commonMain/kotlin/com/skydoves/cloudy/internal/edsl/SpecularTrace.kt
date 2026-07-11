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
 * Traces the Specular body: a refracted single-tap sample, then a moving-hotspot + Blinn-rim highlight
 * screen-blended on top, masked to the lens with an anti-aliased edge. Ports
 * [com.skydoves.cloudy.internal.SPECULAR_KERNEL_AGSL] statement-for-statement (same intermediate names
 * where the source names them) so the two are diffable line-by-line; the raster-parity test is what
 * proves the port, not the resemblance.
 */
internal fun traceSpecular(
  lensCenter: UOffset,
  lensSize: USize,
  cornerRadius: UFloat,
  iLight: UOffset,
  specStrength: UFloat,
  specPower: UFloat,
  specRimMix: UFloat,
  specWidthPx: UFloat,
  specLightZ: UFloat,
  specDomeFrac: UFloat,
  specBodyPower: UFloat,
  specBodyGain: UFloat,
  specFocalK: UFloat,
  specPoolFrac: UFloat,
  specPoolGain: UFloat,
): ShaderModule {
  val scope = MirageShaderScope()
  val xy = Float2(Argument("xy", ShaderType.Float2))
  val smoothEdgePx = float1(1.5f) // SMOOTH_EDGE_PX
  val specSePow = float1(4f) // SPEC_SE_POW

  val halfDim = scope.let("halfDim", lensSize.lift() * float1(0.5f))
  val r = scope.let("r", min(cornerRadius.lift(), min(halfDim.x, halfDim.y)))
  val p = scope.let("p", xy - lensCenter.lift())
  val sdf = scope.let("sdf", Float1(Call("boxRoundedSDF", listOf(p.e, halfDim.e, r.e), ShaderType.Float1)))

  scope.guard(sdf gt smoothEdgePx, sampleContent(xy))

  val normal = scope.let(
    "normal",
    Float2(Call("lensNormalDirection", listOf(p.e, halfDim.e, r.e), ShaderType.Float2)),
  )

  // The `{ ... }` scratch block computing sampleXY: its locals (minDim/depth/curvature/bend) are
  // scoped to that block in the source but never read outside it, so tracing them as ordinary `let`
  // locals in the outer scope is observationally identical — nothing after this reads their names.
  val minDim = scope.let("minDim", min(halfDim.x, halfDim.y))
  val depth = scope.let("depth", clamp(-sdf / (minDim * float1(0.25f)), float1(0f), float1(1f)))
  val curvature = scope.let("curvature", float1(1f) - depth)
  val bend = scope.let("bend", float1(1f) - sqrt(float1(1f) - curvature * curvature))
  val sampleXY = scope.let("sampleXY", xy - normal * (bend * float1(0.25f) * minDim))

  val firstTap = scope.letMutable("pixel", sampleContent(sampleXY))
  scope.ifBlock(firstTap.get().a le float1(0f)) {
    firstTap.set(sampleContent(xy))
  }
  run {
    val pixel = firstTap.get()
    firstTap.set(half4(processColor(pixel.rgb, float1(1f), float1(1f), float4(0f, 0f, 0f, 0f)), pixel.a))
  }

  val edge = float1(0.2f)

  scope.ifBlock((edge gt float1(0f)) and (specStrength.lift() gt float1(0f))) {
    val lightVec = let("lightVec", normalize(iLight.lift()))
    val minHalf = let("minHalf", min(halfDim.x, halfDim.y))
    val q = let("q", abs(p) / float2(max(halfDim.x, float1(1f)), max(halfDim.y, float1(1f))))
    val s2 = let("s2", float2(signSelect(p.x), signSelect(p.y)))
    val seF = let(
      "seF",
      pow(pow(q.x, specSePow) + pow(q.y, specSePow), float1(1f) / specSePow),
    )
    val specDir2 = let(
      "specDir2",
      normalize(
        s2 * float2(
          specSePow * pow(q.x, specSePow - float1(1f)) / max(halfDim.x, float1(1f)),
          specSePow * pow(q.y, specSePow - float1(1f)) / max(halfDim.y, float1(1f)),
        ) + float2(1.0e-4f, 1.0e-4f),
      ),
    )

    val t = let("t", clamp(seF / max(specDomeFrac.lift(), float1(1.0e-2f)), float1(0f), float1(1f)))
    val nCos = let("n_cos", float1(1f) - t)
    val nSin = let("n_sin", sqrt(max(float1(1f) - nCos * nCos, float1(0f))))
    val nn = let("N", normalize(float3(specDir2 * nCos, nSin + float1(1.0e-3f))))

    val ll = let("L", normalize(float3(lightVec, specLightZ.lift())))
    val vv = let("V", float3(0f, 0f, 1f))

    val focal = let("focal", lightVec * (minHalf * specFocalK.lift()))
    val poolR = let("poolR", max(minHalf * specPoolFrac.lift(), float1(1f)))
    val poolD = let("poolD", length(p - focal))
    val pool = let("pool", float1(1f) - smoothstep(float1(0f), poolR, poolD))
    val inside = let("inside", float1(1f) - smoothstep(float1(-6f), float1(0f), sdf))
    val focalPool = let("focalPool", pool * pool * specStrength.lift() * specPoolGain.lift() * inside)

    val ndl = let("ndl", max(dot(nn, ll), float1(0f)))
    val bodySheen = let("bodySheen", pow(ndl, specBodyPower.lift()) * specStrength.lift() * specBodyGain.lift())

    val hh = let("H", normalize(ll + vv))
    val rimBand = let("rimBand", smoothstep(-max(specWidthPx.lift(), float1(1f)), float1(0f), sdf))
    val glint = let("glint", pow(max(dot(nn, hh), float1(0f)), specPower.lift()) * specStrength.lift())
    val rim = let("rim", glint * rimBand)

    val lb = let("Lb", normalize(float3(-lightVec, specLightZ.lift())))
    val back = let(
      "back",
      pow(max(dot(nn, lb), float1(0f)), specPower.lift()) * specStrength.lift() * rimBand * float1(0.25f),
    )

    val hp = let("hp", fract((p / minHalf) * float1(0.5f) + float1(0.5f)))
    val dn = let(
      "dn",
      fract(sin(dot(hp, float2(12.9898f, 78.233f))) * float1(43758.5453f)) - float1(0.5f),
    )

    val body = let("body", focalPool + bodySheen + dn * (float1(1f) / float1(255f)) * specStrength.lift())
    val rimMix = let("rimMix", clamp(specRimMix.lift(), float1(0f), float1(1f)))
    val highlight = let(
      "highlight",
      body * (float1(1f) - rimMix) + (rim + back) * rimMix,
    )

    val pixel = firstTap.get()
    firstTap.set(
      half4(
        pixel.rgb + (half3(1f) - pixel.rgb) * clamp(highlight, float1(0f), float1(1f)),
        pixel.a,
      ),
    )
  }

  val alpha = scope.let("alpha", float1(1f) - smoothstep(-smoothEdgePx * float1(0.5f), smoothEdgePx * float1(0.5f), sdf))
  val bg = scope.let("bg", sampleContent(xy))
  val result = mix(bg, firstTap.get(), alpha)
  return scope.build(result)
}
