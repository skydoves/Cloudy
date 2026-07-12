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
 * Raster-parity gate for [MirageShaders.Chromatic] — the last of the four bundled kernels ported
 * through the eDSL, and the only one with a `float4`-typed uniform read via `.xyz` swizzle
 * ([Float4], `chromaticKRGB`). One kernel program backs every named look (`OilSlick`, `SoapBubble`,
 * `MetallicFoil`, `Pearl`) purely through uniform defaults; this test binds one look's values (the
 * `Chromatic` defaults) since the source is identical regardless of which look's defaults a draw uses.
 */
internal class MirageEdslChromaticTest :
  FunSpec({

    test("MirageShaders.Chromatic compiles and rasterizes through the real skiko RuntimeEffect") {
      meanAbsDiff(
        rasterize(buildEdslChromaticShader(), RASTER),
        rasterize(handRolledChromaticShader(), RASTER),
      ) shouldBe 0.0
    }

    test("OilSlick, SoapBubble, MetallicFoil, and Pearl share one compiled kernel program") {
      val oil = MirageProgramCache.obtain(MirageShaders.OilSlick, Dialect.Sksl).shouldNotBeNull()
      val soap = MirageProgramCache.obtain(MirageShaders.SoapBubble, Dialect.Sksl).shouldNotBeNull()
      val foil = MirageProgramCache.obtain(
        MirageShaders.MetallicFoil,
        Dialect.Sksl,
      ).shouldNotBeNull()
      val pearl = MirageProgramCache.obtain(MirageShaders.Pearl, Dialect.Sksl).shouldNotBeNull()

      oil.compiled.source shouldBe soap.compiled.source
      oil.compiled.source shouldBe foil.compiled.source
      oil.compiled.source shouldBe pearl.compiled.source
    }

    test("the eDSL-traced thin-film tint actually changes the content, not a no-op") {
      val chromatic = rasterize(buildEdslChromaticShader(), RASTER)
      val baseline = rasterize(contentShader(), RASTER)
      meanAbsDiff(chromatic, baseline).shouldBeGreaterThan(1.0)
    }
  })

private fun buildEdslChromaticShader(): Shader {
  val cached = MirageProgramCache.obtain(MirageShaders.Chromatic, Dialect.Sksl).shouldNotBeNull()
  return bindChromaticUniforms(
    RuntimeShaderBuilder(RuntimeEffect.makeForShader(cached.compiled.source)),
  )
}

/**
 * The pre-eDSL kernel text (what `CHROMATIC_KERNEL_SKSL` used to read before this port), kept here
 * only as the golden reference for the raster-parity test above — not reachable from production code.
 */
private fun handRolledChromaticShader(): Shader {
  val source = """
    const float SMOOTH_EDGE_PX = 1.5;
    const float CHROMA_OPD_BASE  = 0.10;
    const float CHROMA_THICK_MIX = 0.55;
    const float CHROMA_RIM_POW   = 3.0;
    const float CHROMA_SE_POW    = 4.0;

    float boxRoundedSDF(float2 p, float2 halfDim, float r) {
        float2 d = abs(p) - halfDim + float2(r);
        float exterior = length(max(d, 0.0));
        float interior = min(max(d.x, d.y), 0.0);
        return exterior + interior - r;
    }

    uniform float2 lensCenter;
    uniform float2 lensSize;
    uniform float cornerRadius;
    uniform float2 iLight;
    uniform float chromaticIntensity;
    uniform float chromaticGain;
    uniform float4 chromaticKRGB;
    uniform float chromaticFloor;
    uniform float chromaticWashout;
    uniform float chromaticModulate;
    uniform float chromaticRimBoost;
    uniform float chromaticPoolFrac;
    uniform shader content;

    half4 main(float2 xy) {
        float2 halfDim = lensSize * 0.5;
        float r = min(cornerRadius, min(halfDim.x, halfDim.y));

        float2 p = xy - lensCenter;
        float sdf = boxRoundedSDF(p, halfDim, r);

        if (sdf > SMOOTH_EDGE_PX) {
            return content.eval(xy);
        }

        half4 pixel = content.eval(xy);

        float minHalf = min(halfDim.x, halfDim.y);
        float2 cLightVec = normalize(iLight);
        float2 q  = abs(p) / max(halfDim, float2(1.0));
        float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float f  = pow(pow(q.x, CHROMA_SE_POW) + pow(q.y, CHROMA_SE_POW), 1.0 / CHROMA_SE_POW);
        float2 cDir = normalize(
            s2 * float2(CHROMA_SE_POW * pow(q.x, CHROMA_SE_POW - 1.0) / max(halfDim.x, 1.0),
                        CHROMA_SE_POW * pow(q.y, CHROMA_SE_POW - 1.0) / max(halfDim.y, 1.0))
            + float2(1.0e-4, 1.0e-4));
        float t       = clamp(f, 0.0, 1.0);
        float n_cos   = 1.0 - t;
        float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));
        float3 cN     = normalize(float3(cDir * n_cos, n_sin + 1.0e-3));
        float3 cL     = normalize(float3(cLightVec, 0.55));

        float cosT     = clamp(dot(cN, cL), 0.0, 1.0);
        float thick    = 1.0 - n_cos;
        float ringTerm = thick / max(1.0 - 0.6 * cosT, 1.0e-2);
        float opdDrive = mix(cosT, ringTerm, CHROMA_THICK_MIX);
        float opd      = opdDrive * chromaticGain + CHROMA_OPD_BASE;
        float3 interf  = 0.5 + 0.5 * cos(6.28318530718 * opd * chromaticKRGB.xyz);
        float3 metalRGB = chromaticFloor + (1.0 - chromaticFloor) * interf;
        float  sat      = exp(-opd * chromaticWashout);
        float3 thinFilm = mix(float3(1.0), metalRGB, clamp(sat, 0.0, 1.0));
        float  rimBoost = chromaticRimBoost * pow(clamp(thick, 0.0, 1.0), CHROMA_RIM_POW);
        float3 chromaRGB = mix(thinFilm, float3(1.0), clamp(rimBoost, 0.0, 1.0));

        float2 cFocal  = cLightVec * (minHalf * 0.55);
        float  cPoolR  = max(minHalf * chromaticPoolFrac, 1.0);
        float  cPool   = 1.0 - smoothstep(0.0, cPoolR, length(p - cFocal));
        float  poolNorm = clamp(cPool * cPool, 0.0, 1.0);
        float  chroma  = chromaticIntensity * mix(1.0, poolNorm, clamp(chromaticModulate, 0.0, 1.0));

        half  cChroma    = half(clamp(chroma, 0.0, 1.0));
        half3 cChromaRGB = half3(chromaRGB) * cChroma;
        half3 cOnWhite   = half3(chromaRGB);
        half3 cOnSrc     = half3(1.0) - (half3(1.0) - pixel.rgb) * (half3(1.0) - cChromaRGB);
        pixel.rgb = mix(cOnWhite, cOnSrc, pixel.a);
        pixel.a   = max(pixel.a, cChroma);

        float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
        half4 bg = content.eval(xy);
        return mix(bg, pixel, alpha);
    }
  """.trimIndent()
  return bindChromaticUniforms(RuntimeShaderBuilder(RuntimeEffect.makeForShader(source)))
}

/** The default Chromatic look both shaders under test are compared at (lens covers the raster). */
private fun bindChromaticUniforms(builder: RuntimeShaderBuilder): Shader {
  builder.uniform("lensCenter", RASTER / 2f, RASTER / 2f)
  builder.uniform("lensSize", RASTER.toFloat(), RASTER.toFloat())
  builder.uniform("cornerRadius", 8f)
  builder.uniform("iLight", -1f, -1f)
  builder.uniform("chromaticIntensity", 0.6f)
  builder.uniform("chromaticGain", 3.0f)
  builder.uniform("chromaticKRGB", 1f, 1.18f, 1.42f, 0f)
  builder.uniform("chromaticFloor", 0.12f)
  builder.uniform("chromaticWashout", 0.16f)
  builder.uniform("chromaticModulate", 1f)
  builder.uniform("chromaticRimBoost", 0f)
  builder.uniform("chromaticPoolFrac", 0.7f)
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
