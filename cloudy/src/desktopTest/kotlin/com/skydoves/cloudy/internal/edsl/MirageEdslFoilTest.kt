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
import com.skydoves.cloudy.MirageShaders
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.MirageProgramCache
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

private const val RASTER = 64

/**
 * Raster-parity gate for [MirageShaders.Foil] — the first eDSL-ported kernel with control flow (an
 * early-return guard, [EarlyReturn]), a Generate/`main(float2 xy)` signature, and a user-defined helper
 * function ([HelperFunction], `foilHash`). Proves the eDSL-traced source rasterizes byte-for-byte
 * identically to the pre-eDSL hand-written kernel text at a fixed, non-zero `mirageTime` (so the
 * time-driven shimmer terms are exercised, not just the `t=0` path).
 */
internal class MirageEdslFoilTest :
  FunSpec({

    test("MirageShaders.Foil compiles and rasterizes through the real skiko RuntimeEffect") {
      meanAbsDiff(
        rasterize(buildEdslFoilShader(), RASTER),
        rasterize(handRolledFoilShader(), RASTER),
      ) shouldBe 0.0
    }

    test("the eDSL-traced early-return guard actually masks pixels outside the lens") {
      // A raster larger than the lens must have transparent corners (the sdf > SMOOTH_EDGE_PX guard).
      val pixels = rasterize(buildEdslFoilShader(), RASTER)
      val cornerAlpha = pixels[3].toInt() and 0xFF // top-left pixel, ARGB_8888 alpha byte
      cornerAlpha shouldBe 0

      val centerIndex = ((RASTER / 2) * RASTER + RASTER / 2) * 4
      val centerAlpha = pixels[centerIndex + 3].toInt() and 0xFF
      centerAlpha.toDouble().shouldBeGreaterThan(0.0)
    }
  })

/** Binds [MirageShaders.Foil]'s eDSL-traced, program-cache-compiled source to a live shader. */
private fun buildEdslFoilShader(): Shader {
  val cached = MirageProgramCache.obtain(MirageShaders.Foil, Dialect.Sksl).shouldNotBeNull()
  return bindFoilUniforms(RuntimeShaderBuilder(RuntimeEffect.makeForShader(cached.compiled.source)))
}

/**
 * The pre-eDSL kernel text (what `FOIL_KERNEL_SKSL` used to read before this port), kept here only as
 * the golden reference for the raster-parity test above — not reachable from production code.
 */
private fun handRolledFoilShader(): Shader {
  val source = """
    const float SMOOTH_EDGE_PX = 1.5;

    float boxRoundedSDF(float2 p, float2 halfDim, float r) {
        float2 d = abs(p) - halfDim + float2(r);
        float exterior = length(max(d, 0.0));
        float interior = min(max(d.x, d.y), 0.0);
        return exterior + interior - r;
    }

    uniform float mirageTime;
    uniform float2 lensCenter;
    uniform float2 lensSize;
    uniform float cornerRadius;
    uniform float2 iLight;
    uniform float foilBands;
    uniform float foilPhase;
    uniform float chromaticGain;
    uniform float sparkleDensity;
    uniform float sparkleAmplitude;

    float foilHash(float2 c) {
        return fract(sin(dot(c, float2(127.1, 311.7))) * 43758.5453);
    }

    half4 main(float2 xy) {
        float2 halfDim = lensSize * 0.5;
        float r = min(cornerRadius, min(halfDim.x, halfDim.y));

        float2 p = xy - lensCenter;
        float sdf = boxRoundedSDF(p, halfDim, r);

        if (sdf > SMOOTH_EDGE_PX) {
            return half4(0.0);
        }

        float minHalf = min(halfDim.x, halfDim.y);
        float2 cLightVec = normalize(iLight);
        float2 pNorm = p / minHalf;
        float t = clamp(max(-sdf, 0.0) / max(minHalf, 1.0), 0.0, 1.0);

        float along = dot(pNorm, cLightVec);
        float glare = smoothstep(0.2, 1.0, along) * (1.0 - t);
        float dome  = (1.0 - smoothstep(0.0, 1.0, length(pNorm))) * 0.5;

        float hueF = fract(along * foilBands + foilPhase + 0.05 * mirageTime);
        float3 hsv = clamp(
            abs(fract(float3(hueF) + float3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0,
            0.0, 1.0);
        float opd = (0.5 + 0.5 * t) * chromaticGain;
        float3 film = 0.5 + 0.5 * cos(6.28318530718 * opd * float3(1.0, 1.18, 1.42));
        float3 rainbow = mix(hsv, film, 0.4);

        float2 cell = floor(pNorm * sparkleDensity);
        float  h    = foilHash(cell);
        float2 cellUv = fract(pNorm * sparkleDensity) - 0.5;
        float  d    = length(cellUv);
        float  aa   = clamp(sparkleDensity / max(minHalf, 1.0), 0.02, 0.25);
        float  dot0 = 1.0 - smoothstep(0.18 - aa, 0.18 + aa, d);
        float  twinkle = 0.5 + 0.5 * sin(6.2831853 * (h + 0.3 * mirageTime));
        float  spark = step(0.78, h) * dot0 * twinkle * sparkleAmplitude;

        float lum = clamp(glare + dome, 0.0, 1.0);
        float3 rgb = rainbow * lum + float3(spark);
        float  a   = clamp(lum + spark, 0.0, 1.0);
        float  mask = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
        return half4(half3(rgb) * half(mask), half(a * mask));
    }
  """.trimIndent()
  return bindFoilUniforms(RuntimeShaderBuilder(RuntimeEffect.makeForShader(source)))
}

/** The one non-default Foil look both shaders under test are compared at (lens covers the raster). */
private fun bindFoilUniforms(builder: RuntimeShaderBuilder): Shader {
  builder.uniform("mirageTime", 1.75f)
  builder.uniform("lensCenter", RASTER / 2f, RASTER / 2f)
  builder.uniform("lensSize", RASTER.toFloat(), RASTER.toFloat())
  builder.uniform("cornerRadius", 8f)
  builder.uniform("iLight", -1f, -1f)
  builder.uniform("foilBands", 5f)
  builder.uniform("foilPhase", 0f)
  builder.uniform("chromaticGain", 3.6f)
  builder.uniform("sparkleDensity", 16f)
  builder.uniform("sparkleAmplitude", 0.3f)
  return builder.makeShader()
}
