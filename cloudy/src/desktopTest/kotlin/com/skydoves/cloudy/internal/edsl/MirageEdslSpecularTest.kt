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
 * Raster-parity gate for [MirageShaders.Specular] — the heaviest kernel ported through the eDSL: a
 * mutable local ([MutableLocal], `pixel`) reassigned once from a `content.eval` fallback and again
 * inside a non-exiting `if` block ([IfBlock]), a `&&`-gated highlight, multiple `content.eval` taps,
 * and `pow`/`sqrt`/`select` (the superellipse sign terms). Proves the eDSL-traced source rasterizes
 * byte-for-byte identically to the pre-eDSL hand-written kernel text with the highlight gate open
 * (`specStrength > 0`), so the `IfBlock` body is actually exercised, not just its guard.
 */
internal class MirageEdslSpecularTest :
  FunSpec({

    test("MirageShaders.Specular compiles and rasterizes through the real skiko RuntimeEffect") {
      meanAbsDiff(
        rasterize(buildEdslSpecularShader(), RASTER),
        rasterize(handRolledSpecularShader(), RASTER),
      ) shouldBe 0.0
    }

    test("the eDSL-traced highlight actually brightens pixels, not a no-op") {
      val withHighlight = rasterize(buildEdslSpecularShader(), RASTER)
      val withoutHighlight = rasterize(buildEdslSpecularShader(specStrength = 0f), RASTER)
      meanAbsDiff(withHighlight, withoutHighlight).shouldBeGreaterThan(0.5)
    }
  })

private fun buildEdslSpecularShader(specStrength: Float = 0.7f): Shader {
  val cached = MirageProgramCache.obtain(MirageShaders.Specular, Dialect.Sksl).shouldNotBeNull()
  return bindSpecularUniforms(
    RuntimeShaderBuilder(RuntimeEffect.makeForShader(cached.compiled.source)),
    specStrength,
  )
}

/**
 * The pre-eDSL kernel text (what `SPECULAR_KERNEL_SKSL` used to read before this port), kept here only
 * as the golden reference for the raster-parity test above — not reachable from production code.
 */
private fun handRolledSpecularShader(): Shader {
  val source = """
    const float SMOOTH_EDGE_PX = 1.5;
    const float SPEC_SE_POW = 4.0;

    float boxRoundedSDF(float2 p, float2 halfDim, float r) {
        float2 d = abs(p) - halfDim + float2(r);
        float exterior = length(max(d, 0.0));
        float interior = min(max(d.x, d.y), 0.0);
        return exterior + interior - r;
    }

    float2 lensNormalDirection(float2 p, float2 halfDim, float r) {
        float2 d = abs(p) - halfDim + float2(r);
        float2 s = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        if (max(d.x, d.y) > 0.0) {
            return s * normalize(max(d, 0.0));
        }
        return d.x > d.y ? float2(s.x, 0.0) : float2(0.0, s.y);
    }

    float toBrightness(half3 c) {
        return dot(c, half3(0.2126, 0.7152, 0.0722));
    }

    half3 processColor(half3 src, float vibrancy, float intensity, float4 overlay) {
        float mono = toBrightness(src);
        half3 vibrant = half3(clamp(mix(half3(mono), src, vibrancy), 0.0, 1.0));
        half3 adjusted = half3(clamp((vibrant - 0.5) * intensity + 0.5, 0.0, 1.0));
        return mix(adjusted, half3(overlay.rgb), overlay.a);
    }

    uniform float2 lensCenter;
    uniform float2 lensSize;
    uniform float cornerRadius;
    uniform float2 iLight;
    uniform float specStrength;
    uniform float specPower;
    uniform float specRimMix;
    uniform float specWidthPx;
    uniform float specLightZ;
    uniform float specDomeFrac;
    uniform float specBodyPower;
    uniform float specBodyGain;
    uniform float specFocalK;
    uniform float specPoolFrac;
    uniform float specPoolGain;
    uniform shader content;

    half4 main(float2 xy) {
        float2 halfDim = lensSize * 0.5;
        float r = min(cornerRadius, min(halfDim.x, halfDim.y));

        float2 p = xy - lensCenter;
        float sdf = boxRoundedSDF(p, halfDim, r);

        if (sdf > SMOOTH_EDGE_PX) {
            return content.eval(xy);
        }

        float2 normal = lensNormalDirection(p, halfDim, r);

        float2 sampleXY = xy;
        {
            float minDim = min(halfDim.x, halfDim.y);
            float depth = clamp(-sdf / (minDim * 0.25), 0.0, 1.0);
            float curvature = 1.0 - depth;
            float bend = 1.0 - sqrt(1.0 - curvature * curvature);
            sampleXY = xy - bend * 0.25 * minDim * normal;
        }

        half4 pixel = content.eval(sampleXY);

        if (pixel.a <= 0.0) {
            pixel = content.eval(xy);
        }

        pixel.rgb = processColor(pixel.rgb, 1.0, 1.0, float4(0.0, 0.0, 0.0, 0.0));

        float edge = 0.2;

        if (edge > 0.0 && specStrength > 0.0) {
            float2 lightVec = normalize(iLight);

            float minHalf = min(halfDim.x, halfDim.y);
            float2 q  = abs(p) / max(halfDim, float2(1.0));
            float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
            float seF = pow(pow(q.x, SPEC_SE_POW) + pow(q.y, SPEC_SE_POW), 1.0 / SPEC_SE_POW);
            float2 specDir2 = normalize(
                s2 * float2(SPEC_SE_POW * pow(q.x, SPEC_SE_POW - 1.0) / max(halfDim.x, 1.0),
                            SPEC_SE_POW * pow(q.y, SPEC_SE_POW - 1.0) / max(halfDim.y, 1.0))
                + float2(1.0e-4, 1.0e-4));

            float t       = clamp(seF / max(specDomeFrac, 1.0e-2), 0.0, 1.0);
            float n_cos   = 1.0 - t;
            float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));
            float3 N      = normalize(float3(specDir2 * n_cos, n_sin + 1.0e-3));

            float3 L = normalize(float3(lightVec, specLightZ));
            float3 V = float3(0.0, 0.0, 1.0);

            float2 focal     = lightVec * (minHalf * specFocalK);
            float  poolR     = max(minHalf * specPoolFrac, 1.0);
            float  poolD     = length(p - focal);
            float  pool      = 1.0 - smoothstep(0.0, poolR, poolD);
            float  inside    = 1.0 - smoothstep(-6.0, 0.0, sdf);
            float  focalPool = pool * pool * specStrength * specPoolGain * inside;

            float ndl       = max(dot(N, L), 0.0);
            float bodySheen = pow(ndl, specBodyPower) * specStrength * specBodyGain;

            float3 H       = normalize(L + V);
            float  rimBand = smoothstep(-max(specWidthPx, 1.0), 0.0, sdf);
            float  glint   = pow(max(dot(N, H), 0.0), specPower) * specStrength;
            float  rim     = glint * rimBand;

            float3 Lb   = normalize(float3(-lightVec, specLightZ));
            float  back  = pow(max(dot(N, Lb), 0.0), specPower) * specStrength * rimBand * 0.25;

            float2 hp = fract((p / minHalf) * 0.5 + 0.5);
            float  dn = fract(sin(dot(hp, float2(12.9898, 78.233))) * 43758.5453) - 0.5;

            float body      = focalPool + bodySheen + dn * (1.0 / 255.0) * specStrength;
            float rimMix    = clamp(specRimMix, 0.0, 1.0);
            float highlight = body * (1.0 - rimMix) + (rim + back) * rimMix;

            pixel.rgb += half3((1.0 - pixel.rgb) * clamp(highlight, 0.0, 1.0));
        }

        float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
        half4 bg = content.eval(xy);
        return mix(bg, pixel, alpha);
    }
  """.trimIndent()
  return bindSpecularUniforms(RuntimeShaderBuilder(RuntimeEffect.makeForShader(source)), 0.7f)
}

/** The one non-default Specular look both shaders under test are compared at (lens covers the raster). */
private fun bindSpecularUniforms(builder: RuntimeShaderBuilder, specStrength: Float): Shader {
  builder.uniform("lensCenter", RASTER / 2f, RASTER / 2f)
  builder.uniform("lensSize", RASTER.toFloat(), RASTER.toFloat())
  builder.uniform("cornerRadius", 8f)
  builder.uniform("iLight", -1f, -1f)
  builder.uniform("specStrength", specStrength)
  builder.uniform("specPower", 10f)
  builder.uniform("specRimMix", 0.4f)
  builder.uniform("specWidthPx", 12f)
  builder.uniform("specLightZ", 0.55f)
  builder.uniform("specDomeFrac", 1.15f)
  builder.uniform("specBodyPower", 2.5f)
  builder.uniform("specBodyGain", 0.6f)
  builder.uniform("specFocalK", 0.55f)
  builder.uniform("specPoolFrac", 0.7f)
  builder.uniform("specPoolGain", 1.3f)
  builder.child("content", contentShader())
  return builder.makeShader()
}

private fun contentShader(): Shader = RuntimeEffect.makeForShader(
  """
  half4 main(float2 xy) {
    float2 uv = xy / float2($RASTER.0, $RASTER.0);
    return half4(half(uv.x), half(uv.y), half(1.0 - uv.x), 1.0);
  }
  """.trimIndent(),
).let { RuntimeShaderBuilder(it).makeShader() }
