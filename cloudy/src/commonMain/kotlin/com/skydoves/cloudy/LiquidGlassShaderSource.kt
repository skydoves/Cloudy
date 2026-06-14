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
 * Cloudy's Liquid Glass shader implementation for creating realistic glass lens distortion effects.
 *
 * This GPU shader provides an interactive liquid glass visualization featuring:
 * - Smooth-cornered rectangular lens geometry with configurable radius
 * - Physically-inspired light bending through curved glass surfaces
 * - RGB wavelength separation for prismatic color fringing
 * - Real-time color manipulation (vibrancy, intensity, overlay)
 * - Specular rim highlights with directional illumination
 * - Sub-pixel edge smoothing for crisp boundaries
 *
 * Pair with [Modifier.cloudy] for combined blur + glass aesthetics.
 *
 * Supports AGSL (Android 13+) and SKSL (Skia-based platforms).
 */
public object LiquidGlassShaderSource {

  /**
   * AGSL shader for Android RuntimeShader (API 33+).
   */
  public const val AGSL: String = """
uniform float2 resolution;
uniform float2 lensCenter;
uniform float2 lensSize;
uniform float cornerRadius;
uniform float refraction;
uniform float curve;
uniform float dispersion;
uniform float saturation;
uniform float contrast;
uniform float4 tint;
uniform float edge;
uniform float2 lightDir;
uniform float specStrength;
uniform float specPower;
uniform float specRimMix;     // 변경: was specSweep — body<->rim crossfade (D)
uniform float specWidthPx;
uniform float specLightZ;      // NEW
uniform float specDomeFrac;    // NEW
uniform float specBodyPower;   // NEW
uniform float specBodyGain;    // NEW
uniform float specFocalK;      // NEW: focal-pool offset toward light (fraction of minHalf)
uniform float specPoolFrac;    // NEW: focal-pool radius (fraction of minHalf)
uniform float specPoolGain;    // NEW: focal-pool peak scale
uniform float chromaticIntensity;  // NEW: 0 = off (bit-exact, ALU 0). 0..1 iridescent overlay strength
uniform float chromaticMode;       // NEW: 0 = Iridescent (thin-film), 1 = Foil (flowing bands); float enum
uniform float chromaticBands;      // NEW: Foil rainbow band count along the light direction (e.g. 3)
uniform float chromaticCycles;     // NEW: Iridescent hue cycles across the light/normal angle (e.g. 1.5)
uniform float chromaticPhase;      // NEW: static hue phase offset (radians-free, fract domain; e.g. 0)
uniform float chromaticModulate;   // NEW: 0..1, modulate rainbow strength by the focal pool (e.g. 1)
uniform shader content;

const float SMOOTH_EDGE_PX = 1.5;
const float SEAM_BLEND_PX = 8.0;   // 대각 블렌드 반폭(px); 클수록 내부 크리스 부드러움

// --- Thin-film interference (Iridescent / Holographic) constants ---
// OPD 모델: opd ≈ (thickness / cos(refr)) → Newton's rings. thickness=bevel 깊이 cT(중심0→림1),
// cosT=cos(입사각, 빛 반응). 림으로 갈수록 막이 두꺼워 밴드가 촘촘(고전 박막), 빛이 돌면 위상 쓸림.
const float CHROMA_OPD_GAIN  = 3.0;   // 면 위 Newton 밴드 수(높을수록 다밴드; 너무 크면 aliasing/busy)
const float CHROMA_OPD_BASE  = 0.10;  // 기저 막 차수(중심 저OPD를 밝은 은백 0차에 두고 밖으로 색이 쌓임)
// thickness↔light 가중: 1=순수 두께링, 0=순수 빛각도. 두 효과 블렌드(둘 다 holographic에 기여).
const float CHROMA_THICK_MIX = 0.55;
// per-channel 파수비(파장 역수 ~650/560/470nm). 벌릴수록 금→자홍→청록 Newton 분리가 또렷.
const float3 CHROMA_KRGB     = float3(1.0, 1.18, 1.42);
// 금속 floor: 채널이 0까지 안 떨어지게(형광 무지개 아님). 낮게 둬 채도 살리되 0은 아님.
const float CHROMA_METAL_FLOOR = 0.12;
// 고차 coherence wash-out 율. 클수록 빨리 은백(파스텔↑·busy↓). 외곽 고차 링을 silver로 진정.
const float CHROMA_WASHOUT     = 0.16;

// --- Named holographic looks (chromaticMode 2..5) ---
// mode 0 (Iridescent) = 위 CHROMA_* 베이스 그대로(bit-exact 무회귀), mode 1 (Foil) = 선형 띠(불변).
// 신규 4종은 같은 thin-film 경로를 타되 아래 per-look 세트(밴드밀도·채널분리·콘트라스트·파스텔율·
// 빛추종)를 branchless step-mask로 고른다. 셰이더 안 mode 분기 → 새 uniform 0개(설계 B).
// 각 세트는 실기기(S25) 실측으로 4종이 확연히 구분되게 벌렸다.
// 필드: OPD_GAIN(밴드 수), KRGB(금↔청록 분리), METAL_FLOOR(콘트라스트; 낮을수록↑), WASHOUT(파스텔율;
//       높을수록 은백), MOD(focal-pool 추종 = "빛 반사처럼 깔리는" 세기; 셰이더 내부 효과 modulate).
// OilSlick: 촘촘 다밴드 + 넓은 분리 + 저washout(고채도) + 강한 빛추종 → 기름막.
const float CHROMA_OIL_GAIN       = 5.5;
const float3 CHROMA_OIL_KRGB      = float3(1.0, 1.30, 1.72);
const float CHROMA_OIL_FLOOR      = 0.05;
const float CHROMA_OIL_WASHOUT    = 0.07;
const float CHROMA_OIL_MOD        = 0.75;
// SoapBubble: 넓은 띠(저밀도) + 높은 washout(파스텔·은백) + 약한 빛추종(면 균일) → 비눗방울.
const float CHROMA_SOAP_GAIN      = 1.7;
const float3 CHROMA_SOAP_KRGB     = float3(1.0, 1.11, 1.26);
const float CHROMA_SOAP_FLOOR     = 0.22;
const float CHROMA_SOAP_WASHOUT   = 0.50;
const float CHROMA_SOAP_MOD       = 0.22;
// MetallicFoil: 저washout(채도유지) + 저floor(콘트라스트↑) + 강한 빛추종 → 강한 고채도 금속박.
const float CHROMA_FOILM_GAIN     = 3.6;
const float3 CHROMA_FOILM_KRGB    = float3(1.0, 1.26, 1.62);
const float CHROMA_FOILM_FLOOR    = 0.03;
const float CHROMA_FOILM_WASHOUT  = 0.05;
const float CHROMA_FOILM_MOD      = 0.82;
// Pearl: 높은 washout(파스텔) + 높은 floor(저콘트라스트 은백) + 좁은 분리 → 은은한 진주.
const float CHROMA_PEARL_GAIN     = 2.4;
const float3 CHROMA_PEARL_KRGB    = float3(1.0, 1.07, 1.18);
const float CHROMA_PEARL_FLOOR    = 0.46;
const float CHROMA_PEARL_WASHOUT  = 0.58;
const float CHROMA_PEARL_MOD      = 0.20;
// Fresnel rim 부스트 — 가장자리(cT→1)서 무지개를 더 밝게(유리 림에 빛 걸림).
// MetallicFoil/Pearl에만 가산(아래 mFoilM/mPearl 마스크). 저비용 1항.
const float CHROMA_RIM_POW        = 3.0;
const float CHROMA_RIM_GAIN       = 0.45;

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

    // Compute refracted sample position
    float2 sampleXY = xy;
    if (refraction > 0.0 && curve > 0.0) {
        float minDim = min(halfDim.x, halfDim.y);
        float depth = clamp(-sdf / (minDim * refraction), 0.0, 1.0);
        float curvature = 1.0 - depth;
        float bend = 1.0 - sqrt(1.0 - curvature * curvature);
        sampleXY = xy - bend * curve * minDim * normal;
    }

    // RGB channel separation for prismatic effect
    half4 pixel;
    if (dispersion > 0.0) {
        float2 normP = p / halfDim;
        float2 shift = dispersion * normP * normP * normP * min(halfDim.x, halfDim.y) * 0.1;

        float2 xyR = sampleXY - shift;
        float2 xyG = sampleXY;
        float2 xyB = sampleXY + shift;

        float sdfR = boxRoundedSDF(xyR - lensCenter, halfDim, r);
        float sdfB = boxRoundedSDF(xyB - lensCenter, halfDim, r);

        half4 gVal = content.eval(xyG);
        half4 rVal = (sdfR <= 0.0) ? content.eval(xyR) : gVal;
        half4 bVal = (sdfB <= 0.0) ? content.eval(xyB) : gVal;

        pixel = half4(rVal.r, gVal.g, bVal.b, gVal.a);
    } else {
        pixel = content.eval(sampleXY);
    }

    // Handle fully transparent samples
    if (pixel.a <= 0.0) {
        pixel = content.eval(xy);
    }

    pixel.rgb = processColor(pixel.rgb, saturation, contrast, tint);

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
        float2 lightVec = normalize(lightDir);

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

    // Chromatic overlay — light-reactive iridescent sheen, independent of the white specular pool.
    // 흰색 specular(screen-blend) 위에 별도로 얹는 무지갯빛 항. specular와 분리된 자체 게이트라
    // chromaticIntensity==0이면 pixel.rgb 수정 0 → 기존 룩과 bit-exact, ALU 0 (uniform 분기).
    // specular 게이트(edge/specStrength)와 독립 → specular off에서도 무지개만 켤 수 있다.
    // 두 모드: 0 = Iridescent(박막 간섭, 빛/노멀 각도로 hue), 1 = Foil(빛 방향 투영, lightVec 움직이면 띠가 흐른다).
    // hue·HSV→RGB 산술 체인은 전부 float/float3 — AGSL half(fp16)는 채도 만점 무지개에 밴딩이 심함.
    // 블렌드는 tint-multiply(흰 카드도 chromaRGB로 채색); screen은 흰 배경(pixel≈1)에서 무지개가 사라짐.
    if (chromaticIntensity > 0.0) {
        // 공유 스칼라 재계산(specular 게이트 밖 → 독립). lightDir/halfDim/sdf/p는 main 스코프에서 그대로.
        float2 cLightVec = normalize(lightDir);                       // 빛 방향(2D), specular의 lightVec와 동일식
        float  cMinHalf  = min(halfDim.x, halfDim.y);                 // p 정규화 기준(specular minHalf와 동일)
        float2 pNorm     = p / cMinHalf;                              // lens-local 정규화 좌표(~[-1,1]); px면 fract 앨리어싱

        // bevel 노멀/광원 3D 재구성(Iridescent용 dot(N,L)). specular :183-192와 동일 수식, chroma-local 이름.
        float2 cD2 = abs(p) - halfDim + float2(r);                    // boxRoundedSDF와 동일 기저
        float2 cS2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float2 cSpecDir;
        if (max(cD2.x, cD2.y) > 0.0) {
            cSpecDir = cS2 * normalize(max(cD2, 0.0));                // 외부/코너: 해석적
        } else {
            float cw  = clamp(0.5 + 0.5 * (cD2.x - cD2.y) / SEAM_BLEND_PX, 0.0, 1.0);
            float2 cv = float2(cS2.x * cw, cS2.y * (1.0 - cw)) + float2(0.0, 1.0e-4);
            cSpecDir  = normalize(cv);                                // 내부: seam-free softmax
        }
        float  cBevelPx = max(cMinHalf * specDomeFrac, 1.0);
        float  cT       = clamp(max(-sdf, 0.0) / cBevelPx, 0.0, 1.0);
        float  cNcos    = 1.0 - cT;
        float  cNsin    = sqrt(max(1.0 - cNcos * cNcos, 0.0));        // n_cos~1 상쇄 가드(float)
        float3 cN       = normalize(float3(cSpecDir * cNcos, cNsin + 1.0e-3));
        float3 cL       = normalize(float3(cLightVec, specLightZ));

        // hue 합성 — 두 모드(전 체인 float).
        //   Foil(mode 1, 불변): 표면을 빛에 투영한 선형 무지개 띠 → 빛 움직이면 띠가 흐른다.
        //   Iridescent(mode 0, thin-film): 박막 간섭색. dot(cN,cL)=cos(입사각)이 광학 경로차(OPD)를
        //     만들고, R/G/B 파장이 달라(파수 kRGB) 채널마다 다른 위상으로 보강/상쇄 → Newton 시퀀스
        //     (은백→금→자홍→청록)가 산술이 아니라 물리에서 자연 발생. cN(bevel 노멀)이 면 위에서
        //     변해 "두께 변조"를, cL이 빛 따라 돌아 위상 쓸림(gyro 반응)을 공짜로 준다.
        // Foil: 기존 선형 hue → HSV 램프.
        float hueF = fract(dot(pNorm, cLightVec) * chromaticBands + chromaticPhase);
        float3 foilRGB = clamp(
            abs(fract(float3(hueF) + float3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0,
            0.0, 1.0);
        // --- per-look 파라미터 선택 (chromaticMode → thin-film 세트), branchless step-mask ---
        // 마스크는 정확히 0.0/1.0 (step 결과의 곱) → mode 0은 m0=1·나머지=0 → 모든 param이 베이스
        // const와 IEEE bit 동일(x*1+0*…=x). ∴ Iridescent(mode 0) 무회귀 보장. mode 1(Foil)은 아래
        // isFoil 경로로 분리돼 thinFilm 자체가 버려지므로 param 무관(불변).
        float cMode  = chromaticMode;
        float m0     = 1.0 - step(0.5, cMode);                       // mode 0 (Iridescent, 베이스)
        float mOil   = step(1.5, cMode) * (1.0 - step(2.5, cMode));  // mode 2
        float mSoap  = step(2.5, cMode) * (1.0 - step(3.5, cMode));  // mode 3
        float mFoilM = step(3.5, cMode) * (1.0 - step(4.5, cMode));  // mode 4
        float mPearl = step(4.5, cMode);                             // mode 5 (이상)
        // 각 룩 세트를 마스크 가중합으로 고른다(분기 발산 0). mode 0/1에서 베이스 const 비트 보존.
        float  lookGain    = CHROMA_OPD_GAIN * m0 + CHROMA_OIL_GAIN * mOil + CHROMA_SOAP_GAIN * mSoap
                           + CHROMA_FOILM_GAIN * mFoilM + CHROMA_PEARL_GAIN * mPearl;
        float3 lookKRGB    = CHROMA_KRGB * m0 + CHROMA_OIL_KRGB * mOil + CHROMA_SOAP_KRGB * mSoap
                           + CHROMA_FOILM_KRGB * mFoilM + CHROMA_PEARL_KRGB * mPearl;
        float  lookFloor   = CHROMA_METAL_FLOOR * m0 + CHROMA_OIL_FLOOR * mOil + CHROMA_SOAP_FLOOR * mSoap
                           + CHROMA_FOILM_FLOOR * mFoilM + CHROMA_PEARL_FLOOR * mPearl;
        float  lookWashout = CHROMA_WASHOUT * m0 + CHROMA_OIL_WASHOUT * mOil + CHROMA_SOAP_WASHOUT * mSoap
                           + CHROMA_FOILM_WASHOUT * mFoilM + CHROMA_PEARL_WASHOUT * mPearl;

        // Iridescent: 박막 간섭(Newton's rings). 광학 경로차 opd = thickness/cos(refr).
        //   thickness = bevel 깊이 cT(중심0→림1; cNcos=1-cT) → 림으로 갈수록 막이 두꺼워 밴드 촘촘.
        //   cosT = cos(입사각) = dot(cN,cL) → 빛이 돌면 위상 쓸림(gyro 반응). 1e-2 가드(grazing 발산).
        // CHROMA_THICK_MIX로 두께링(공간)↔빛각도(반응)를 블렌드, GAIN이 면 위 Newton 밴드 수를 정한다.
        float cosT     = clamp(dot(cN, cL), 0.0, 1.0);
        float thick    = 1.0 - cNcos;                                // = cT, bevel 깊이(중심0→림1)
        float ringTerm = thick / max(1.0 - 0.6 * cosT, 1.0e-2);      // 두께/cos: Newton 링(빛 의존)
        float opdDrive = mix(cosT, ringTerm, CHROMA_THICK_MIX);      // 빛각도↔두께링 블렌드
        float opd      = opdDrive * (chromaticCycles * lookGain) + CHROMA_OPD_BASE + chromaticPhase;
        // per-channel 보강간섭(파장 역수비 kRGB). 0.5+0.5cos = [0,1] 중심 0.5(은백 기준 진동).
        float3 interf = 0.5 + 0.5 * cos(6.28318530718 * opd * lookKRGB);
        // 금속 floor: 채널이 0까지 안 떨어지게(형광 무지개=채널 하나 늘 0 → sticker). 진동폭만 살린다.
        float3 metalRGB = lookFloor + (1.0 - lookFloor) * interf;
        // 고차 coherence wash-out: OPD 클수록 파스텔→clean silver(흰 1.0 기준; metalRGB luma로 하면
        // 저채도부가 베이지로 탁해진다). 약하게 둬 금속 채도 유지. sat=간섭색 보존율.
        float  sat       = exp(-opd * lookWashout);
        float3 thinFilm  = mix(float3(1.0), metalRGB, clamp(sat, 0.0, 1.0)); // 은백(흰) ↔ 간섭색
        // Fresnel rim 부스트: 가장자리(cT→1)서 thinFilm을 흰쪽으로 더 밝게(유리 림 광택).
        //   MetallicFoil/Pearl만(mFoilM+mPearl 마스크) → Iridescent/Foil/Oil/Soap 불변.
        //   cT=thick(중심0→림1). pow로 림 집중. 0..1 안에서 mix → 클램프 불필요.
        float  rimSel    = mFoilM + mPearl;                          // 0/1 (해당 모드만)
        float  rimBoost  = rimSel * CHROMA_RIM_GAIN * pow(clamp(thick, 0.0, 1.0), CHROMA_RIM_POW);
        thinFilm         = mix(thinFilm, float3(1.0), clamp(rimBoost, 0.0, 1.0)); // 림으로 갈수록 백광
        // mode 1만 Foil(선형 띠)로 분기, 나머지(0,2,3,4,5)는 thin-film. step-window = 정확히 0/1 →
        //   mode 1: mix(thinFilm, foilRGB, 1.0)=foilRGB(불변). mode 0: mix(...,0.0)=thinFilm(불변).
        float  isFoil    = step(0.5, cMode) * (1.0 - step(1.5, cMode));
        float3 chromaRGB = mix(thinFilm, foilRGB, isFoil);           // 1=Foil, 그외=thin-film 변형

        // focal-pool 모듈레이션 — specular의 raw pool²(정규화본; specStrength/Gain 곱 전)으로 무지개 세기 변조.
        // specular 게이트 밖이라 pool을 저렴하게 재계산(length+smoothstep 몇 줄): focal/poolR 식은 specular와 동일.
        float2 cFocal  = cLightVec * (cMinHalf * specFocalK);        // 빛 방향 오프셋(lensCenter=원점 기준)
        float  cPoolR  = max(cMinHalf * specPoolFrac, 1.0);          // zero-width smoothstep 가드
        float  cPool   = 1.0 - smoothstep(0.0, cPoolR, length(p - cFocal)); // 1 at focal, 0 at rim (edge0<edge1)
        float  poolNorm = clamp(cPool * cPool, 0.0, 1.0);           // raw pool²(정규화) = modulator
        // "빛 반사처럼 깔리는" 강화 — 무지개 세기를 focal-pool(빛 핫스팟)로 변조해 빛 따라 진해진다.
        //   effMod = focal-pool 추종 세기. mode 0(Iridescent)/1(Foil)은 binding 값(chromaticModulate,
        //   현재 0.3) 그대로 → bit-exact 무회귀(마스크 0/1, x*1+0*…=x). 신규 4종만 per-look(0.2~0.82)로
        //   끌어올려 카드 위 빛 반사 지각을 강화. 새 식/uniform 0개(기존 cPool 재사용).
        float  baseMod = m0 + isFoil;                               // mode 0 또는 1 → 1.0, 그외 0.0
        float  effMod  = chromaticModulate * baseMod
                       + CHROMA_OIL_MOD * mOil + CHROMA_SOAP_MOD * mSoap
                       + CHROMA_FOILM_MOD * mFoilM + CHROMA_PEARL_MOD * mPearl;
        float  chroma  = chromaticIntensity * mix(1.0, poolNorm, clamp(effMod, 0.0, 1.0));

        // 두 경로가 정반대 블렌드를 원한다 → content alpha로 보간:
        //   투명 베이스(a→0, 흰 카드): cOnWhite = chromaRGB. 흰(1,1,1)*무지개 = 순수 무지개. screen이면
        //     흰 배경에서 1-(1-1)*(…)=1 로 무지개가 사라지므로 multiply(=흰*chromaRGB)가 맞다. 절대 불변.
        //   불투명 베이스(a→1, 사진): cOnSrc = SCREEN(=어두워지지 않는 glow). multiply는 항상 값을 낮춰
        //     (chromaRGB는 채널 하나가 항상 0) 어두운/컬러 픽셀을 탁하게 만든다(인물부 색조오염, 측정 -26%).
        //     screen = pixel + (1-pixel)*chromaRGB*chroma 와 등가 → 항상 pixel 이상(밝아지는 방향),
        //     1 self-limit(과포화/클립 없음), 흰 픽셀(=1)은 거의 불변. 어두운 인물부에도 무지개가 더해 보인다.
        // 불투명 white(=1)는 screen이 1을 유지(흰 위에 glow를 더 못 얹음) — 물리적으로 옳고, 흰 '카드'는
        // 투명 경로(a≈0)를 타므로 회귀 없음. chromaticIntensity==0이면 블록 미실행 → bit-exact off 보존.
        half  cChroma  = half(clamp(chroma, 0.0, 1.0));
        half3 cChromaRGB = half3(chromaRGB) * cChroma;            // chroma 가중 무지개(공유 항)
        half3 cOnWhite = half3(chromaRGB);                        // 흰 베이스(1,1,1) * chromaRGB = chromaRGB(불변)
        half3 cOnSrc   = half3(1.0) - (half3(1.0) - pixel.rgb) * (half3(1.0) - cChromaRGB); // SCREEN glow
        pixel.rgb = mix(cOnWhite, cOnSrc, pixel.a);              // a=0→흰 위 무지개, a=1→screen glow
        pixel.a   = max(pixel.a, cChroma);                       // 투명 배경에서도 오버레이가 보이도록
    }

    // Anti-aliased edge transition
    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""

  /**
   * SKSL shader for Skia RuntimeEffect (iOS, macOS, Desktop, WASM).
   */
  public const val SKSL: String = """
uniform float2 resolution;
uniform float2 lensCenter;
uniform float2 lensSize;
uniform float cornerRadius;
uniform float refraction;
uniform float curve;
uniform float dispersion;
uniform float saturation;
uniform float contrast;
uniform float4 tint;
uniform float edge;
uniform float2 lightDir;
uniform float specStrength;
uniform float specPower;
uniform float specRimMix;     // 변경: was specSweep — body<->rim crossfade (D)
uniform float specWidthPx;
uniform float specLightZ;      // NEW
uniform float specDomeFrac;    // NEW
uniform float specBodyPower;   // NEW
uniform float specBodyGain;    // NEW
uniform float specFocalK;      // NEW: focal-pool offset toward light (fraction of minHalf)
uniform float specPoolFrac;    // NEW: focal-pool radius (fraction of minHalf)
uniform float specPoolGain;    // NEW: focal-pool peak scale
uniform float chromaticIntensity;  // NEW: 0 = off (bit-exact, ALU 0). 0..1 iridescent overlay strength
uniform float chromaticMode;       // NEW: 0 = Iridescent (thin-film), 1 = Foil (flowing bands); float enum
uniform float chromaticBands;      // NEW: Foil rainbow band count along the light direction (e.g. 3)
uniform float chromaticCycles;     // NEW: Iridescent hue cycles across the light/normal angle (e.g. 1.5)
uniform float chromaticPhase;      // NEW: static hue phase offset (radians-free, fract domain; e.g. 0)
uniform float chromaticModulate;   // NEW: 0..1, modulate rainbow strength by the focal pool (e.g. 1)
uniform shader content;

const float SMOOTH_EDGE_PX = 1.5;
const float SEAM_BLEND_PX = 8.0;   // 대각 블렌드 반폭(px); 클수록 내부 크리스 부드러움

// --- Thin-film interference (Iridescent / Holographic) constants ---
// OPD 모델: opd ≈ (thickness / cos(refr)) → Newton's rings. thickness=bevel 깊이 cT(중심0→림1),
// cosT=cos(입사각, 빛 반응). 림으로 갈수록 막이 두꺼워 밴드가 촘촘(고전 박막), 빛이 돌면 위상 쓸림.
const float CHROMA_OPD_GAIN  = 3.0;   // 면 위 Newton 밴드 수(높을수록 다밴드; 너무 크면 aliasing/busy)
const float CHROMA_OPD_BASE  = 0.10;  // 기저 막 차수(중심 저OPD를 밝은 은백 0차에 두고 밖으로 색이 쌓임)
// thickness↔light 가중: 1=순수 두께링, 0=순수 빛각도. 두 효과 블렌드(둘 다 holographic에 기여).
const float CHROMA_THICK_MIX = 0.55;
// per-channel 파수비(파장 역수 ~650/560/470nm). 벌릴수록 금→자홍→청록 Newton 분리가 또렷.
const float3 CHROMA_KRGB     = float3(1.0, 1.18, 1.42);
// 금속 floor: 채널이 0까지 안 떨어지게(형광 무지개 아님). 낮게 둬 채도 살리되 0은 아님.
const float CHROMA_METAL_FLOOR = 0.12;
// 고차 coherence wash-out 율. 클수록 빨리 은백(파스텔↑·busy↓). 외곽 고차 링을 silver로 진정.
const float CHROMA_WASHOUT     = 0.16;

// --- Named holographic looks (chromaticMode 2..5) ---
// mode 0 (Iridescent) = 위 CHROMA_* 베이스 그대로(bit-exact 무회귀), mode 1 (Foil) = 선형 띠(불변).
// 신규 4종은 같은 thin-film 경로를 타되 아래 per-look 세트(밴드밀도·채널분리·콘트라스트·파스텔율·
// 빛추종)를 branchless step-mask로 고른다. 셰이더 안 mode 분기 → 새 uniform 0개(설계 B).
// 각 세트는 실기기(S25) 실측으로 4종이 확연히 구분되게 벌렸다.
// 필드: OPD_GAIN(밴드 수), KRGB(금↔청록 분리), METAL_FLOOR(콘트라스트; 낮을수록↑), WASHOUT(파스텔율;
//       높을수록 은백), MOD(focal-pool 추종 = "빛 반사처럼 깔리는" 세기; 셰이더 내부 효과 modulate).
// OilSlick: 촘촘 다밴드 + 넓은 분리 + 저washout(고채도) + 강한 빛추종 → 기름막.
const float CHROMA_OIL_GAIN       = 5.5;
const float3 CHROMA_OIL_KRGB      = float3(1.0, 1.30, 1.72);
const float CHROMA_OIL_FLOOR      = 0.05;
const float CHROMA_OIL_WASHOUT    = 0.07;
const float CHROMA_OIL_MOD        = 0.75;
// SoapBubble: 넓은 띠(저밀도) + 높은 washout(파스텔·은백) + 약한 빛추종(면 균일) → 비눗방울.
const float CHROMA_SOAP_GAIN      = 1.7;
const float3 CHROMA_SOAP_KRGB     = float3(1.0, 1.11, 1.26);
const float CHROMA_SOAP_FLOOR     = 0.22;
const float CHROMA_SOAP_WASHOUT   = 0.50;
const float CHROMA_SOAP_MOD       = 0.22;
// MetallicFoil: 저washout(채도유지) + 저floor(콘트라스트↑) + 강한 빛추종 → 강한 고채도 금속박.
const float CHROMA_FOILM_GAIN     = 3.6;
const float3 CHROMA_FOILM_KRGB    = float3(1.0, 1.26, 1.62);
const float CHROMA_FOILM_FLOOR    = 0.03;
const float CHROMA_FOILM_WASHOUT  = 0.05;
const float CHROMA_FOILM_MOD      = 0.82;
// Pearl: 높은 washout(파스텔) + 높은 floor(저콘트라스트 은백) + 좁은 분리 → 은은한 진주.
const float CHROMA_PEARL_GAIN     = 2.4;
const float3 CHROMA_PEARL_KRGB    = float3(1.0, 1.07, 1.18);
const float CHROMA_PEARL_FLOOR    = 0.46;
const float CHROMA_PEARL_WASHOUT  = 0.58;
const float CHROMA_PEARL_MOD      = 0.20;
// Fresnel rim 부스트 — 가장자리(cT→1)서 무지개를 더 밝게(유리 림에 빛 걸림).
// MetallicFoil/Pearl에만 가산(아래 mFoilM/mPearl 마스크). 저비용 1항.
const float CHROMA_RIM_POW        = 3.0;
const float CHROMA_RIM_GAIN       = 0.45;

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

    // Compute refracted sample position
    float2 sampleXY = xy;
    if (refraction > 0.0 && curve > 0.0) {
        float minDim = min(halfDim.x, halfDim.y);
        float depth = clamp(-sdf / (minDim * refraction), 0.0, 1.0);
        float curvature = 1.0 - depth;
        float bend = 1.0 - sqrt(1.0 - curvature * curvature);
        sampleXY = xy - bend * curve * minDim * normal;
    }

    // RGB channel separation for prismatic effect
    half4 pixel;
    if (dispersion > 0.0) {
        float2 normP = p / halfDim;
        float2 shift = dispersion * normP * normP * normP * min(halfDim.x, halfDim.y) * 0.1;

        float2 xyR = sampleXY - shift;
        float2 xyG = sampleXY;
        float2 xyB = sampleXY + shift;

        float sdfR = boxRoundedSDF(xyR - lensCenter, halfDim, r);
        float sdfB = boxRoundedSDF(xyB - lensCenter, halfDim, r);

        half4 gVal = content.eval(xyG);
        half4 rVal = (sdfR <= 0.0) ? content.eval(xyR) : gVal;
        half4 bVal = (sdfB <= 0.0) ? content.eval(xyB) : gVal;

        pixel = half4(rVal.r, gVal.g, bVal.b, gVal.a);
    } else {
        pixel = content.eval(sampleXY);
    }

    // Handle fully transparent samples
    if (pixel.a <= 0.0) {
        pixel = content.eval(xy);
    }

    pixel.rgb = processColor(pixel.rgb, saturation, contrast, tint);

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
        float2 lightVec = normalize(lightDir);

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

    // Chromatic overlay — light-reactive iridescent sheen, independent of the white specular pool.
    // 흰색 specular(screen-blend) 위에 별도로 얹는 무지갯빛 항. specular와 분리된 자체 게이트라
    // chromaticIntensity==0이면 pixel.rgb 수정 0 → 기존 룩과 bit-exact, ALU 0 (uniform 분기).
    // specular 게이트(edge/specStrength)와 독립 → specular off에서도 무지개만 켤 수 있다.
    // 두 모드: 0 = Iridescent(박막 간섭, 빛/노멀 각도로 hue), 1 = Foil(빛 방향 투영, lightVec 움직이면 띠가 흐른다).
    // hue·HSV→RGB 산술 체인은 전부 float/float3 — AGSL half(fp16)는 채도 만점 무지개에 밴딩이 심함.
    // 블렌드는 tint-multiply(흰 카드도 chromaRGB로 채색); screen은 흰 배경(pixel≈1)에서 무지개가 사라짐.
    if (chromaticIntensity > 0.0) {
        // 공유 스칼라 재계산(specular 게이트 밖 → 독립). lightDir/halfDim/sdf/p는 main 스코프에서 그대로.
        float2 cLightVec = normalize(lightDir);                       // 빛 방향(2D), specular의 lightVec와 동일식
        float  cMinHalf  = min(halfDim.x, halfDim.y);                 // p 정규화 기준(specular minHalf와 동일)
        float2 pNorm     = p / cMinHalf;                              // lens-local 정규화 좌표(~[-1,1]); px면 fract 앨리어싱

        // bevel 노멀/광원 3D 재구성(Iridescent용 dot(N,L)). specular :183-192와 동일 수식, chroma-local 이름.
        float2 cD2 = abs(p) - halfDim + float2(r);                    // boxRoundedSDF와 동일 기저
        float2 cS2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float2 cSpecDir;
        if (max(cD2.x, cD2.y) > 0.0) {
            cSpecDir = cS2 * normalize(max(cD2, 0.0));                // 외부/코너: 해석적
        } else {
            float cw  = clamp(0.5 + 0.5 * (cD2.x - cD2.y) / SEAM_BLEND_PX, 0.0, 1.0);
            float2 cv = float2(cS2.x * cw, cS2.y * (1.0 - cw)) + float2(0.0, 1.0e-4);
            cSpecDir  = normalize(cv);                                // 내부: seam-free softmax
        }
        float  cBevelPx = max(cMinHalf * specDomeFrac, 1.0);
        float  cT       = clamp(max(-sdf, 0.0) / cBevelPx, 0.0, 1.0);
        float  cNcos    = 1.0 - cT;
        float  cNsin    = sqrt(max(1.0 - cNcos * cNcos, 0.0));        // n_cos~1 상쇄 가드(float)
        float3 cN       = normalize(float3(cSpecDir * cNcos, cNsin + 1.0e-3));
        float3 cL       = normalize(float3(cLightVec, specLightZ));

        // hue 합성 — 두 모드(전 체인 float).
        //   Foil(mode 1, 불변): 표면을 빛에 투영한 선형 무지개 띠 → 빛 움직이면 띠가 흐른다.
        //   Iridescent(mode 0, thin-film): 박막 간섭색. dot(cN,cL)=cos(입사각)이 광학 경로차(OPD)를
        //     만들고, R/G/B 파장이 달라(파수 kRGB) 채널마다 다른 위상으로 보강/상쇄 → Newton 시퀀스
        //     (은백→금→자홍→청록)가 산술이 아니라 물리에서 자연 발생. cN(bevel 노멀)이 면 위에서
        //     변해 "두께 변조"를, cL이 빛 따라 돌아 위상 쓸림(gyro 반응)을 공짜로 준다.
        // Foil: 기존 선형 hue → HSV 램프.
        float hueF = fract(dot(pNorm, cLightVec) * chromaticBands + chromaticPhase);
        float3 foilRGB = clamp(
            abs(fract(float3(hueF) + float3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0,
            0.0, 1.0);
        // --- per-look 파라미터 선택 (chromaticMode → thin-film 세트), branchless step-mask ---
        // 마스크는 정확히 0.0/1.0 (step 결과의 곱) → mode 0은 m0=1·나머지=0 → 모든 param이 베이스
        // const와 IEEE bit 동일(x*1+0*…=x). ∴ Iridescent(mode 0) 무회귀 보장. mode 1(Foil)은 아래
        // isFoil 경로로 분리돼 thinFilm 자체가 버려지므로 param 무관(불변).
        float cMode  = chromaticMode;
        float m0     = 1.0 - step(0.5, cMode);                       // mode 0 (Iridescent, 베이스)
        float mOil   = step(1.5, cMode) * (1.0 - step(2.5, cMode));  // mode 2
        float mSoap  = step(2.5, cMode) * (1.0 - step(3.5, cMode));  // mode 3
        float mFoilM = step(3.5, cMode) * (1.0 - step(4.5, cMode));  // mode 4
        float mPearl = step(4.5, cMode);                             // mode 5 (이상)
        // 각 룩 세트를 마스크 가중합으로 고른다(분기 발산 0). mode 0/1에서 베이스 const 비트 보존.
        float  lookGain    = CHROMA_OPD_GAIN * m0 + CHROMA_OIL_GAIN * mOil + CHROMA_SOAP_GAIN * mSoap
                           + CHROMA_FOILM_GAIN * mFoilM + CHROMA_PEARL_GAIN * mPearl;
        float3 lookKRGB    = CHROMA_KRGB * m0 + CHROMA_OIL_KRGB * mOil + CHROMA_SOAP_KRGB * mSoap
                           + CHROMA_FOILM_KRGB * mFoilM + CHROMA_PEARL_KRGB * mPearl;
        float  lookFloor   = CHROMA_METAL_FLOOR * m0 + CHROMA_OIL_FLOOR * mOil + CHROMA_SOAP_FLOOR * mSoap
                           + CHROMA_FOILM_FLOOR * mFoilM + CHROMA_PEARL_FLOOR * mPearl;
        float  lookWashout = CHROMA_WASHOUT * m0 + CHROMA_OIL_WASHOUT * mOil + CHROMA_SOAP_WASHOUT * mSoap
                           + CHROMA_FOILM_WASHOUT * mFoilM + CHROMA_PEARL_WASHOUT * mPearl;

        // Iridescent: 박막 간섭(Newton's rings). 광학 경로차 opd = thickness/cos(refr).
        //   thickness = bevel 깊이 cT(중심0→림1; cNcos=1-cT) → 림으로 갈수록 막이 두꺼워 밴드 촘촘.
        //   cosT = cos(입사각) = dot(cN,cL) → 빛이 돌면 위상 쓸림(gyro 반응). 1e-2 가드(grazing 발산).
        // CHROMA_THICK_MIX로 두께링(공간)↔빛각도(반응)를 블렌드, GAIN이 면 위 Newton 밴드 수를 정한다.
        float cosT     = clamp(dot(cN, cL), 0.0, 1.0);
        float thick    = 1.0 - cNcos;                                // = cT, bevel 깊이(중심0→림1)
        float ringTerm = thick / max(1.0 - 0.6 * cosT, 1.0e-2);      // 두께/cos: Newton 링(빛 의존)
        float opdDrive = mix(cosT, ringTerm, CHROMA_THICK_MIX);      // 빛각도↔두께링 블렌드
        float opd      = opdDrive * (chromaticCycles * lookGain) + CHROMA_OPD_BASE + chromaticPhase;
        // per-channel 보강간섭(파장 역수비 kRGB). 0.5+0.5cos = [0,1] 중심 0.5(은백 기준 진동).
        float3 interf = 0.5 + 0.5 * cos(6.28318530718 * opd * lookKRGB);
        // 금속 floor: 채널이 0까지 안 떨어지게(형광 무지개=채널 하나 늘 0 → sticker). 진동폭만 살린다.
        float3 metalRGB = lookFloor + (1.0 - lookFloor) * interf;
        // 고차 coherence wash-out: OPD 클수록 파스텔→clean silver(흰 1.0 기준; metalRGB luma로 하면
        // 저채도부가 베이지로 탁해진다). 약하게 둬 금속 채도 유지. sat=간섭색 보존율.
        float  sat       = exp(-opd * lookWashout);
        float3 thinFilm  = mix(float3(1.0), metalRGB, clamp(sat, 0.0, 1.0)); // 은백(흰) ↔ 간섭색
        // Fresnel rim 부스트: 가장자리(cT→1)서 thinFilm을 흰쪽으로 더 밝게(유리 림 광택).
        //   MetallicFoil/Pearl만(mFoilM+mPearl 마스크) → Iridescent/Foil/Oil/Soap 불변.
        //   cT=thick(중심0→림1). pow로 림 집중. 0..1 안에서 mix → 클램프 불필요.
        float  rimSel    = mFoilM + mPearl;                          // 0/1 (해당 모드만)
        float  rimBoost  = rimSel * CHROMA_RIM_GAIN * pow(clamp(thick, 0.0, 1.0), CHROMA_RIM_POW);
        thinFilm         = mix(thinFilm, float3(1.0), clamp(rimBoost, 0.0, 1.0)); // 림으로 갈수록 백광
        // mode 1만 Foil(선형 띠)로 분기, 나머지(0,2,3,4,5)는 thin-film. step-window = 정확히 0/1 →
        //   mode 1: mix(thinFilm, foilRGB, 1.0)=foilRGB(불변). mode 0: mix(...,0.0)=thinFilm(불변).
        float  isFoil    = step(0.5, cMode) * (1.0 - step(1.5, cMode));
        float3 chromaRGB = mix(thinFilm, foilRGB, isFoil);           // 1=Foil, 그외=thin-film 변형

        // focal-pool 모듈레이션 — specular의 raw pool²(정규화본; specStrength/Gain 곱 전)으로 무지개 세기 변조.
        // specular 게이트 밖이라 pool을 저렴하게 재계산(length+smoothstep 몇 줄): focal/poolR 식은 specular와 동일.
        float2 cFocal  = cLightVec * (cMinHalf * specFocalK);        // 빛 방향 오프셋(lensCenter=원점 기준)
        float  cPoolR  = max(cMinHalf * specPoolFrac, 1.0);          // zero-width smoothstep 가드
        float  cPool   = 1.0 - smoothstep(0.0, cPoolR, length(p - cFocal)); // 1 at focal, 0 at rim (edge0<edge1)
        float  poolNorm = clamp(cPool * cPool, 0.0, 1.0);           // raw pool²(정규화) = modulator
        // "빛 반사처럼 깔리는" 강화 — 무지개 세기를 focal-pool(빛 핫스팟)로 변조해 빛 따라 진해진다.
        //   effMod = focal-pool 추종 세기. mode 0(Iridescent)/1(Foil)은 binding 값(chromaticModulate,
        //   현재 0.3) 그대로 → bit-exact 무회귀(마스크 0/1, x*1+0*…=x). 신규 4종만 per-look(0.2~0.82)로
        //   끌어올려 카드 위 빛 반사 지각을 강화. 새 식/uniform 0개(기존 cPool 재사용).
        float  baseMod = m0 + isFoil;                               // mode 0 또는 1 → 1.0, 그외 0.0
        float  effMod  = chromaticModulate * baseMod
                       + CHROMA_OIL_MOD * mOil + CHROMA_SOAP_MOD * mSoap
                       + CHROMA_FOILM_MOD * mFoilM + CHROMA_PEARL_MOD * mPearl;
        float  chroma  = chromaticIntensity * mix(1.0, poolNorm, clamp(effMod, 0.0, 1.0));

        // 두 경로가 정반대 블렌드를 원한다 → content alpha로 보간:
        //   투명 베이스(a→0, 흰 카드): cOnWhite = chromaRGB. 흰(1,1,1)*무지개 = 순수 무지개. screen이면
        //     흰 배경에서 1-(1-1)*(…)=1 로 무지개가 사라지므로 multiply(=흰*chromaRGB)가 맞다. 절대 불변.
        //   불투명 베이스(a→1, 사진): cOnSrc = SCREEN(=어두워지지 않는 glow). multiply는 항상 값을 낮춰
        //     (chromaRGB는 채널 하나가 항상 0) 어두운/컬러 픽셀을 탁하게 만든다(인물부 색조오염, 측정 -26%).
        //     screen = pixel + (1-pixel)*chromaRGB*chroma 와 등가 → 항상 pixel 이상(밝아지는 방향),
        //     1 self-limit(과포화/클립 없음), 흰 픽셀(=1)은 거의 불변. 어두운 인물부에도 무지개가 더해 보인다.
        // 불투명 white(=1)는 screen이 1을 유지(흰 위에 glow를 더 못 얹음) — 물리적으로 옳고, 흰 '카드'는
        // 투명 경로(a≈0)를 타므로 회귀 없음. chromaticIntensity==0이면 블록 미실행 → bit-exact off 보존.
        half  cChroma  = half(clamp(chroma, 0.0, 1.0));
        half3 cChromaRGB = half3(chromaRGB) * cChroma;            // chroma 가중 무지개(공유 항)
        half3 cOnWhite = half3(chromaRGB);                        // 흰 베이스(1,1,1) * chromaRGB = chromaRGB(불변)
        half3 cOnSrc   = half3(1.0) - (half3(1.0) - pixel.rgb) * (half3(1.0) - cChromaRGB); // SCREEN glow
        pixel.rgb = mix(cOnWhite, cOnSrc, pixel.a);              // a=0→흰 위 무지개, a=1→screen glow
        pixel.a   = max(pixel.a, cChroma);                       // 투명 배경에서도 오버레이가 보이도록
    }

    // Anti-aliased edge transition
    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""
}
