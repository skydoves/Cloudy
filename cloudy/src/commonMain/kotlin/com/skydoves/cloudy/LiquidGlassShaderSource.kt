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
 * Liquid Glass shader for realistic glass lens distortion effects.
 *
 * Features: rounded-rect lens geometry, light bending through a curved surface,
 * RGB wavelength separation, color processing (vibrancy/intensity/overlay),
 * specular rim highlights, and a light-reactive chromatic overlay.
 *
 * Pair with [Modifier.cloudy] for combined blur + glass. Supports AGSL (Android 13+)
 * and SKSL (Skia-based platforms).
 */
public object LiquidGlassShaderSource {

  /** AGSL shader for Android RuntimeShader (API 33+). */
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
uniform float specRimMix;     // body<->rim crossfade
uniform float specWidthPx;
uniform float specLightZ;
uniform float specDomeFrac;
uniform float specBodyPower;
uniform float specBodyGain;
uniform float specFocalK;          // focal-pool offset toward light (fraction of minHalf)
uniform float specPoolFrac;        // focal-pool radius (fraction of minHalf)
uniform float specPoolGain;        // focal-pool peak scale
uniform float chromaticIntensity;  // 0 = off (bit-exact, ALU 0). 0..1 overlay strength
uniform float chromaticMode;       // float enum: 0 Iridescent, 1 Foil, 2..5 named looks
uniform float chromaticBands;      // Foil rainbow band count along the light direction
uniform float chromaticCycles;     // Iridescent hue cycles across the light/normal angle
uniform float chromaticPhase;      // static hue phase offset (fract domain)
uniform float chromaticModulate;   // 0..1, modulate rainbow strength by the focal pool
uniform shader content;

const float SMOOTH_EDGE_PX = 1.5;
const float SEAM_BLEND_PX = 8.0;   // diagonal-seam blend half-width (px)

// Thin-film interference (Iridescent / Holographic) base set.
// opd ~ thickness/cos(refr) (Newton's rings): thickness = bevel depth (center 0 -> rim 1),
// cosT = cos(incidence) (light-reactive). Thicker film at the rim -> denser bands.
const float CHROMA_OPD_GAIN  = 3.0;   // Newton band count (too high -> aliasing/busy)
const float CHROMA_OPD_BASE  = 0.10;  // base film order (low-OPD center sits at silver 0th)
const float CHROMA_THICK_MIX = 0.55;  // 1 = pure thickness rings, 0 = pure light angle
const float3 CHROMA_KRGB     = float3(1.0, 1.18, 1.42); // wavenumber ratio (~650/560/470nm)
const float CHROMA_METAL_FLOOR = 0.12; // keep channels off 0 (metal, not neon)
const float CHROMA_WASHOUT     = 0.16; // higher-order wash-out to silver

// Named holographic looks (chromaticMode 2..5). Same thin-film path, per-look set picked
// by a branchless step-mask (no in-shader mode branch, no extra uniform). mode 0/1 keep the
// base constants bit-exact. Fields: GAIN (band count), KRGB (gold<->cyan split), FLOOR
// (contrast, lower = more), WASHOUT (pastel rate), MOD (focal-pool follow strength).
const float CHROMA_OIL_GAIN       = 5.5;
const float3 CHROMA_OIL_KRGB      = float3(1.0, 1.30, 1.72);
const float CHROMA_OIL_FLOOR      = 0.05;
const float CHROMA_OIL_WASHOUT    = 0.07;
const float CHROMA_OIL_MOD        = 0.75;
const float CHROMA_SOAP_GAIN      = 1.7;
const float3 CHROMA_SOAP_KRGB     = float3(1.0, 1.11, 1.26);
const float CHROMA_SOAP_FLOOR     = 0.22;
const float CHROMA_SOAP_WASHOUT   = 0.50;
const float CHROMA_SOAP_MOD       = 0.22;
const float CHROMA_FOILM_GAIN     = 3.6;
const float3 CHROMA_FOILM_KRGB    = float3(1.0, 1.26, 1.62);
const float CHROMA_FOILM_FLOOR    = 0.03;
const float CHROMA_FOILM_WASHOUT  = 0.05;
const float CHROMA_FOILM_MOD      = 0.82;
const float CHROMA_PEARL_GAIN     = 2.4;
const float3 CHROMA_PEARL_KRGB    = float3(1.0, 1.07, 1.18);
const float CHROMA_PEARL_FLOOR    = 0.46;
const float CHROMA_PEARL_WASHOUT  = 0.58;
const float CHROMA_PEARL_MOD      = 0.20;
// Fresnel rim boost (cT->1), added for MetallicFoil/Pearl only.
const float CHROMA_RIM_POW        = 3.0;
const float CHROMA_RIM_GAIN       = 0.45;

// Signed distance to a rounded-corner box. Negative inside, positive outside.
float boxRoundedSDF(float2 p, float2 halfDim, float r) {
    float2 d = abs(p) - halfDim + float2(r);
    float exterior = length(max(d, 0.0));
    float interior = min(max(d.x, d.y), 0.0);
    return exterior + interior - r;
}

// Outward-facing direction from the lens surface.
float2 lensNormalDirection(float2 p, float2 halfDim, float r) {
    float2 d = abs(p) - halfDim + float2(r);
    float2 s = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);

    if (max(d.x, d.y) > 0.0) {
        return s * normalize(max(d, 0.0));
    }
    return d.x > d.y ? float2(s.x, 0.0) : float2(0.0, s.y);
}

// Perceptual brightness (ITU-R BT.709).
float toBrightness(half3 c) {
    return dot(c, half3(0.2126, 0.7152, 0.0722));
}

// Vibrancy, intensity adjustment, and color overlay.
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

    // Specular highlight: moving focal pool + body sheen + tight Blinn rim glint + back-rim fill.
    // Gate also checks specStrength so specStrength==0 is ALU 0 + bit-exact off.
    //   specRimMix 0 = pure body (focal pool), 1 = pure rim glint
    //   specPower  rim/back lobe sharpness (Blinn); specWidthPx rim band thickness
    //   specStrength peak highlight (screen-blended, <= 1.0)
    //   specLightZ/specDomeFrac/specBodyPower/specBodyGain  fake-3D bevel lighting
    //   specFocalK/specPoolFrac/specPoolGain  dual-axis moving hotspot
    if (edge > 0.0 && specStrength > 0.0) {
        float2 lightVec = normalize(lightDir);

        // Seam-free in-plane direction (specular only; refraction's 'normal' is unchanged).
        // Rounded-rect interior is an L-inf field, so the gradient is discontinuous on the
        // diagonal -> blend with an 8px softmax.
        float2 d2 = abs(p) - halfDim + float2(r);
        float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float2 specDir2;
        if (max(d2.x, d2.y) > 0.0) {
            specDir2 = s2 * normalize(max(d2, 0.0));
        } else {
            // w->1 x-dominant, ->0 y-dominant, =0.5 diagonal seam. +1e-4 guards normalize at dead center.
            float w  = clamp(0.5 + 0.5 * (d2.x - d2.y) / SEAM_BLEND_PX, 0.0, 1.0);
            float2 v = float2(s2.x * w, s2.y * (1.0 - w)) + float2(0.0, 1.0e-4);
            specDir2 = normalize(v);
        }

        // Fake-3D surface normal from the rounded-rect bevel.
        float minHalf = min(halfDim.x, halfDim.y);
        float bevelPx = max(minHalf * specDomeFrac, 1.0);
        float depthIn = max(-sdf, 0.0);
        float t       = clamp(depthIn / bevelPx, 0.0, 1.0);
        float n_cos   = 1.0 - t;
        float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));      // float: catastrophic cancellation near n_cos~1
        float3 N      = normalize(float3(specDir2 * n_cos, n_sin + 1.0e-3));

        float3 L = normalize(float3(lightVec, specLightZ));
        float3 V = float3(0.0, 0.0, 1.0);

        // Moving focal hotspot: shift the pool by minHalf*specFocalK along lightVec so it
        // tracks the light on both axes (pitch=lightVec.y, roll=lightVec.x). inside fades the rim.
        float2 focal     = lightVec * (minHalf * specFocalK);
        float  poolR     = max(minHalf * specPoolFrac, 1.0);      // guard zero-width smoothstep
        float  poolD     = length(p - focal);
        float  pool      = 1.0 - smoothstep(0.0, poolR, poolD);   // edge0<edge1; 1 at focal, 0 at rim
        float  inside    = 1.0 - smoothstep(-6.0, 0.0, sdf);      // lens interior only
        float  focalPool = pool * pool * specStrength * specPoolGain * inside; // pool^2 = harder core

        // Broad body sheen (modeling fill).
        float ndl       = max(dot(N, L), 0.0);
        float bodySheen = pow(ndl, specBodyPower) * specStrength * specBodyGain; // float: pow bands in fp16

        // Tight Blinn rim glint, limited to the rim band.
        float3 H       = normalize(L + V);
        float  rimBand = smoothstep(-max(specWidthPx, 1.0), 0.0, sdf); // max(..,1) guards zero-width smoothstep
        float  glint   = pow(max(dot(N, H), 0.0), specPower) * specStrength; // float: pow bands in fp16
        float  rim     = glint * rimBand;

        // Back-rim fill (opposite light, rim-locked, 1/4 weight).
        float3 Lb   = normalize(float3(-lightVec, specLightZ));
        float  back  = pow(max(dot(N, Lb), 0.0), specPower) * specStrength * rimBand * 0.25; // float: pow

        // Ordered dither to break 8-bit Mach banding on the body ramp.
        // fract-bound the coords before sin to avoid argument blow-up on large lenses.
        float2 hp = fract((p / minHalf) * 0.5 + 0.5);
        float  dn = fract(sin(dot(hp, float2(12.9898, 78.233))) * 43758.5453) - 0.5;

        // Linear body<->rim crossfade (monotonic, pure endpoints).
        float body      = focalPool + bodySheen + dn * (1.0 / 255.0) * specStrength;
        float rimMix    = clamp(specRimMix, 0.0, 1.0);
        float highlight = body * (1.0 - rimMix) + (rim + back) * rimMix;

        // Screen blend: survives bright backgrounds, clamp keeps it <= 1.0.
        pixel.rgb += half3((1.0 - pixel.rgb) * clamp(highlight, 0.0, 1.0));
    }

    // Chromatic overlay: light-reactive iridescent sheen, independent of the white specular pool.
    // Own gate, so chromaticIntensity==0 is bit-exact off / ALU 0. Works even when specular is off.
    //   mode 0 = Iridescent (thin-film, hue from light/normal angle)
    //   mode 1 = Foil (light-direction projection, bands flow as lightVec moves)
    // hue/HSV->RGB chain is float (AGSL half/fp16 bands hard on saturated rainbows).
    // Blend is tint-multiply (tints even a white card); screen would erase the rainbow on white.
    if (chromaticIntensity > 0.0) {
        // Recompute shared scalars (independent of the specular gate).
        float2 cLightVec = normalize(lightDir);
        float  cMinHalf  = min(halfDim.x, halfDim.y);
        float2 pNorm     = p / cMinHalf;

        // Bevel normal + 3D light (for dot(cN,cL)), mirroring the specular math with chroma-local names.
        float2 cD2 = abs(p) - halfDim + float2(r);
        float2 cS2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float2 cSpecDir;
        if (max(cD2.x, cD2.y) > 0.0) {
            cSpecDir = cS2 * normalize(max(cD2, 0.0));
        } else {
            float cw  = clamp(0.5 + 0.5 * (cD2.x - cD2.y) / SEAM_BLEND_PX, 0.0, 1.0);
            float2 cv = float2(cS2.x * cw, cS2.y * (1.0 - cw)) + float2(0.0, 1.0e-4);
            cSpecDir  = normalize(cv);
        }
        float  cBevelPx = max(cMinHalf * specDomeFrac, 1.0);
        float  cT       = clamp(max(-sdf, 0.0) / cBevelPx, 0.0, 1.0);
        float  cNcos    = 1.0 - cT;
        float  cNsin    = sqrt(max(1.0 - cNcos * cNcos, 0.0));        // float: cancellation guard near cNcos~1
        float3 cN       = normalize(float3(cSpecDir * cNcos, cNsin + 1.0e-3));
        float3 cL       = normalize(float3(cLightVec, specLightZ));

        // Foil (mode 1): linear hue band projected onto the light direction -> HSV ramp.
        float hueF = fract(dot(pNorm, cLightVec) * chromaticBands + chromaticPhase);
        float3 foilRGB = clamp(
            abs(fract(float3(hueF) + float3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0,
            0.0, 1.0);

        // Per-look parameter select (chromaticMode -> thin-film set), branchless step-mask.
        // Masks are exactly 0.0/1.0, so mode 0 -> base const bit-exact (x*1+0*..=x); mode 1 (Foil)
        // discards thinFilm via isFoil below, so params are irrelevant there.
        float cMode  = chromaticMode;
        float m0     = 1.0 - step(0.5, cMode);                       // mode 0 (base)
        float mOil   = step(1.5, cMode) * (1.0 - step(2.5, cMode));  // mode 2
        float mSoap  = step(2.5, cMode) * (1.0 - step(3.5, cMode));  // mode 3
        float mFoilM = step(3.5, cMode) * (1.0 - step(4.5, cMode));  // mode 4
        float mPearl = step(4.5, cMode);                             // mode 5+
        float  lookGain    = CHROMA_OPD_GAIN * m0 + CHROMA_OIL_GAIN * mOil + CHROMA_SOAP_GAIN * mSoap
                           + CHROMA_FOILM_GAIN * mFoilM + CHROMA_PEARL_GAIN * mPearl;
        float3 lookKRGB    = CHROMA_KRGB * m0 + CHROMA_OIL_KRGB * mOil + CHROMA_SOAP_KRGB * mSoap
                           + CHROMA_FOILM_KRGB * mFoilM + CHROMA_PEARL_KRGB * mPearl;
        float  lookFloor   = CHROMA_METAL_FLOOR * m0 + CHROMA_OIL_FLOOR * mOil + CHROMA_SOAP_FLOOR * mSoap
                           + CHROMA_FOILM_FLOOR * mFoilM + CHROMA_PEARL_FLOOR * mPearl;
        float  lookWashout = CHROMA_WASHOUT * m0 + CHROMA_OIL_WASHOUT * mOil + CHROMA_SOAP_WASHOUT * mSoap
                           + CHROMA_FOILM_WASHOUT * mFoilM + CHROMA_PEARL_WASHOUT * mPearl;

        // Iridescent thin-film (Newton's rings). opd = thickness/cos(refr).
        //   thick = bevel depth cT (center 0 -> rim 1); cosT = dot(cN,cL) (light-reactive, gyro).
        //   1e-2 guards grazing-angle blow-up. THICK_MIX blends thickness rings vs light angle.
        float cosT     = clamp(dot(cN, cL), 0.0, 1.0);
        float thick    = 1.0 - cNcos;
        float ringTerm = thick / max(1.0 - 0.6 * cosT, 1.0e-2);
        float opdDrive = mix(cosT, ringTerm, CHROMA_THICK_MIX);
        float opd      = opdDrive * (chromaticCycles * lookGain) + CHROMA_OPD_BASE + chromaticPhase;
        // Per-channel constructive interference; 0.5+0.5cos oscillates about silver.
        float3 interf = 0.5 + 0.5 * cos(6.28318530718 * opd * lookKRGB);
        // Metal floor keeps channels off 0 (off-0 = metal, never neon).
        float3 metalRGB = lookFloor + (1.0 - lookFloor) * interf;
        // Higher-order wash-out toward clean silver (white ref, not luma -> avoids beige muddying).
        float  sat       = exp(-opd * lookWashout);
        float3 thinFilm  = mix(float3(1.0), metalRGB, clamp(sat, 0.0, 1.0));
        // Fresnel rim boost: brighten toward white at the rim (cT->1). MetallicFoil/Pearl only.
        float  rimSel    = mFoilM + mPearl;
        float  rimBoost  = rimSel * CHROMA_RIM_GAIN * pow(clamp(thick, 0.0, 1.0), CHROMA_RIM_POW);
        thinFilm         = mix(thinFilm, float3(1.0), clamp(rimBoost, 0.0, 1.0));
        // mode 1 -> Foil, all others -> thin-film. step-window is exactly 0/1 (no regression).
        float  isFoil    = step(0.5, cMode) * (1.0 - step(1.5, cMode));
        float3 chromaRGB = mix(thinFilm, foilRGB, isFoil);

        // Focal-pool modulation: drive rainbow strength by the raw pool^2 (normalized, pre-gain).
        float2 cFocal  = cLightVec * (cMinHalf * specFocalK);
        float  cPoolR  = max(cMinHalf * specPoolFrac, 1.0);          // guard zero-width smoothstep
        float  cPool   = 1.0 - smoothstep(0.0, cPoolR, length(p - cFocal)); // edge0<edge1
        float  poolNorm = clamp(cPool * cPool, 0.0, 1.0);
        // effMod = focal-pool follow strength. mode 0/1 keep the binding value (bit-exact);
        // named looks raise it per-look. No new uniform (reuses cPool).
        float  baseMod = m0 + isFoil;                               // mode 0 or 1 -> 1.0
        float  effMod  = chromaticModulate * baseMod
                       + CHROMA_OIL_MOD * mOil + CHROMA_SOAP_MOD * mSoap
                       + CHROMA_FOILM_MOD * mFoilM + CHROMA_PEARL_MOD * mPearl;
        float  chroma  = chromaticIntensity * mix(1.0, poolNorm, clamp(effMod, 0.0, 1.0));

        // Two blends interpolated by content alpha:
        //   transparent base (a->0, white card): multiply (white*chromaRGB = pure rainbow).
        //     screen would erase it on white (1-(1-1)*..=1), so multiply is correct.
        //   opaque base (a->1, photo): screen glow (never darkens). multiply would dim/mud
        //     colored pixels since one chromaRGB channel is ~0.
        half  cChroma  = half(clamp(chroma, 0.0, 1.0));
        half3 cChromaRGB = half3(chromaRGB) * cChroma;
        half3 cOnWhite = half3(chromaRGB);
        half3 cOnSrc   = half3(1.0) - (half3(1.0) - pixel.rgb) * (half3(1.0) - cChromaRGB); // SCREEN
        pixel.rgb = mix(cOnWhite, cOnSrc, pixel.a);
        pixel.a   = max(pixel.a, cChroma);                       // keep overlay visible over transparent bg
    }

    // Anti-aliased edge transition
    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""

  /** SKSL shader for Skia RuntimeEffect (iOS, macOS, Desktop, WASM). */
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
uniform float specRimMix;     // body<->rim crossfade
uniform float specWidthPx;
uniform float specLightZ;
uniform float specDomeFrac;
uniform float specBodyPower;
uniform float specBodyGain;
uniform float specFocalK;          // focal-pool offset toward light (fraction of minHalf)
uniform float specPoolFrac;        // focal-pool radius (fraction of minHalf)
uniform float specPoolGain;        // focal-pool peak scale
uniform float chromaticIntensity;  // 0 = off (bit-exact, ALU 0). 0..1 overlay strength
uniform float chromaticMode;       // float enum: 0 Iridescent, 1 Foil, 2..5 named looks
uniform float chromaticBands;      // Foil rainbow band count along the light direction
uniform float chromaticCycles;     // Iridescent hue cycles across the light/normal angle
uniform float chromaticPhase;      // static hue phase offset (fract domain)
uniform float chromaticModulate;   // 0..1, modulate rainbow strength by the focal pool
uniform shader content;

const float SMOOTH_EDGE_PX = 1.5;
const float SEAM_BLEND_PX = 8.0;   // diagonal-seam blend half-width (px)

// Thin-film interference (Iridescent / Holographic) base set.
// opd ~ thickness/cos(refr) (Newton's rings): thickness = bevel depth (center 0 -> rim 1),
// cosT = cos(incidence) (light-reactive). Thicker film at the rim -> denser bands.
const float CHROMA_OPD_GAIN  = 3.0;   // Newton band count (too high -> aliasing/busy)
const float CHROMA_OPD_BASE  = 0.10;  // base film order (low-OPD center sits at silver 0th)
const float CHROMA_THICK_MIX = 0.55;  // 1 = pure thickness rings, 0 = pure light angle
const float3 CHROMA_KRGB     = float3(1.0, 1.18, 1.42); // wavenumber ratio (~650/560/470nm)
const float CHROMA_METAL_FLOOR = 0.12; // keep channels off 0 (metal, not neon)
const float CHROMA_WASHOUT     = 0.16; // higher-order wash-out to silver

// Named holographic looks (chromaticMode 2..5). Same thin-film path, per-look set picked
// by a branchless step-mask (no in-shader mode branch, no extra uniform). mode 0/1 keep the
// base constants bit-exact. Fields: GAIN (band count), KRGB (gold<->cyan split), FLOOR
// (contrast, lower = more), WASHOUT (pastel rate), MOD (focal-pool follow strength).
const float CHROMA_OIL_GAIN       = 5.5;
const float3 CHROMA_OIL_KRGB      = float3(1.0, 1.30, 1.72);
const float CHROMA_OIL_FLOOR      = 0.05;
const float CHROMA_OIL_WASHOUT    = 0.07;
const float CHROMA_OIL_MOD        = 0.75;
const float CHROMA_SOAP_GAIN      = 1.7;
const float3 CHROMA_SOAP_KRGB     = float3(1.0, 1.11, 1.26);
const float CHROMA_SOAP_FLOOR     = 0.22;
const float CHROMA_SOAP_WASHOUT   = 0.50;
const float CHROMA_SOAP_MOD       = 0.22;
const float CHROMA_FOILM_GAIN     = 3.6;
const float3 CHROMA_FOILM_KRGB    = float3(1.0, 1.26, 1.62);
const float CHROMA_FOILM_FLOOR    = 0.03;
const float CHROMA_FOILM_WASHOUT  = 0.05;
const float CHROMA_FOILM_MOD      = 0.82;
const float CHROMA_PEARL_GAIN     = 2.4;
const float3 CHROMA_PEARL_KRGB    = float3(1.0, 1.07, 1.18);
const float CHROMA_PEARL_FLOOR    = 0.46;
const float CHROMA_PEARL_WASHOUT  = 0.58;
const float CHROMA_PEARL_MOD      = 0.20;
// Fresnel rim boost (cT->1), added for MetallicFoil/Pearl only.
const float CHROMA_RIM_POW        = 3.0;
const float CHROMA_RIM_GAIN       = 0.45;

// Signed distance to a rounded-corner box. Negative inside, positive outside.
float boxRoundedSDF(float2 p, float2 halfDim, float r) {
    float2 d = abs(p) - halfDim + float2(r);
    float exterior = length(max(d, 0.0));
    float interior = min(max(d.x, d.y), 0.0);
    return exterior + interior - r;
}

// Outward-facing direction from the lens surface.
float2 lensNormalDirection(float2 p, float2 halfDim, float r) {
    float2 d = abs(p) - halfDim + float2(r);
    float2 s = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);

    if (max(d.x, d.y) > 0.0) {
        return s * normalize(max(d, 0.0));
    }
    return d.x > d.y ? float2(s.x, 0.0) : float2(0.0, s.y);
}

// Perceptual brightness (ITU-R BT.709).
float toBrightness(half3 c) {
    return dot(c, half3(0.2126, 0.7152, 0.0722));
}

// Vibrancy, intensity adjustment, and color overlay.
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

    // Specular highlight: moving focal pool + body sheen + tight Blinn rim glint + back-rim fill.
    // Gate also checks specStrength so specStrength==0 is ALU 0 + bit-exact off.
    //   specRimMix 0 = pure body (focal pool), 1 = pure rim glint
    //   specPower  rim/back lobe sharpness (Blinn); specWidthPx rim band thickness
    //   specStrength peak highlight (screen-blended, <= 1.0)
    //   specLightZ/specDomeFrac/specBodyPower/specBodyGain  fake-3D bevel lighting
    //   specFocalK/specPoolFrac/specPoolGain  dual-axis moving hotspot
    if (edge > 0.0 && specStrength > 0.0) {
        float2 lightVec = normalize(lightDir);

        // Seam-free in-plane direction (specular only; refraction's 'normal' is unchanged).
        // Rounded-rect interior is an L-inf field, so the gradient is discontinuous on the
        // diagonal -> blend with an 8px softmax.
        float2 d2 = abs(p) - halfDim + float2(r);
        float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float2 specDir2;
        if (max(d2.x, d2.y) > 0.0) {
            specDir2 = s2 * normalize(max(d2, 0.0));
        } else {
            // w->1 x-dominant, ->0 y-dominant, =0.5 diagonal seam. +1e-4 guards normalize at dead center.
            float w  = clamp(0.5 + 0.5 * (d2.x - d2.y) / SEAM_BLEND_PX, 0.0, 1.0);
            float2 v = float2(s2.x * w, s2.y * (1.0 - w)) + float2(0.0, 1.0e-4);
            specDir2 = normalize(v);
        }

        // Fake-3D surface normal from the rounded-rect bevel.
        float minHalf = min(halfDim.x, halfDim.y);
        float bevelPx = max(minHalf * specDomeFrac, 1.0);
        float depthIn = max(-sdf, 0.0);
        float t       = clamp(depthIn / bevelPx, 0.0, 1.0);
        float n_cos   = 1.0 - t;
        float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));      // float: catastrophic cancellation near n_cos~1
        float3 N      = normalize(float3(specDir2 * n_cos, n_sin + 1.0e-3));

        float3 L = normalize(float3(lightVec, specLightZ));
        float3 V = float3(0.0, 0.0, 1.0);

        // Moving focal hotspot: shift the pool by minHalf*specFocalK along lightVec so it
        // tracks the light on both axes (pitch=lightVec.y, roll=lightVec.x). inside fades the rim.
        float2 focal     = lightVec * (minHalf * specFocalK);
        float  poolR     = max(minHalf * specPoolFrac, 1.0);      // guard zero-width smoothstep
        float  poolD     = length(p - focal);
        float  pool      = 1.0 - smoothstep(0.0, poolR, poolD);   // edge0<edge1; 1 at focal, 0 at rim
        float  inside    = 1.0 - smoothstep(-6.0, 0.0, sdf);      // lens interior only
        float  focalPool = pool * pool * specStrength * specPoolGain * inside; // pool^2 = harder core

        // Broad body sheen (modeling fill).
        float ndl       = max(dot(N, L), 0.0);
        float bodySheen = pow(ndl, specBodyPower) * specStrength * specBodyGain; // float: pow bands in fp16

        // Tight Blinn rim glint, limited to the rim band.
        float3 H       = normalize(L + V);
        float  rimBand = smoothstep(-max(specWidthPx, 1.0), 0.0, sdf); // max(..,1) guards zero-width smoothstep
        float  glint   = pow(max(dot(N, H), 0.0), specPower) * specStrength; // float: pow bands in fp16
        float  rim     = glint * rimBand;

        // Back-rim fill (opposite light, rim-locked, 1/4 weight).
        float3 Lb   = normalize(float3(-lightVec, specLightZ));
        float  back  = pow(max(dot(N, Lb), 0.0), specPower) * specStrength * rimBand * 0.25; // float: pow

        // Ordered dither to break 8-bit Mach banding on the body ramp.
        // fract-bound the coords before sin to avoid argument blow-up on large lenses.
        float2 hp = fract((p / minHalf) * 0.5 + 0.5);
        float  dn = fract(sin(dot(hp, float2(12.9898, 78.233))) * 43758.5453) - 0.5;

        // Linear body<->rim crossfade (monotonic, pure endpoints).
        float body      = focalPool + bodySheen + dn * (1.0 / 255.0) * specStrength;
        float rimMix    = clamp(specRimMix, 0.0, 1.0);
        float highlight = body * (1.0 - rimMix) + (rim + back) * rimMix;

        // Screen blend: survives bright backgrounds, clamp keeps it <= 1.0.
        pixel.rgb += half3((1.0 - pixel.rgb) * clamp(highlight, 0.0, 1.0));
    }

    // Chromatic overlay: light-reactive iridescent sheen, independent of the white specular pool.
    // Own gate, so chromaticIntensity==0 is bit-exact off / ALU 0. Works even when specular is off.
    //   mode 0 = Iridescent (thin-film, hue from light/normal angle)
    //   mode 1 = Foil (light-direction projection, bands flow as lightVec moves)
    // hue/HSV->RGB chain is float (AGSL half/fp16 bands hard on saturated rainbows).
    // Blend is tint-multiply (tints even a white card); screen would erase the rainbow on white.
    if (chromaticIntensity > 0.0) {
        // Recompute shared scalars (independent of the specular gate).
        float2 cLightVec = normalize(lightDir);
        float  cMinHalf  = min(halfDim.x, halfDim.y);
        float2 pNorm     = p / cMinHalf;

        // Bevel normal + 3D light (for dot(cN,cL)), mirroring the specular math with chroma-local names.
        float2 cD2 = abs(p) - halfDim + float2(r);
        float2 cS2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float2 cSpecDir;
        if (max(cD2.x, cD2.y) > 0.0) {
            cSpecDir = cS2 * normalize(max(cD2, 0.0));
        } else {
            float cw  = clamp(0.5 + 0.5 * (cD2.x - cD2.y) / SEAM_BLEND_PX, 0.0, 1.0);
            float2 cv = float2(cS2.x * cw, cS2.y * (1.0 - cw)) + float2(0.0, 1.0e-4);
            cSpecDir  = normalize(cv);
        }
        float  cBevelPx = max(cMinHalf * specDomeFrac, 1.0);
        float  cT       = clamp(max(-sdf, 0.0) / cBevelPx, 0.0, 1.0);
        float  cNcos    = 1.0 - cT;
        float  cNsin    = sqrt(max(1.0 - cNcos * cNcos, 0.0));        // float: cancellation guard near cNcos~1
        float3 cN       = normalize(float3(cSpecDir * cNcos, cNsin + 1.0e-3));
        float3 cL       = normalize(float3(cLightVec, specLightZ));

        // Foil (mode 1): linear hue band projected onto the light direction -> HSV ramp.
        float hueF = fract(dot(pNorm, cLightVec) * chromaticBands + chromaticPhase);
        float3 foilRGB = clamp(
            abs(fract(float3(hueF) + float3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0,
            0.0, 1.0);

        // Per-look parameter select (chromaticMode -> thin-film set), branchless step-mask.
        // Masks are exactly 0.0/1.0, so mode 0 -> base const bit-exact (x*1+0*..=x); mode 1 (Foil)
        // discards thinFilm via isFoil below, so params are irrelevant there.
        float cMode  = chromaticMode;
        float m0     = 1.0 - step(0.5, cMode);                       // mode 0 (base)
        float mOil   = step(1.5, cMode) * (1.0 - step(2.5, cMode));  // mode 2
        float mSoap  = step(2.5, cMode) * (1.0 - step(3.5, cMode));  // mode 3
        float mFoilM = step(3.5, cMode) * (1.0 - step(4.5, cMode));  // mode 4
        float mPearl = step(4.5, cMode);                             // mode 5+
        float  lookGain    = CHROMA_OPD_GAIN * m0 + CHROMA_OIL_GAIN * mOil + CHROMA_SOAP_GAIN * mSoap
                           + CHROMA_FOILM_GAIN * mFoilM + CHROMA_PEARL_GAIN * mPearl;
        float3 lookKRGB    = CHROMA_KRGB * m0 + CHROMA_OIL_KRGB * mOil + CHROMA_SOAP_KRGB * mSoap
                           + CHROMA_FOILM_KRGB * mFoilM + CHROMA_PEARL_KRGB * mPearl;
        float  lookFloor   = CHROMA_METAL_FLOOR * m0 + CHROMA_OIL_FLOOR * mOil + CHROMA_SOAP_FLOOR * mSoap
                           + CHROMA_FOILM_FLOOR * mFoilM + CHROMA_PEARL_FLOOR * mPearl;
        float  lookWashout = CHROMA_WASHOUT * m0 + CHROMA_OIL_WASHOUT * mOil + CHROMA_SOAP_WASHOUT * mSoap
                           + CHROMA_FOILM_WASHOUT * mFoilM + CHROMA_PEARL_WASHOUT * mPearl;

        // Iridescent thin-film (Newton's rings). opd = thickness/cos(refr).
        //   thick = bevel depth cT (center 0 -> rim 1); cosT = dot(cN,cL) (light-reactive, gyro).
        //   1e-2 guards grazing-angle blow-up. THICK_MIX blends thickness rings vs light angle.
        float cosT     = clamp(dot(cN, cL), 0.0, 1.0);
        float thick    = 1.0 - cNcos;
        float ringTerm = thick / max(1.0 - 0.6 * cosT, 1.0e-2);
        float opdDrive = mix(cosT, ringTerm, CHROMA_THICK_MIX);
        float opd      = opdDrive * (chromaticCycles * lookGain) + CHROMA_OPD_BASE + chromaticPhase;
        // Per-channel constructive interference; 0.5+0.5cos oscillates about silver.
        float3 interf = 0.5 + 0.5 * cos(6.28318530718 * opd * lookKRGB);
        // Metal floor keeps channels off 0 (off-0 = metal, never neon).
        float3 metalRGB = lookFloor + (1.0 - lookFloor) * interf;
        // Higher-order wash-out toward clean silver (white ref, not luma -> avoids beige muddying).
        float  sat       = exp(-opd * lookWashout);
        float3 thinFilm  = mix(float3(1.0), metalRGB, clamp(sat, 0.0, 1.0));
        // Fresnel rim boost: brighten toward white at the rim (cT->1). MetallicFoil/Pearl only.
        float  rimSel    = mFoilM + mPearl;
        float  rimBoost  = rimSel * CHROMA_RIM_GAIN * pow(clamp(thick, 0.0, 1.0), CHROMA_RIM_POW);
        thinFilm         = mix(thinFilm, float3(1.0), clamp(rimBoost, 0.0, 1.0));
        // mode 1 -> Foil, all others -> thin-film. step-window is exactly 0/1 (no regression).
        float  isFoil    = step(0.5, cMode) * (1.0 - step(1.5, cMode));
        float3 chromaRGB = mix(thinFilm, foilRGB, isFoil);

        // Focal-pool modulation: drive rainbow strength by the raw pool^2 (normalized, pre-gain).
        float2 cFocal  = cLightVec * (cMinHalf * specFocalK);
        float  cPoolR  = max(cMinHalf * specPoolFrac, 1.0);          // guard zero-width smoothstep
        float  cPool   = 1.0 - smoothstep(0.0, cPoolR, length(p - cFocal)); // edge0<edge1
        float  poolNorm = clamp(cPool * cPool, 0.0, 1.0);
        // effMod = focal-pool follow strength. mode 0/1 keep the binding value (bit-exact);
        // named looks raise it per-look. No new uniform (reuses cPool).
        float  baseMod = m0 + isFoil;                               // mode 0 or 1 -> 1.0
        float  effMod  = chromaticModulate * baseMod
                       + CHROMA_OIL_MOD * mOil + CHROMA_SOAP_MOD * mSoap
                       + CHROMA_FOILM_MOD * mFoilM + CHROMA_PEARL_MOD * mPearl;
        float  chroma  = chromaticIntensity * mix(1.0, poolNorm, clamp(effMod, 0.0, 1.0));

        // Two blends interpolated by content alpha:
        //   transparent base (a->0, white card): multiply (white*chromaRGB = pure rainbow).
        //     screen would erase it on white (1-(1-1)*..=1), so multiply is correct.
        //   opaque base (a->1, photo): screen glow (never darkens). multiply would dim/mud
        //     colored pixels since one chromaRGB channel is ~0.
        half  cChroma  = half(clamp(chroma, 0.0, 1.0));
        half3 cChromaRGB = half3(chromaRGB) * cChroma;
        half3 cOnWhite = half3(chromaRGB);
        half3 cOnSrc   = half3(1.0) - (half3(1.0) - pixel.rgb) * (half3(1.0) - cChromaRGB); // SCREEN
        pixel.rgb = mix(cOnWhite, cOnSrc, pixel.a);
        pixel.a   = max(pixel.a, cChroma);                       // keep overlay visible over transparent bg
    }

    // Anti-aliased edge transition
    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""
}
