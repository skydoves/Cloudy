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
 * Shader text building blocks for the open [Modifier.shaderEffect] recipe mechanism.
 *
 * A recipe author writes only a `half4 main(float2 xy)` body; the binding prepends a fixed
 * **preamble** (standard uniform declarations + shared helpers/consts) so every recipe sees the same
 * lens framing as the built-in liquid-glass effect. The constants here are the two preamble layers
 * (uniforms / helpers) and the bundled example recipe bodies (specular, chromatic).
 *
 * AGSL (Android RuntimeShader, API 33+) and SKSL (Skia RuntimeEffect) are byte-identical here, just
 * as [LiquidGlassShaderSource] keeps them in parity — the two languages share the same surface for
 * this feature set. Keeping them as separate consts (rather than one shared string) mirrors the
 * existing source layout and leaves room for a future divergence without an API change.
 */

// ---------------------------------------------------------------------------------------------
// Preamble — owned entirely by the library and prepended to every recipe body.
//
// Layer 1 (uniforms): the standard inputs the binding writes each draw — iResolution, lensCenter,
//   lensSize, cornerRadius, iLight, iTime, and the `content` shader input. A recipe must NOT
//   redeclare any of these (a duplicate uniform is a compile error).
// Layer 2 (helpers): the geometry/color helpers a recipe may call (boxRoundedSDF,
//   lensNormalDirection, toBrightness, processColor) plus the shared edge consts. Cloned from
//   LiquidGlassShaderSource.kt:64-98 (LiquidGlass* is left 0-modified); the originals stay the
//   source of truth, this is the recipe-facing copy.
//
// The preamble declares NO `main` — the recipe owns the single entry point.
// ---------------------------------------------------------------------------------------------

/** AGSL preamble layer 1: the standard uniform declarations the binding writes each draw. */
internal const val PREAMBLE_UNIFORMS_AGSL: String = """
uniform float2 iResolution;
uniform float2 lensCenter;
uniform float2 lensSize;
uniform float cornerRadius;
uniform float2 iLight;
uniform float iTime;
uniform shader content;
"""

/** AGSL preamble layer 2: helper functions + shared consts a recipe body may call. */
internal const val PREAMBLE_HELPERS_AGSL: String = """
const float SMOOTH_EDGE_PX = 1.5;
const float SEAM_BLEND_PX = 8.0;   // 대각 블렌드 반폭(px); 클수록 내부 크리스 부드러움

// Signed distance to a box with rounded corners
// Negative = inside, Positive = outside, Zero = on boundary
float boxRoundedSDF(float2 p, float2 halfDim, float r) {
    float2 d = abs(p) - halfDim + float2(r);
    float exterior = length(max(d, 0.0));
    float interior = min(max(d.x, d.y), 0.0);
    return exterior + interior - r;
}

// Outward-facing direction vector from the lens surface
float2 lensNormalDirection(float2 p, float2 halfDim, float r) {
    float2 d = abs(p) - halfDim + float2(r);
    float2 s = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);

    if (max(d.x, d.y) > 0.0) {
        return s * normalize(max(d, 0.0));
    }
    return d.x > d.y ? float2(s.x, 0.0) : float2(0.0, s.y);
}

// Perceptual brightness (ITU-R BT.709 standard)
float toBrightness(half3 c) {
    return dot(c, half3(0.2126, 0.7152, 0.0722));
}

// Color processing: vibrancy, intensity adjustment, and color overlay
half3 processColor(half3 src, float vibrancy, float intensity, float4 overlay) {
    float mono = toBrightness(src);
    half3 vibrant = half3(clamp(mix(half3(mono), src, vibrancy), 0.0, 1.0));
    half3 adjusted = half3(clamp((vibrant - 0.5) * intensity + 0.5, 0.0, 1.0));
    return mix(adjusted, half3(overlay.rgb), overlay.a);
}
"""

/** Full AGSL preamble (uniforms + helpers) prepended to every recipe's AGSL body. */
internal const val PREAMBLE_AGSL: String = PREAMBLE_UNIFORMS_AGSL + PREAMBLE_HELPERS_AGSL

/** SKSL preamble layer 1 — byte-identical to [PREAMBLE_UNIFORMS_AGSL] (AGSL/SKSL parity). */
internal const val PREAMBLE_UNIFORMS_SKSL: String = """
uniform float2 iResolution;
uniform float2 lensCenter;
uniform float2 lensSize;
uniform float cornerRadius;
uniform float2 iLight;
uniform float iTime;
uniform shader content;
"""

/** SKSL preamble layer 2 — byte-identical to [PREAMBLE_HELPERS_AGSL] (AGSL/SKSL parity). */
internal const val PREAMBLE_HELPERS_SKSL: String = """
const float SMOOTH_EDGE_PX = 1.5;
const float SEAM_BLEND_PX = 8.0;   // 대각 블렌드 반폭(px); 클수록 내부 크리스 부드러움

// Signed distance to a box with rounded corners
// Negative = inside, Positive = outside, Zero = on boundary
float boxRoundedSDF(float2 p, float2 halfDim, float r) {
    float2 d = abs(p) - halfDim + float2(r);
    float exterior = length(max(d, 0.0));
    float interior = min(max(d.x, d.y), 0.0);
    return exterior + interior - r;
}

// Outward-facing direction vector from the lens surface
float2 lensNormalDirection(float2 p, float2 halfDim, float r) {
    float2 d = abs(p) - halfDim + float2(r);
    float2 s = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);

    if (max(d.x, d.y) > 0.0) {
        return s * normalize(max(d, 0.0));
    }
    return d.x > d.y ? float2(s.x, 0.0) : float2(0.0, s.y);
}

// Perceptual brightness (ITU-R BT.709 standard)
float toBrightness(half3 c) {
    return dot(c, half3(0.2126, 0.7152, 0.0722));
}

// Color processing: vibrancy, intensity adjustment, and color overlay
half3 processColor(half3 src, float vibrancy, float intensity, float4 overlay) {
    float mono = toBrightness(src);
    half3 vibrant = half3(clamp(mix(half3(mono), src, vibrancy), 0.0, 1.0));
    half3 adjusted = half3(clamp((vibrant - 0.5) * intensity + 0.5, 0.0, 1.0));
    return mix(adjusted, half3(overlay.rgb), overlay.a);
}
"""

/** Full SKSL preamble (uniforms + helpers) prepended to every recipe's SKSL body. */
internal const val PREAMBLE_SKSL: String = PREAMBLE_UNIFORMS_SKSL + PREAMBLE_HELPERS_SKSL

// ---------------------------------------------------------------------------------------------
// SPECULAR recipe — the bevel-lit glint of the liquid-glass shader, repackaged as a self-contained
// recipe main(). It re-computes its own lens setup (halfDim/r/p/sdf/normal/pixel — the values the
// built-in main() carries through local scope) so the body stands alone on top of the preamble.
//
// Renames vs LiquidGlassShaderSource.kt: lightDir -> iLight (the preamble's standard light uniform).
// `resolution` is renamed iResolution in the preamble but the specular block never reads it, so the
// body never names it. The 11 spec* uniforms are NOT standard, so they are self-declared here and
// fed by ShaderRecipes.Specular's bindUniforms.
//
// The specular block below (the `// --- ...` sections from seam-free direction down to the screen
// blend) is a verbatim copy of LiquidGlassShaderSource.kt:164-234 with the single lightDir->iLight
// rename; the formula/constants/operation order are byte-for-byte the original (bit-exact gate).
// Color/refraction setup uses inline default literals (refraction 0.25, curve 0.25, dispersion 0,
// saturation 1, contrast 1, tint transparent) instead of uniforms, since a pure-specular recipe has
// no reason to vary them — keeping the binding's bindUniforms to exactly the 11 spec* values.
// fp16 note: pow/sin/broad ramps are kept `float` (the originals' fp16-banding guards are preserved).
// ---------------------------------------------------------------------------------------------

/** AGSL body for [ShaderRecipes.Specular] — a complete `half4 main` on top of [PREAMBLE_AGSL]. */
internal const val SPECULAR_AGSL: String = """
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

half4 main(float2 xy) {
    float2 halfDim = lensSize * 0.5;
    float r = min(cornerRadius, min(halfDim.x, halfDim.y));

    float2 p = xy - lensCenter;
    float sdf = boxRoundedSDF(p, halfDim, r);

    // Skip pixels outside the lens boundary
    if (sdf > SMOOTH_EDGE_PX) {
        return content.eval(xy);
    }

    float2 normal = lensNormalDirection(p, halfDim, r);

    // Refracted sample position — default refraction/curve (0.25/0.25) inlined; a pure specular
    // recipe does not vary them, so they are literals rather than uniforms.
    float2 sampleXY = xy;
    {
        float minDim = min(halfDim.x, halfDim.y);
        float depth = clamp(-sdf / (minDim * 0.25), 0.0, 1.0);
        float curvature = 1.0 - depth;
        float bend = 1.0 - sqrt(1.0 - curvature * curvature);
        sampleXY = xy - bend * 0.25 * minDim * normal;
    }

    // No dispersion (default 0) — single tap.
    half4 pixel = content.eval(sampleXY);

    // Handle fully transparent samples
    if (pixel.a <= 0.0) {
        pixel = content.eval(xy);
    }

    // processColor with saturation 1 / contrast 1 / transparent tint = identity, kept for parity.
    pixel.rgb = processColor(pixel.rgb, 1.0, 1.0, float4(0.0, 0.0, 0.0, 0.0));

    // edge inlined to its default (0.2) so the original `edge > 0.0` gate stays satisfied.
    float edge = 0.2;

    // Specular highlight — a moving focal hotspot + a tight Blinn rim glint.
    // 4 terms: focal pool (light pours across the face, dual-axis) + body sheen modeling fill
    //          + tight Blinn rim glint + back-rim fill.
    // 게이트가 specStrength도 검사 → NoGlow(specStrength==0)는 ALU 0 + bit-exact off (F).
    // specStrength는 두 binding 모두 항상 set 되므로 게이트가 필요 uniform write를 막지 않음.
    // specRimMix:   0 = 순수 body(focal pool), 1 = 순수 rim glint (crossfade)
    // specPower:    rim/back lobe 샤프니스 (Blinn)
    // specWidthPx:  rim band 두께, `edge`와 분리
    // specStrength: peak highlight (screen-blended; <= 1.0)
    // specLightZ/specDomeFrac/specBodyPower/specBodyGain: fake-3D bevel 라이팅
    // specFocalK/specPoolFrac/specPoolGain: 빛이 면을 가로질러 흐르는 이동 핫스팟(양축)
    if (edge > 0.0 && specStrength > 0.0) {
        float2 lightVec = normalize(iLight);

        // --- seam-free in-plane direction (스페큘러 전용; 굴절이 읽는 'normal'은 불변) ---
        // 굴절 경로(상단)는 hard-pick 'normal'을 그대로 사용한다(회귀 없음, G1).
        // 여기서만 d.x==d.y 대각 크리스를 없앤 연속 방향을 따로 만든다.
        // 둥근 사각형 내부는 L-inf 필드라 진짜 gradient는 대각에서 불연속 → 8px softmax로 블렌딩.
        float2 d2 = abs(p) - halfDim + float2(r);                 // boxRoundedSDF와 동일 기저
        float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float2 specDir2;
        if (max(d2.x, d2.y) > 0.0) {
            specDir2 = s2 * normalize(max(d2, 0.0));              // 외부/코너: 해석적
        } else {
            // 내부: w->1 x-우세, ->0 y-우세, =0.5 대각 seam. +1e-4 = dead-center normalize 특이점 가드.
            float w  = clamp(0.5 + 0.5 * (d2.x - d2.y) / SEAM_BLEND_PX, 0.0, 1.0);
            float2 v = float2(s2.x * w, s2.y * (1.0 - w)) + float2(0.0, 1.0e-4);
            specDir2 = normalize(v);
        }

        // --- fake-3D surface normal from the rounded-rect bevel ---
        float minHalf = min(halfDim.x, halfDim.y);
        float bevelPx = max(minHalf * specDomeFrac, 1.0);
        float depthIn = max(-sdf, 0.0);
        float t       = clamp(depthIn / bevelPx, 0.0, 1.0);
        float n_cos   = 1.0 - t;                                  // 면내 크기
        float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));      // float: n_cos~1 근처 치명적 상쇄
        float3 N      = normalize(float3(specDir2 * n_cos, n_sin + 1.0e-3));

        float3 L = normalize(float3(lightVec, specLightZ));
        float3 V = float3(0.0, 0.0, 1.0);

        // --- MOVING FOCAL HOTSPOT — "light pours across the face on both axes" ---
        // 핫스팟을 lightVec 방향으로 minHalf*specFocalK 만큼 옮긴다: pitch(lightVec.y)=수직,
        // roll(lightVec.x)=수평 이동 → 이동하는 밝은 풀(둘 다 축). N≈+Z로 붕괴하던 옛 body sheen과
        // 달리 면 전체에서 빛 방향을 강하게 따른다. inside 마스크로 렌즈 밖/림은 페이드.
        float2 focal     = lightVec * (minHalf * specFocalK);     // lensCenter(=원점) 기준 오프셋
        float  poolR     = max(minHalf * specPoolFrac, 1.0);      // 0 가드(zero-width smoothstep 회피)
        float  poolD     = length(p - focal);
        float  pool      = 1.0 - smoothstep(0.0, poolR, poolD);   // 1 at focal, 0 at rim (edge0<edge1)
        float  inside    = 1.0 - smoothstep(-6.0, 0.0, sdf);      // 렌즈 안쪽만(밖/경계 페이드; edge0<edge1)
        float  focalPool = pool * pool * specStrength * specPoolGain * inside; // pool^2 = 더 단단한 코어

        // --- broad body sheen (완만한 modeling fill, 핫스팟 위에 더함) ---
        float ndl       = max(dot(N, L), 0.0);
        float bodySheen = pow(ndl, specBodyPower) * specStrength * specBodyGain; // float: pow는 fp16서 밴딩

        // --- tight Blinn rim glint, rim band에 한정 ---
        // specWidthPx==0이면 zero-width smoothstep = 구현정의 하드스텝 → max(...,1.0) 가드 (G6).
        float3 H       = normalize(L + V);
        float  rimBand = smoothstep(-max(specWidthPx, 1.0), 0.0, sdf);
        float  glint   = pow(max(dot(N, H), 0.0), specPower) * specStrength;     // float: pow bands in fp16
        float  rim     = glint * rimBand;

        // --- back-rim fill (반대편 광원, rim-locked, 1/4 가중) ---
        float3 Lb   = normalize(float3(-lightVec, specLightZ));
        float  back  = pow(max(dot(N, Lb), 0.0), specPower) * specStrength * rimBand * 0.25; // float: pow

        // --- ordered dither: 넓은 body 램프의 8-bit Mach 밴드 깨기 ---
        // lens-local 좌표를 fract로 bound 후 sin → 대형 렌즈서 sin 인자 폭주/줄무늬 붕괴 방지 (G5).
        // specStrength를 곱해 off일 때 정확히 0 (F).
        float2 hp = fract((p / minHalf) * 0.5 + 0.5);             // bounded ~[0,1)
        float  dn = fract(sin(dot(hp, float2(12.9898, 78.233))) * 43758.5453) - 0.5;

        // --- body(이동 핫스팟 + sheen + dither) <-> rim 선형 crossfade (monotonic, pure endpoint) ---
        // rimMix=0 -> 순수 body(focal pool); rimMix=1 -> 순수 rim glint(과거 룩).
        float body      = focalPool + bodySheen + dn * (1.0 / 255.0) * specStrength;
        float rimMix    = clamp(specRimMix, 0.0, 1.0);
        float highlight = body * (1.0 - rimMix) + (rim + back) * rimMix;

        // Screen blend: 밝은 배경에서도 생존, [0,1]로 clamp → 1.0 초과 불가(스크린 블렌드 불변식 보장).
        pixel.rgb += half3((1.0 - pixel.rgb) * clamp(highlight, 0.0, 1.0));
    }

    // Anti-aliased edge transition
    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""

/** SKSL body for [ShaderRecipes.Specular] — byte-identical to [SPECULAR_AGSL] (AGSL/SKSL parity). */
internal const val SPECULAR_SKSL: String = """
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

half4 main(float2 xy) {
    float2 halfDim = lensSize * 0.5;
    float r = min(cornerRadius, min(halfDim.x, halfDim.y));

    float2 p = xy - lensCenter;
    float sdf = boxRoundedSDF(p, halfDim, r);

    // Skip pixels outside the lens boundary
    if (sdf > SMOOTH_EDGE_PX) {
        return content.eval(xy);
    }

    float2 normal = lensNormalDirection(p, halfDim, r);

    // Refracted sample position — default refraction/curve (0.25/0.25) inlined; a pure specular
    // recipe does not vary them, so they are literals rather than uniforms.
    float2 sampleXY = xy;
    {
        float minDim = min(halfDim.x, halfDim.y);
        float depth = clamp(-sdf / (minDim * 0.25), 0.0, 1.0);
        float curvature = 1.0 - depth;
        float bend = 1.0 - sqrt(1.0 - curvature * curvature);
        sampleXY = xy - bend * 0.25 * minDim * normal;
    }

    // No dispersion (default 0) — single tap.
    half4 pixel = content.eval(sampleXY);

    // Handle fully transparent samples
    if (pixel.a <= 0.0) {
        pixel = content.eval(xy);
    }

    // processColor with saturation 1 / contrast 1 / transparent tint = identity, kept for parity.
    pixel.rgb = processColor(pixel.rgb, 1.0, 1.0, float4(0.0, 0.0, 0.0, 0.0));

    // edge inlined to its default (0.2) so the original `edge > 0.0` gate stays satisfied.
    float edge = 0.2;

    // Specular highlight — a moving focal hotspot + a tight Blinn rim glint.
    // 4 terms: focal pool (light pours across the face, dual-axis) + body sheen modeling fill
    //          + tight Blinn rim glint + back-rim fill.
    // 게이트가 specStrength도 검사 → NoGlow(specStrength==0)는 ALU 0 + bit-exact off (F).
    // specStrength는 두 binding 모두 항상 set 되므로 게이트가 필요 uniform write를 막지 않음.
    // specRimMix:   0 = 순수 body(focal pool), 1 = 순수 rim glint (crossfade)
    // specPower:    rim/back lobe 샤프니스 (Blinn)
    // specWidthPx:  rim band 두께, `edge`와 분리
    // specStrength: peak highlight (screen-blended; <= 1.0)
    // specLightZ/specDomeFrac/specBodyPower/specBodyGain: fake-3D bevel 라이팅
    // specFocalK/specPoolFrac/specPoolGain: 빛이 면을 가로질러 흐르는 이동 핫스팟(양축)
    if (edge > 0.0 && specStrength > 0.0) {
        float2 lightVec = normalize(iLight);

        // --- seam-free in-plane direction (스페큘러 전용; 굴절이 읽는 'normal'은 불변) ---
        // 굴절 경로(상단)는 hard-pick 'normal'을 그대로 사용한다(회귀 없음, G1).
        // 여기서만 d.x==d.y 대각 크리스를 없앤 연속 방향을 따로 만든다.
        // 둥근 사각형 내부는 L-inf 필드라 진짜 gradient는 대각에서 불연속 → 8px softmax로 블렌딩.
        float2 d2 = abs(p) - halfDim + float2(r);                 // boxRoundedSDF와 동일 기저
        float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float2 specDir2;
        if (max(d2.x, d2.y) > 0.0) {
            specDir2 = s2 * normalize(max(d2, 0.0));              // 외부/코너: 해석적
        } else {
            // 내부: w->1 x-우세, ->0 y-우세, =0.5 대각 seam. +1e-4 = dead-center normalize 특이점 가드.
            float w  = clamp(0.5 + 0.5 * (d2.x - d2.y) / SEAM_BLEND_PX, 0.0, 1.0);
            float2 v = float2(s2.x * w, s2.y * (1.0 - w)) + float2(0.0, 1.0e-4);
            specDir2 = normalize(v);
        }

        // --- fake-3D surface normal from the rounded-rect bevel ---
        float minHalf = min(halfDim.x, halfDim.y);
        float bevelPx = max(minHalf * specDomeFrac, 1.0);
        float depthIn = max(-sdf, 0.0);
        float t       = clamp(depthIn / bevelPx, 0.0, 1.0);
        float n_cos   = 1.0 - t;                                  // 면내 크기
        float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));      // float: n_cos~1 근처 치명적 상쇄
        float3 N      = normalize(float3(specDir2 * n_cos, n_sin + 1.0e-3));

        float3 L = normalize(float3(lightVec, specLightZ));
        float3 V = float3(0.0, 0.0, 1.0);

        // --- MOVING FOCAL HOTSPOT — "light pours across the face on both axes" ---
        // 핫스팟을 lightVec 방향으로 minHalf*specFocalK 만큼 옮긴다: pitch(lightVec.y)=수직,
        // roll(lightVec.x)=수평 이동 → 이동하는 밝은 풀(둘 다 축). N≈+Z로 붕괴하던 옛 body sheen과
        // 달리 면 전체에서 빛 방향을 강하게 따른다. inside 마스크로 렌즈 밖/림은 페이드.
        float2 focal     = lightVec * (minHalf * specFocalK);     // lensCenter(=원점) 기준 오프셋
        float  poolR     = max(minHalf * specPoolFrac, 1.0);      // 0 가드(zero-width smoothstep 회피)
        float  poolD     = length(p - focal);
        float  pool      = 1.0 - smoothstep(0.0, poolR, poolD);   // 1 at focal, 0 at rim (edge0<edge1)
        float  inside    = 1.0 - smoothstep(-6.0, 0.0, sdf);      // 렌즈 안쪽만(밖/경계 페이드; edge0<edge1)
        float  focalPool = pool * pool * specStrength * specPoolGain * inside; // pool^2 = 더 단단한 코어

        // --- broad body sheen (완만한 modeling fill, 핫스팟 위에 더함) ---
        float ndl       = max(dot(N, L), 0.0);
        float bodySheen = pow(ndl, specBodyPower) * specStrength * specBodyGain; // float: pow는 fp16서 밴딩

        // --- tight Blinn rim glint, rim band에 한정 ---
        // specWidthPx==0이면 zero-width smoothstep = 구현정의 하드스텝 → max(...,1.0) 가드 (G6).
        float3 H       = normalize(L + V);
        float  rimBand = smoothstep(-max(specWidthPx, 1.0), 0.0, sdf);
        float  glint   = pow(max(dot(N, H), 0.0), specPower) * specStrength;     // float: pow bands in fp16
        float  rim     = glint * rimBand;

        // --- back-rim fill (반대편 광원, rim-locked, 1/4 가중) ---
        float3 Lb   = normalize(float3(-lightVec, specLightZ));
        float  back  = pow(max(dot(N, Lb), 0.0), specPower) * specStrength * rimBand * 0.25; // float: pow

        // --- ordered dither: 넓은 body 램프의 8-bit Mach 밴드 깨기 ---
        // lens-local 좌표를 fract로 bound 후 sin → 대형 렌즈서 sin 인자 폭주/줄무늬 붕괴 방지 (G5).
        // specStrength를 곱해 off일 때 정확히 0 (F).
        float2 hp = fract((p / minHalf) * 0.5 + 0.5);             // bounded ~[0,1)
        float  dn = fract(sin(dot(hp, float2(12.9898, 78.233))) * 43758.5453) - 0.5;

        // --- body(이동 핫스팟 + sheen + dither) <-> rim 선형 crossfade (monotonic, pure endpoint) ---
        // rimMix=0 -> 순수 body(focal pool); rimMix=1 -> 순수 rim glint(과거 룩).
        float body      = focalPool + bodySheen + dn * (1.0 / 255.0) * specStrength;
        float rimMix    = clamp(specRimMix, 0.0, 1.0);
        float highlight = body * (1.0 - rimMix) + (rim + back) * rimMix;

        // Screen blend: 밝은 배경에서도 생존, [0,1]로 clamp → 1.0 초과 불가(스크린 블렌드 불변식 보장).
        pixel.rgb += half3((1.0 - pixel.rgb) * clamp(highlight, 0.0, 1.0));
    }

    // Anti-aliased edge transition
    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""

// ---------------------------------------------------------------------------------------------
// CHROMATIC recipe — a new thin-film (Newton's-rings) iridescence body authored purely as a recipe,
// proving the open mechanism can express a fresh effect with NO core/preamble changes. It reads the
// content (ContentFilter) and uses the lens SDF/bevel for geometry, but is otherwise independent of
// the specular body. Single "Iridescent" look (named multi-presets are a follow-up).
//
// Physics (from the chromatic-overlay design): optical path difference OPD = thickness / cos, with
// `cos = dot(bevelNormal, L)` (the bevel gives a free thickness modulation), then per-channel
// 0.5 + 0.5*cos(2π·opd·kRGB) with kRGB the inverse-wavelength ratios, a metal floor so it never goes
// fully fluorescent, and an exp() saturation falloff for a pastel wash. Blend is alpha-branched:
// transparent base (a≈0) lays the iridescence down and raises alpha; opaque base screens it over the
// content (multiply would darken). hue/cos chain is `float` (AGSL `half` is fp16 → rainbow banding).
// Self-declares chromaticIntensity / chromaticGain (its own uniforms, fed by bindUniforms).
// ---------------------------------------------------------------------------------------------

/** AGSL body for [ShaderRecipes.Chromatic] — a complete `half4 main` on top of [PREAMBLE_AGSL]. */
internal const val CHROMATIC_AGSL: String = """
uniform float chromaticIntensity;   // overall blend amount (0 = off)
uniform float chromaticGain;        // OPD scale — higher = denser, finer interference rings

// Six-segment HSV ramp endpoint helper (float chain: fp16 banding on a saturated rainbow).
half4 main(float2 xy) {
    float2 halfDim = lensSize * 0.5;
    float r = min(cornerRadius, min(halfDim.x, halfDim.y));

    float2 p = xy - lensCenter;
    float sdf = boxRoundedSDF(p, halfDim, r);

    if (sdf > SMOOTH_EDGE_PX) {
        return content.eval(xy);
    }

    half4 pixel = content.eval(xy);

    // --- bevel normal (same fake-3D construction the specular body uses) for a thickness term ---
    float2 d2 = abs(p) - halfDim + float2(r);
    float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
    float2 cDir;
    if (max(d2.x, d2.y) > 0.0) {
        cDir = s2 * normalize(max(d2, 0.0));
    } else {
        float w = clamp(0.5 + 0.5 * (d2.x - d2.y) / SEAM_BLEND_PX, 0.0, 1.0);
        cDir = normalize(float2(s2.x * w, s2.y * (1.0 - w)) + float2(0.0, 1.0e-4));
    }
    float minHalf = min(halfDim.x, halfDim.y);
    float depthIn = max(-sdf, 0.0);
    float t       = clamp(depthIn / max(minHalf, 1.0), 0.0, 1.0);
    float n_cos   = 1.0 - t;
    float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));         // float: catastrophic cancel near 1
    float3 cN     = normalize(float3(cDir * n_cos, n_sin + 1.0e-3));
    float3 cL     = normalize(float3(iLight, 0.55));

    // --- thin-film Newton's rings: OPD = thickness / cos, per-channel cosine interference ---
    // cos = dot(cN, cL): the bevel modulates film thickness for free, so the rings curve with the
    // lens shape. float chain throughout (fp16 banding ruins a saturated interference fringe).
    float cosTheta = max(dot(cN, cL), 1.0e-3);
    float opd      = (chromaticGain * (0.5 + 0.5 * t)) / cosTheta;
    // Inverse-wavelength ratios (R<G<B) so the three channels separate into a spectral fringe.
    float ir = 0.5 + 0.5 * cos(6.2831853 * opd * 1.00);
    float ig = 0.5 + 0.5 * cos(6.2831853 * opd * 1.18);
    float ib = 0.5 + 0.5 * cos(6.2831853 * opd * 1.42);
    // exp() saturation falloff toward the center (washed pastel, not fluorescent) + metal floor.
    // float chain end-to-end; the only half conversion is the vector constructor at the assignment
    // to pixel (same pattern the built-in shader uses — `half3(floatExpr)` — so no scalar half cast).
    float wash = exp(-1.5 * t);
    float3 chromaRGB = float3(mix(0.12, ir, wash), mix(0.12, ig, wash), mix(0.12, ib, wash));

    float k = clamp(chromaticIntensity, 0.0, 1.0);
    float3 base = float3(pixel.rgb);
    // Alpha-branched blend: transparent base lays iridescence + raises alpha; opaque base screens it
    // over the content (multiply would darken a bright card). G: smoothstep edges kept edge0<edge1.
    if (pixel.a <= 0.0) {
        pixel = half4(chromaRGB * k, k);
    } else {
        float3 screen = float3(1.0) - (float3(1.0) - base) * (float3(1.0) - chromaRGB * k);
        pixel.rgb = half3(mix(base, screen, k));
    }

    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""

/** SKSL body for [ShaderRecipes.Chromatic] — byte-identical to [CHROMATIC_AGSL] (AGSL/SKSL parity). */
internal const val CHROMATIC_SKSL: String = """
uniform float chromaticIntensity;   // overall blend amount (0 = off)
uniform float chromaticGain;        // OPD scale — higher = denser, finer interference rings

// Six-segment HSV ramp endpoint helper (float chain: fp16 banding on a saturated rainbow).
half4 main(float2 xy) {
    float2 halfDim = lensSize * 0.5;
    float r = min(cornerRadius, min(halfDim.x, halfDim.y));

    float2 p = xy - lensCenter;
    float sdf = boxRoundedSDF(p, halfDim, r);

    if (sdf > SMOOTH_EDGE_PX) {
        return content.eval(xy);
    }

    half4 pixel = content.eval(xy);

    // --- bevel normal (same fake-3D construction the specular body uses) for a thickness term ---
    float2 d2 = abs(p) - halfDim + float2(r);
    float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
    float2 cDir;
    if (max(d2.x, d2.y) > 0.0) {
        cDir = s2 * normalize(max(d2, 0.0));
    } else {
        float w = clamp(0.5 + 0.5 * (d2.x - d2.y) / SEAM_BLEND_PX, 0.0, 1.0);
        cDir = normalize(float2(s2.x * w, s2.y * (1.0 - w)) + float2(0.0, 1.0e-4));
    }
    float minHalf = min(halfDim.x, halfDim.y);
    float depthIn = max(-sdf, 0.0);
    float t       = clamp(depthIn / max(minHalf, 1.0), 0.0, 1.0);
    float n_cos   = 1.0 - t;
    float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));         // float: catastrophic cancel near 1
    float3 cN     = normalize(float3(cDir * n_cos, n_sin + 1.0e-3));
    float3 cL     = normalize(float3(iLight, 0.55));

    // --- thin-film Newton's rings: OPD = thickness / cos, per-channel cosine interference ---
    // cos = dot(cN, cL): the bevel modulates film thickness for free, so the rings curve with the
    // lens shape. float chain throughout (fp16 banding ruins a saturated interference fringe).
    float cosTheta = max(dot(cN, cL), 1.0e-3);
    float opd      = (chromaticGain * (0.5 + 0.5 * t)) / cosTheta;
    // Inverse-wavelength ratios (R<G<B) so the three channels separate into a spectral fringe.
    float ir = 0.5 + 0.5 * cos(6.2831853 * opd * 1.00);
    float ig = 0.5 + 0.5 * cos(6.2831853 * opd * 1.18);
    float ib = 0.5 + 0.5 * cos(6.2831853 * opd * 1.42);
    // exp() saturation falloff toward the center (washed pastel, not fluorescent) + metal floor.
    // float chain end-to-end; the only half conversion is the vector constructor at the assignment
    // to pixel (same pattern the built-in shader uses — `half3(floatExpr)` — so no scalar half cast).
    float wash = exp(-1.5 * t);
    float3 chromaRGB = float3(mix(0.12, ir, wash), mix(0.12, ig, wash), mix(0.12, ib, wash));

    float k = clamp(chromaticIntensity, 0.0, 1.0);
    float3 base = float3(pixel.rgb);
    // Alpha-branched blend: transparent base lays iridescence + raises alpha; opaque base screens it
    // over the content (multiply would darken a bright card). G: smoothstep edges kept edge0<edge1.
    if (pixel.a <= 0.0) {
        pixel = half4(chromaRGB * k, k);
    } else {
        float3 screen = float3(1.0) - (float3(1.0) - base) * (float3(1.0) - chromaRGB * k);
        pixel.rgb = half3(mix(base, screen, k));
    }

    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""
