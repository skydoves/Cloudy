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
package com.skydoves.cloudy.internal

/**
 * Kernel bodies the [MirageCompiler] splices onto the [MiragePreamble] and generated uniform
 * declarations. This is the successor of the top-level `MirageShaders.kt` recipe consts: the
 * specular / chromatic / foil bodies are carried here verbatim so the preset-porting step (a later
 * milestone) can pair each with its [MirageParams] subclass and register it as an
 * [Optic][com.skydoves.cloudy.Optic].
 *
 * ## Why the uniform names are NOT renamed here
 * The ported bodies still name the historical uniforms (`iLight`, `chromaticGain`, `spec*`, …). The
 * final naming is decided where a kernel is paired with its params, because the property names of
 * that [MirageParams] subclass *become* the uniform identifiers the compiler emits — renaming the
 * kernel text without an authored params schema would reference names no schema provides. So the
 * rename (`iLight` -> `light`, `chromaticGain` -> `gain`, …) is deferred to the preset-porting step,
 * keeping this step a pure text move. `MirageShaders.kt` stays alongside this file until that step
 * consumes it, then it is deleted.
 *
 * AGSL and SKSL bodies are kept byte-identical (the two languages share this surface), following the
 * existing `MirageShaders.kt` convention.
 */

// ---------------------------------------------------------------------------------------------
// DUOTONE — a new Colorize demo kernel. Point-wise: it reads only the passed `src` pixel (never the
// content sampler), maps luminance onto a shadow -> highlight gradient, and cross-fades by `amount`.
// The compiler wraps this `kernel(...)` with `half4 main(float2 xy){ return kernel(xy, content.eval(xy)); }`.
// `shadow` / `highlight` / `amount` are supplied by the paired MirageParams (uniformColor / uniform).
// ---------------------------------------------------------------------------------------------

/** AGSL Colorize kernel for the Duotone demo optic. */
internal const val DUOTONE_KERNEL_AGSL: String = """
half4 kernel(float2 p, half4 src) {
    half g = half(dot(src.rgb, half3(0.2126, 0.7152, 0.0722)));
    half3 dz = mix(half3(shadow.rgb), half3(highlight.rgb), g);
    return half4(mix(src.rgb, dz, half(amount)), src.a);
}
"""

/** SKSL Colorize kernel — byte-identical to [DUOTONE_KERNEL_AGSL] (AGSL/SKSL parity). */
internal const val DUOTONE_KERNEL_SKSL: String = """
half4 kernel(float2 p, half4 src) {
    half g = half(dot(src.rgb, half3(0.2126, 0.7152, 0.0722)));
    half3 dz = mix(half3(shadow.rgb), half3(highlight.rgb), g);
    return half4(mix(src.rgb, dz, half(amount)), src.a);
}
"""

// ---------------------------------------------------------------------------------------------
// SPECULAR — Composite main() carried verbatim from MirageShaders.kt SPECULAR_*. Self-declares its
// 11 spec* uniforms and reads the standard lens uniforms + preamble helpers. The preset-porting step
// will strip the inline `uniform ...;` lines (the params schema will declare them) and apply the
// standard-uniform renames; here it is an unchanged text move.
// ---------------------------------------------------------------------------------------------

/** AGSL Composite `main` body for the specular optic. */
internal const val SPECULAR_KERNEL_AGSL: String = """
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
    // The gate checks specStrength too, so NoGlow (specStrength==0) is ALU 0 + bit-exact off (F).
    // specStrength is always set by both bindings, so the gate never blocks a required uniform write.
    // specRimMix:   0 = pure body (focal pool), 1 = pure rim glint (crossfade)
    // specPower:    rim/back lobe sharpness (Blinn)
    // specWidthPx:  rim band thickness, decoupled from `edge`
    // specStrength: peak highlight (screen-blended; <= 1.0)
    // specLightZ/specDomeFrac/specBodyPower/specBodyGain: fake-3D bevel lighting
    // specFocalK/specPoolFrac/specPoolGain: moving hotspot that flows across the face (dual-axis)
    if (edge > 0.0 && specStrength > 0.0) {
        float2 lightVec = normalize(iLight);

        // --- seam-free in-plane direction (specular only; the 'normal' the refraction reads is unchanged) ---
        // The refraction path (above) keeps using the hard-pick 'normal' (no regression, G1).
        // Only here do we build a separate continuous direction that removes the d.x==d.y diagonal crease.
        // A rounded rect interior is an L-inf field, so the true gradient breaks on the diagonal -> blend with an 8px softmax.
        float2 d2 = abs(p) - halfDim + float2(r);                 // same basis as boxRoundedSDF
        float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float2 specDir2;
        if (max(d2.x, d2.y) > 0.0) {
            specDir2 = s2 * normalize(max(d2, 0.0));              // outside/corner: analytic
        } else {
            // interior: w->1 x-dominant, ->0 y-dominant, =0.5 diagonal seam. +1e-4 = dead-center normalize singularity guard.
            float w  = clamp(0.5 + 0.5 * (d2.x - d2.y) / SEAM_BLEND_PX, 0.0, 1.0);
            float2 v = float2(s2.x * w, s2.y * (1.0 - w)) + float2(0.0, 1.0e-4);
            specDir2 = normalize(v);
        }

        // --- fake-3D surface normal from the rounded-rect bevel ---
        float minHalf = min(halfDim.x, halfDim.y);
        float bevelPx = max(minHalf * specDomeFrac, 1.0);
        float depthIn = max(-sdf, 0.0);
        float t       = clamp(depthIn / bevelPx, 0.0, 1.0);
        float n_cos   = 1.0 - t;                                  // in-plane magnitude
        float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));      // float: catastrophic cancellation near n_cos~1
        float3 N      = normalize(float3(specDir2 * n_cos, n_sin + 1.0e-3));

        float3 L = normalize(float3(lightVec, specLightZ));
        float3 V = float3(0.0, 0.0, 1.0);

        // --- MOVING FOCAL HOTSPOT — "light pours across the face on both axes" ---
        // Shift the hotspot along lightVec by minHalf*specFocalK: pitch(lightVec.y)=vertical,
        // roll(lightVec.x)=horizontal move -> a moving bright pool (both axes). Unlike the old body
        // sheen that collapsed to N~+Z, it follows the light strongly across the face; the inside mask fades it outside/at the rim.
        float2 focal     = lightVec * (minHalf * specFocalK);     // offset from lensCenter (=origin)
        float  poolR     = max(minHalf * specPoolFrac, 1.0);      // 0 guard (avoid zero-width smoothstep)
        float  poolD     = length(p - focal);
        float  pool      = 1.0 - smoothstep(0.0, poolR, poolD);   // 1 at focal, 0 at rim (edge0<edge1)
        float  inside    = 1.0 - smoothstep(-6.0, 0.0, sdf);      // lens interior only (fades outside/at the boundary; edge0<edge1)
        float  focalPool = pool * pool * specStrength * specPoolGain * inside; // pool^2 = harder core

        // --- broad body sheen (gentle modeling fill, added on top of the hotspot) ---
        float ndl       = max(dot(N, L), 0.0);
        float bodySheen = pow(ndl, specBodyPower) * specStrength * specBodyGain; // float: pow bands in fp16

        // --- tight Blinn rim glint, limited to the rim band ---
        // specWidthPx==0 makes a zero-width smoothstep = implementation-defined hard step -> max(...,1.0) guard (G6).
        float3 H       = normalize(L + V);
        float  rimBand = smoothstep(-max(specWidthPx, 1.0), 0.0, sdf);
        float  glint   = pow(max(dot(N, H), 0.0), specPower) * specStrength;     // float: pow bands in fp16
        float  rim     = glint * rimBand;

        // --- back-rim fill (opposite light source, rim-locked, 1/4 weight) ---
        float3 Lb   = normalize(float3(-lightVec, specLightZ));
        float  back  = pow(max(dot(N, Lb), 0.0), specPower) * specStrength * rimBand * 0.25; // float: pow

        // --- ordered dither: break the 8-bit Mach banding of the broad body ramp ---
        // bound the lens-local coords with fract before sin -> avoids sin-argument blowup/stripe collapse on large lenses (G5).
        // multiplied by specStrength so it is exactly 0 when off (F).
        float2 hp = fract((p / minHalf) * 0.5 + 0.5);             // bounded ~[0,1)
        float  dn = fract(sin(dot(hp, float2(12.9898, 78.233))) * 43758.5453) - 0.5;

        // --- linear crossfade body(moving hotspot + sheen + dither) <-> rim (monotonic, pure endpoint) ---
        // rimMix=0 -> pure body (focal pool); rimMix=1 -> pure rim glint (the legacy look).
        float body      = focalPool + bodySheen + dn * (1.0 / 255.0) * specStrength;
        float rimMix    = clamp(specRimMix, 0.0, 1.0);
        float highlight = body * (1.0 - rimMix) + (rim + back) * rimMix;

        // Screen blend: survives bright backgrounds; clamp to [0,1] -> cannot exceed 1.0 (preserves the screen-blend invariant).
        pixel.rgb += half3((1.0 - pixel.rgb) * clamp(highlight, 0.0, 1.0));
    }

    // Anti-aliased edge transition
    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""

/** SKSL Composite `main` body for the specular optic — byte-identical to [SPECULAR_KERNEL_AGSL]. */
internal const val SPECULAR_KERNEL_SKSL: String = """
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
    // The gate checks specStrength too, so NoGlow (specStrength==0) is ALU 0 + bit-exact off (F).
    // specStrength is always set by both bindings, so the gate never blocks a required uniform write.
    // specRimMix:   0 = pure body (focal pool), 1 = pure rim glint (crossfade)
    // specPower:    rim/back lobe sharpness (Blinn)
    // specWidthPx:  rim band thickness, decoupled from `edge`
    // specStrength: peak highlight (screen-blended; <= 1.0)
    // specLightZ/specDomeFrac/specBodyPower/specBodyGain: fake-3D bevel lighting
    // specFocalK/specPoolFrac/specPoolGain: moving hotspot that flows across the face (dual-axis)
    if (edge > 0.0 && specStrength > 0.0) {
        float2 lightVec = normalize(iLight);

        // --- seam-free in-plane direction (specular only; the 'normal' the refraction reads is unchanged) ---
        // The refraction path (above) keeps using the hard-pick 'normal' (no regression, G1).
        // Only here do we build a separate continuous direction that removes the d.x==d.y diagonal crease.
        // A rounded rect interior is an L-inf field, so the true gradient breaks on the diagonal -> blend with an 8px softmax.
        float2 d2 = abs(p) - halfDim + float2(r);                 // same basis as boxRoundedSDF
        float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float2 specDir2;
        if (max(d2.x, d2.y) > 0.0) {
            specDir2 = s2 * normalize(max(d2, 0.0));              // outside/corner: analytic
        } else {
            // interior: w->1 x-dominant, ->0 y-dominant, =0.5 diagonal seam. +1e-4 = dead-center normalize singularity guard.
            float w  = clamp(0.5 + 0.5 * (d2.x - d2.y) / SEAM_BLEND_PX, 0.0, 1.0);
            float2 v = float2(s2.x * w, s2.y * (1.0 - w)) + float2(0.0, 1.0e-4);
            specDir2 = normalize(v);
        }

        // --- fake-3D surface normal from the rounded-rect bevel ---
        float minHalf = min(halfDim.x, halfDim.y);
        float bevelPx = max(minHalf * specDomeFrac, 1.0);
        float depthIn = max(-sdf, 0.0);
        float t       = clamp(depthIn / bevelPx, 0.0, 1.0);
        float n_cos   = 1.0 - t;                                  // in-plane magnitude
        float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));      // float: catastrophic cancellation near n_cos~1
        float3 N      = normalize(float3(specDir2 * n_cos, n_sin + 1.0e-3));

        float3 L = normalize(float3(lightVec, specLightZ));
        float3 V = float3(0.0, 0.0, 1.0);

        // --- MOVING FOCAL HOTSPOT — "light pours across the face on both axes" ---
        // Shift the hotspot along lightVec by minHalf*specFocalK: pitch(lightVec.y)=vertical,
        // roll(lightVec.x)=horizontal move -> a moving bright pool (both axes). Unlike the old body
        // sheen that collapsed to N~+Z, it follows the light strongly across the face; the inside mask fades it outside/at the rim.
        float2 focal     = lightVec * (minHalf * specFocalK);     // offset from lensCenter (=origin)
        float  poolR     = max(minHalf * specPoolFrac, 1.0);      // 0 guard (avoid zero-width smoothstep)
        float  poolD     = length(p - focal);
        float  pool      = 1.0 - smoothstep(0.0, poolR, poolD);   // 1 at focal, 0 at rim (edge0<edge1)
        float  inside    = 1.0 - smoothstep(-6.0, 0.0, sdf);      // lens interior only (fades outside/at the boundary; edge0<edge1)
        float  focalPool = pool * pool * specStrength * specPoolGain * inside; // pool^2 = harder core

        // --- broad body sheen (gentle modeling fill, added on top of the hotspot) ---
        float ndl       = max(dot(N, L), 0.0);
        float bodySheen = pow(ndl, specBodyPower) * specStrength * specBodyGain; // float: pow bands in fp16

        // --- tight Blinn rim glint, limited to the rim band ---
        // specWidthPx==0 makes a zero-width smoothstep = implementation-defined hard step -> max(...,1.0) guard (G6).
        float3 H       = normalize(L + V);
        float  rimBand = smoothstep(-max(specWidthPx, 1.0), 0.0, sdf);
        float  glint   = pow(max(dot(N, H), 0.0), specPower) * specStrength;     // float: pow bands in fp16
        float  rim     = glint * rimBand;

        // --- back-rim fill (opposite light source, rim-locked, 1/4 weight) ---
        float3 Lb   = normalize(float3(-lightVec, specLightZ));
        float  back  = pow(max(dot(N, Lb), 0.0), specPower) * specStrength * rimBand * 0.25; // float: pow

        // --- ordered dither: break the 8-bit Mach banding of the broad body ramp ---
        // bound the lens-local coords with fract before sin -> avoids sin-argument blowup/stripe collapse on large lenses (G5).
        // multiplied by specStrength so it is exactly 0 when off (F).
        float2 hp = fract((p / minHalf) * 0.5 + 0.5);             // bounded ~[0,1)
        float  dn = fract(sin(dot(hp, float2(12.9898, 78.233))) * 43758.5453) - 0.5;

        // --- linear crossfade body(moving hotspot + sheen + dither) <-> rim (monotonic, pure endpoint) ---
        // rimMix=0 -> pure body (focal pool); rimMix=1 -> pure rim glint (the legacy look).
        float body      = focalPool + bodySheen + dn * (1.0 / 255.0) * specStrength;
        float rimMix    = clamp(specRimMix, 0.0, 1.0);
        float highlight = body * (1.0 - rimMix) + (rim + back) * rimMix;

        // Screen blend: survives bright backgrounds; clamp to [0,1] -> cannot exceed 1.0 (preserves the screen-blend invariant).
        pixel.rgb += half3((1.0 - pixel.rgb) * clamp(highlight, 0.0, 1.0));
    }

    // Anti-aliased edge transition
    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""

// ---------------------------------------------------------------------------------------------
// CHROMATIC — Composite main() carried verbatim from MirageShaders.kt CHROMATIC_*. Parameterized
// thin-film iridescence; self-declares its 7 chromatic* uniforms. Carried unchanged (see the file
// note on deferred renaming).
// ---------------------------------------------------------------------------------------------

/** AGSL Composite `main` body for the chromatic optic. */
internal const val CHROMATIC_KERNEL_AGSL: String = """
uniform float chromaticIntensity;
uniform float chromaticGain;
uniform float4 chromaticKRGB;   // .xyz = r/g/b wavenumber ratios; float4 to match the 4-arg write
uniform float chromaticFloor;
uniform float chromaticWashout;
uniform float chromaticModulate;
uniform float chromaticRimBoost;

// thin-film: thickness = bevel depth (center 0 -> rim 1), cos = light incidence; THICK_MIX blends
// thickness rings vs pure light angle. OPD_BASE seats the silver 0th order at the center.
const float CHROMA_OPD_BASE  = 0.10;
const float CHROMA_THICK_MIX = 0.55;
const float CHROMA_RIM_POW   = 3.0;

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
    float minHalf = min(halfDim.x, halfDim.y);
    float2 cLightVec = normalize(iLight);
    float2 d2 = abs(p) - halfDim + float2(r);
    float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
    float2 cDir;
    if (max(d2.x, d2.y) > 0.0) {
        cDir = s2 * normalize(max(d2, 0.0));
    } else {
        float w = clamp(0.5 + 0.5 * (d2.x - d2.y) / SEAM_BLEND_PX, 0.0, 1.0);
        cDir = normalize(float2(s2.x * w, s2.y * (1.0 - w)) + float2(0.0, 1.0e-4));
    }
    float depthIn = max(-sdf, 0.0);
    float t       = clamp(depthIn / max(minHalf, 1.0), 0.0, 1.0);
    float n_cos   = 1.0 - t;
    float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));         // float: catastrophic cancel near 1
    float3 cN     = normalize(float3(cDir * n_cos, n_sin + 1.0e-3));
    float3 cL     = normalize(float3(cLightVec, 0.55));

    // --- thin-film Newton's rings (per-look params are uniforms, no mode step-mask) ---
    float cosT     = clamp(dot(cN, cL), 0.0, 1.0);
    float thick    = 1.0 - n_cos;
    float ringTerm = thick / max(1.0 - 0.6 * cosT, 1.0e-2);      // grazing-angle blow-up guard
    float opdDrive = mix(cosT, ringTerm, CHROMA_THICK_MIX);
    float opd      = opdDrive * chromaticGain + CHROMA_OPD_BASE;
    // Per-channel constructive interference about silver; kRGB from the uniform (4-arg .xyz).
    float3 interf  = 0.5 + 0.5 * cos(6.28318530718 * opd * chromaticKRGB.xyz);
    float3 metalRGB = chromaticFloor + (1.0 - chromaticFloor) * interf;
    float  sat      = exp(-opd * chromaticWashout);              // wash toward clean silver
    float3 thinFilm = mix(float3(1.0), metalRGB, clamp(sat, 0.0, 1.0));
    // Fresnel rim boost toward white at the rim (thick->1); gated by the uniform (0 = no rim).
    float  rimBoost = chromaticRimBoost * pow(clamp(thick, 0.0, 1.0), CHROMA_RIM_POW);
    float3 chromaRGB = mix(thinFilm, float3(1.0), clamp(rimBoost, 0.0, 1.0));

    // --- focal-pool modulation: drive rainbow strength by the raw pool^2 (mirrors specular) ---
    float2 cFocal  = cLightVec * (minHalf * 0.55);              // specFocalK default
    float  cPoolR  = max(minHalf * 0.7, 1.0);                   // specPoolFrac default; zero-width guard
    float  cPool   = 1.0 - smoothstep(0.0, cPoolR, length(p - cFocal)); // edge0<edge1
    float  poolNorm = clamp(cPool * cPool, 0.0, 1.0);
    float  chroma  = chromaticIntensity * mix(1.0, poolNorm, clamp(chromaticModulate, 0.0, 1.0));

    // --- alpha-branched blend (transparent -> multiply rainbow, opaque -> screen glow) ---
    // Blend done in half space (proven #124 idiom): cOnWhite multiply on a white card = pure rainbow
    // (screen would erase it), cOnSrc screen glow over an opaque photo (multiply would darken it).
    half  cChroma    = half(clamp(chroma, 0.0, 1.0));
    half3 cChromaRGB = half3(chromaRGB) * cChroma;
    half3 cOnWhite   = half3(chromaRGB);
    half3 cOnSrc     = half3(1.0) - (half3(1.0) - pixel.rgb) * (half3(1.0) - cChromaRGB); // SCREEN
    pixel.rgb = mix(cOnWhite, cOnSrc, pixel.a);
    pixel.a   = max(pixel.a, cChroma);                          // keep visible over transparent bg

    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""

/** SKSL Composite `main` body for the chromatic optic — byte-identical to [CHROMATIC_KERNEL_AGSL]. */
internal const val CHROMATIC_KERNEL_SKSL: String = """
uniform float chromaticIntensity;
uniform float chromaticGain;
uniform float4 chromaticKRGB;   // .xyz = r/g/b wavenumber ratios; float4 to match the 4-arg write
uniform float chromaticFloor;
uniform float chromaticWashout;
uniform float chromaticModulate;
uniform float chromaticRimBoost;

// thin-film: thickness = bevel depth (center 0 -> rim 1), cos = light incidence; THICK_MIX blends
// thickness rings vs pure light angle. OPD_BASE seats the silver 0th order at the center.
const float CHROMA_OPD_BASE  = 0.10;
const float CHROMA_THICK_MIX = 0.55;
const float CHROMA_RIM_POW   = 3.0;

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
    float minHalf = min(halfDim.x, halfDim.y);
    float2 cLightVec = normalize(iLight);
    float2 d2 = abs(p) - halfDim + float2(r);
    float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
    float2 cDir;
    if (max(d2.x, d2.y) > 0.0) {
        cDir = s2 * normalize(max(d2, 0.0));
    } else {
        float w = clamp(0.5 + 0.5 * (d2.x - d2.y) / SEAM_BLEND_PX, 0.0, 1.0);
        cDir = normalize(float2(s2.x * w, s2.y * (1.0 - w)) + float2(0.0, 1.0e-4));
    }
    float depthIn = max(-sdf, 0.0);
    float t       = clamp(depthIn / max(minHalf, 1.0), 0.0, 1.0);
    float n_cos   = 1.0 - t;
    float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));         // float: catastrophic cancel near 1
    float3 cN     = normalize(float3(cDir * n_cos, n_sin + 1.0e-3));
    float3 cL     = normalize(float3(cLightVec, 0.55));

    // --- thin-film Newton's rings (per-look params are uniforms, no mode step-mask) ---
    float cosT     = clamp(dot(cN, cL), 0.0, 1.0);
    float thick    = 1.0 - n_cos;
    float ringTerm = thick / max(1.0 - 0.6 * cosT, 1.0e-2);      // grazing-angle blow-up guard
    float opdDrive = mix(cosT, ringTerm, CHROMA_THICK_MIX);
    float opd      = opdDrive * chromaticGain + CHROMA_OPD_BASE;
    // Per-channel constructive interference about silver; kRGB from the uniform (4-arg .xyz).
    float3 interf  = 0.5 + 0.5 * cos(6.28318530718 * opd * chromaticKRGB.xyz);
    float3 metalRGB = chromaticFloor + (1.0 - chromaticFloor) * interf;
    float  sat      = exp(-opd * chromaticWashout);              // wash toward clean silver
    float3 thinFilm = mix(float3(1.0), metalRGB, clamp(sat, 0.0, 1.0));
    // Fresnel rim boost toward white at the rim (thick->1); gated by the uniform (0 = no rim).
    float  rimBoost = chromaticRimBoost * pow(clamp(thick, 0.0, 1.0), CHROMA_RIM_POW);
    float3 chromaRGB = mix(thinFilm, float3(1.0), clamp(rimBoost, 0.0, 1.0));

    // --- focal-pool modulation: drive rainbow strength by the raw pool^2 (mirrors specular) ---
    float2 cFocal  = cLightVec * (minHalf * 0.55);              // specFocalK default
    float  cPoolR  = max(minHalf * 0.7, 1.0);                   // specPoolFrac default; zero-width guard
    float  cPool   = 1.0 - smoothstep(0.0, cPoolR, length(p - cFocal)); // edge0<edge1
    float  poolNorm = clamp(cPool * cPool, 0.0, 1.0);
    float  chroma  = chromaticIntensity * mix(1.0, poolNorm, clamp(chromaticModulate, 0.0, 1.0));

    // --- alpha-branched blend (transparent -> multiply rainbow, opaque -> screen glow) ---
    // Blend done in half space (proven #124 idiom): cOnWhite multiply on a white card = pure rainbow
    // (screen would erase it), cOnSrc screen glow over an opaque photo (multiply would darken it).
    half  cChroma    = half(clamp(chroma, 0.0, 1.0));
    half3 cChromaRGB = half3(chromaRGB) * cChroma;
    half3 cOnWhite   = half3(chromaRGB);
    half3 cOnSrc     = half3(1.0) - (half3(1.0) - pixel.rgb) * (half3(1.0) - cChromaRGB); // SCREEN
    pixel.rgb = mix(cOnWhite, cOnSrc, pixel.a);
    pixel.a   = max(pixel.a, cChroma);                          // keep visible over transparent bg

    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""

// ---------------------------------------------------------------------------------------------
// FOIL — Generate main() carried verbatim from MirageShaders.kt FOIL_*. Content-free overlay: it
// never samples content and self-declares its 5 foil/sparkle uniforms. Carried unchanged.
// ---------------------------------------------------------------------------------------------

/** AGSL Generate `main` body for the foil overlay optic. */
internal const val FOIL_KERNEL_AGSL: String = """
uniform float foilBands;
uniform float foilPhase;
uniform float chromaticGain;
uniform float sparkleDensity;
uniform float sparkleAmplitude;

// hash for the sparkle field — bounded input (lens-local, fract) so sin() never blows up at scale.
float foilHash(float2 c) {
    return fract(sin(dot(c, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 xy) {
    float2 halfDim = lensSize * 0.5;
    float r = min(cornerRadius, min(halfDim.x, halfDim.y));

    float2 p = xy - lensCenter;
    float sdf = boxRoundedSDF(p, halfDim, r);

    // Pure overlay: outside the lens contributes nothing (transparent), composited via SrcOver.
    if (sdf > SMOOTH_EDGE_PX) {
        return half4(0.0);
    }

    float minHalf = min(halfDim.x, halfDim.y);
    float2 cLightVec = normalize(iLight);
    float2 pNorm = p / minHalf;
    float t = clamp(max(-sdf, 0.0) / max(minHalf, 1.0), 0.0, 1.0);   // bevel depth (0 center -> 1 rim)

    // --- 1. glare: directional highlight following the light, plus a soft radial dome ---
    float along = dot(pNorm, cLightVec);                             // -1..1 along light
    float glare = smoothstep(0.2, 1.0, along) * (1.0 - t);          // bright toward the lit edge
    float dome  = (1.0 - smoothstep(0.0, 1.0, length(pNorm))) * 0.5; // gentle center fill

    // --- 2. thin-film rainbow: HSV band along the light + a thin-film tint (reuses chromaticGain) ---
    float hueF = fract(along * foilBands + foilPhase + 0.05 * iTime); // slow flow with time
    float3 hsv = clamp(
        abs(fract(float3(hueF) + float3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0,
        0.0, 1.0);
    float opd = (0.5 + 0.5 * t) * chromaticGain;
    float3 film = 0.5 + 0.5 * cos(6.28318530718 * opd * float3(1.0, 1.18, 1.42));
    float3 rainbow = mix(hsv, film, 0.4);                            // band hue tinted by interference

    // --- 3. sparkle (AA): cell-quantized hash dots, each softened to >= 1px, conservative amp ---
    // Cell pitch in screen px = minHalf / sparkleDensity (>= ~2px at density 16, minHalf ~260).
    float2 cell = floor(pNorm * sparkleDensity);
    float  h    = foilHash(cell);
    float2 cellUv = fract(pNorm * sparkleDensity) - 0.5;            // -0.5..0.5 within the cell
    float  d    = length(cellUv);
    // AA edge WITHOUT fwidth(): AGSL fixes its feature set at GLSL ES 1.0, which has no derivative
    // functions (fwidth/dFdx fail to compile). One screen px is sparkleDensity/minHalf in cell-UV
    // units, so an analytic ~1px soft band gives the same crawl-free dot edge, no derivatives.
    float  aa   = clamp(sparkleDensity / max(minHalf, 1.0), 0.02, 0.25);
    float  dot0 = 1.0 - smoothstep(0.18 - aa, 0.18 + aa, d);        // soft round dot
    float  twinkle = 0.5 + 0.5 * sin(6.2831853 * (h + 0.3 * iTime)); // per-cell time shimmer
    float  spark = step(0.78, h) * dot0 * twinkle * sparkleAmplitude; // only the brightest cells

    // --- composite: rainbow tinted by glare/dome, plus additive sparkle, masked to the lens ---
    float lum = clamp(glare + dome, 0.0, 1.0);
    float3 rgb = rainbow * lum + float3(spark);
    float  a   = clamp(lum + spark, 0.0, 1.0);
    float  mask = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    // Premultiplied output (SrcOver brush): rgb scaled by coverage, alpha masked to the lens.
    return half4(half3(rgb) * half(mask), half(a * mask));
}
"""

/** SKSL Generate `main` body for the foil overlay optic — byte-identical to [FOIL_KERNEL_AGSL]. */
internal const val FOIL_KERNEL_SKSL: String = """
uniform float foilBands;
uniform float foilPhase;
uniform float chromaticGain;
uniform float sparkleDensity;
uniform float sparkleAmplitude;

// hash for the sparkle field — bounded input (lens-local, fract) so sin() never blows up at scale.
float foilHash(float2 c) {
    return fract(sin(dot(c, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 xy) {
    float2 halfDim = lensSize * 0.5;
    float r = min(cornerRadius, min(halfDim.x, halfDim.y));

    float2 p = xy - lensCenter;
    float sdf = boxRoundedSDF(p, halfDim, r);

    // Pure overlay: outside the lens contributes nothing (transparent), composited via SrcOver.
    if (sdf > SMOOTH_EDGE_PX) {
        return half4(0.0);
    }

    float minHalf = min(halfDim.x, halfDim.y);
    float2 cLightVec = normalize(iLight);
    float2 pNorm = p / minHalf;
    float t = clamp(max(-sdf, 0.0) / max(minHalf, 1.0), 0.0, 1.0);   // bevel depth (0 center -> 1 rim)

    // --- 1. glare: directional highlight following the light, plus a soft radial dome ---
    float along = dot(pNorm, cLightVec);                             // -1..1 along light
    float glare = smoothstep(0.2, 1.0, along) * (1.0 - t);          // bright toward the lit edge
    float dome  = (1.0 - smoothstep(0.0, 1.0, length(pNorm))) * 0.5; // gentle center fill

    // --- 2. thin-film rainbow: HSV band along the light + a thin-film tint (reuses chromaticGain) ---
    float hueF = fract(along * foilBands + foilPhase + 0.05 * iTime); // slow flow with time
    float3 hsv = clamp(
        abs(fract(float3(hueF) + float3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0,
        0.0, 1.0);
    float opd = (0.5 + 0.5 * t) * chromaticGain;
    float3 film = 0.5 + 0.5 * cos(6.28318530718 * opd * float3(1.0, 1.18, 1.42));
    float3 rainbow = mix(hsv, film, 0.4);                            // band hue tinted by interference

    // --- 3. sparkle (AA): cell-quantized hash dots, each softened to >= 1px, conservative amp ---
    // Cell pitch in screen px = minHalf / sparkleDensity (>= ~2px at density 16, minHalf ~260).
    float2 cell = floor(pNorm * sparkleDensity);
    float  h    = foilHash(cell);
    float2 cellUv = fract(pNorm * sparkleDensity) - 0.5;            // -0.5..0.5 within the cell
    float  d    = length(cellUv);
    // AA edge WITHOUT fwidth(): AGSL fixes its feature set at GLSL ES 1.0, which has no derivative
    // functions (fwidth/dFdx fail to compile). One screen px is sparkleDensity/minHalf in cell-UV
    // units, so an analytic ~1px soft band gives the same crawl-free dot edge, no derivatives.
    float  aa   = clamp(sparkleDensity / max(minHalf, 1.0), 0.02, 0.25);
    float  dot0 = 1.0 - smoothstep(0.18 - aa, 0.18 + aa, d);        // soft round dot
    float  twinkle = 0.5 + 0.5 * sin(6.2831853 * (h + 0.3 * iTime)); // per-cell time shimmer
    float  spark = step(0.78, h) * dot0 * twinkle * sparkleAmplitude; // only the brightest cells

    // --- composite: rainbow tinted by glare/dome, plus additive sparkle, masked to the lens ---
    float lum = clamp(glare + dome, 0.0, 1.0);
    float3 rgb = rainbow * lum + float3(spark);
    float  a   = clamp(lum + spark, 0.0, 1.0);
    float  mask = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    // Premultiplied output (SrcOver brush): rgb scaled by coverage, alpha masked to the lens.
    return half4(half3(rgb) * half(mask), half(a * mask));
}
"""
