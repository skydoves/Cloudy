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
 * declarations. Each body is paired with its [MirageParams] subclass and registered as an
 * [Optic][com.skydoves.cloudy.Optic] in [MirageOptics][com.skydoves.cloudy.MirageOptics].
 *
 * ## Uniforms are declared by the paired params, not inline
 * A Composite / Generate body names its uniforms (`lensCenter`, `iLight`, `spec*`, `chromatic*`,
 * `foil*`, …) but does **not** declare them: the compiler emits one declaration per schema entry from
 * the paired params, so an inline `uniform ...;` would collide and fail to compile. The property names
 * of the params subclass therefore *are* the uniform identifiers, and they match the identifiers these
 * bodies read. The one non-uniform rename applied here is `iTime` -> the standard `mirageTime` uniform,
 * so the codegen clock drives the foil shimmer.
 *
 * AGSL and SKSL bodies are kept byte-identical (the two languages share this surface).
 */

/**
 * AGSL Colorize kernel for the Duotone demo optic. Point-wise: it reads only the passed `src` pixel
 * (never the content sampler), maps luminance onto a shadow -> highlight gradient, and cross-fades by
 * `amount`. The compiler wraps this `kernel(...)` with
 * `half4 main(float2 xy){ return kernel(xy, content.eval(xy)); }`. `shadow` / `highlight` / `amount`
 * are supplied by the paired MirageParams (uniformColor / uniform).
 */
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

/**
 * AGSL Composite `main` body for the specular optic. Reads the standard lens uniforms + preamble
 * helpers, and its 11 `spec*` terms.
 *
 * The `spec*` / lens / `iLight` uniforms are NOT declared here: they are the property names of the
 * paired `SpecularParams`, so the compiler emits their declarations from that schema. Declaring them
 * inline as well would be a duplicate-uniform compile error.
 */
internal const val SPECULAR_KERNEL_AGSL: String = """
// Superellipse power for the bevel field (see the highlight block): its |q|^4 iso-contours are
// smooth rounded rects with no diagonal ridge, unlike the box SDF depth whose ridges lie on the
// diagonals and stack into a corner-to-corner X at high strength/full-bleed.
const float SPEC_SE_POW = 4.0;

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

        // --- superellipse in-plane direction (specular only; the refraction 'normal' is unchanged) ---
        // The refraction path (above) keeps using the hard-pick 'normal'. Only here do we build a
        // separate continuous direction. The box SDF depth field ridges on the diagonals,
        // so its bevel rings stack into a corner-to-corner X at high strength/full-bleed; a superellipse
        // (power-4) normalized distance has smooth rounded-rect iso-contours with no diagonal ridge, and
        // its gradient is the bevel direction. Power 4 on |q|<=~1.4 is fp-safe; the 1e-4 bias keeps
        // normalize() finite at the exact (sub-pixel, invisible) center where the gradient vanishes.
        float minHalf = min(halfDim.x, halfDim.y);
        float2 q  = abs(p) / max(halfDim, float2(1.0));
        float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float seF = pow(pow(q.x, SPEC_SE_POW) + pow(q.y, SPEC_SE_POW), 1.0 / SPEC_SE_POW);
        float2 specDir2 = normalize(
            s2 * float2(SPEC_SE_POW * pow(q.x, SPEC_SE_POW - 1.0) / max(halfDim.x, 1.0),
                        SPEC_SE_POW * pow(q.y, SPEC_SE_POW - 1.0) / max(halfDim.y, 1.0))
            + float2(1.0e-4, 1.0e-4));

        // --- fake-3D surface normal from the superellipse bevel ---
        // specDomeFrac scales the bevel band: t reaches 1 (flat, in-plane) at fraction specDomeFrac of
        // the way to the rim, matching the box construction's `depthIn / (minHalf*specDomeFrac)` slope.
        float t       = clamp(seF / max(specDomeFrac, 1.0e-2), 0.0, 1.0);
        float n_cos   = 1.0 - t;                                  // in-plane magnitude
        float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));      // float: catastrophic cancellation near n_cos~1
        float3 N      = normalize(float3(specDir2 * n_cos, n_sin + 1.0e-3));

        float3 L = normalize(float3(lightVec, specLightZ));
        float3 V = float3(0.0, 0.0, 1.0);

        // --- MOVING FOCAL HOTSPOT — "light pours across the face on both axes" ---
        // Shift the hotspot along lightVec by minHalf*specFocalK: pitch(lightVec.y)=vertical,
        // roll(lightVec.x)=horizontal move -> a moving bright pool (both axes). It follows the light
        // strongly across the face; the inside mask fades it outside/at the rim.
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
// Superellipse power for the bevel field (see the highlight block): its |q|^4 iso-contours are
// smooth rounded rects with no diagonal ridge, unlike the box SDF depth whose ridges lie on the
// diagonals and stack into a corner-to-corner X at high strength/full-bleed.
const float SPEC_SE_POW = 4.0;

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

        // --- superellipse in-plane direction (specular only; the refraction 'normal' is unchanged) ---
        // The refraction path (above) keeps using the hard-pick 'normal'. Only here do we build a
        // separate continuous direction. The box SDF depth field ridges on the diagonals,
        // so its bevel rings stack into a corner-to-corner X at high strength/full-bleed; a superellipse
        // (power-4) normalized distance has smooth rounded-rect iso-contours with no diagonal ridge, and
        // its gradient is the bevel direction. Power 4 on |q|<=~1.4 is fp-safe; the 1e-4 bias keeps
        // normalize() finite at the exact (sub-pixel, invisible) center where the gradient vanishes.
        float minHalf = min(halfDim.x, halfDim.y);
        float2 q  = abs(p) / max(halfDim, float2(1.0));
        float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
        float seF = pow(pow(q.x, SPEC_SE_POW) + pow(q.y, SPEC_SE_POW), 1.0 / SPEC_SE_POW);
        float2 specDir2 = normalize(
            s2 * float2(SPEC_SE_POW * pow(q.x, SPEC_SE_POW - 1.0) / max(halfDim.x, 1.0),
                        SPEC_SE_POW * pow(q.y, SPEC_SE_POW - 1.0) / max(halfDim.y, 1.0))
            + float2(1.0e-4, 1.0e-4));

        // --- fake-3D surface normal from the superellipse bevel ---
        // specDomeFrac scales the bevel band: t reaches 1 (flat, in-plane) at fraction specDomeFrac of
        // the way to the rim, matching the box construction's `depthIn / (minHalf*specDomeFrac)` slope.
        float t       = clamp(seF / max(specDomeFrac, 1.0e-2), 0.0, 1.0);
        float n_cos   = 1.0 - t;                                  // in-plane magnitude
        float n_sin   = sqrt(max(1.0 - n_cos * n_cos, 0.0));      // float: catastrophic cancellation near n_cos~1
        float3 N      = normalize(float3(specDir2 * n_cos, n_sin + 1.0e-3));

        float3 L = normalize(float3(lightVec, specLightZ));
        float3 V = float3(0.0, 0.0, 1.0);

        // --- MOVING FOCAL HOTSPOT — "light pours across the face on both axes" ---
        // Shift the hotspot along lightVec by minHalf*specFocalK: pitch(lightVec.y)=vertical,
        // roll(lightVec.x)=horizontal move -> a moving bright pool (both axes). It follows the light
        // strongly across the face; the inside mask fades it outside/at the rim.
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

/**
 * AGSL Composite `main` body for the chromatic optic - parameterized thin-film iridescence. The 7
 * `chromatic*` / lens / `iLight` uniforms are declared by the paired `ChromaticParams` schema, so they
 * are NOT declared inline (a duplicate would fail to compile); the `CHROMA_*` consts stay in the body
 * since they are shader-private, not uniforms.
 */
internal const val CHROMATIC_KERNEL_AGSL: String = """
// thin-film: thickness = bevel depth (center 0 -> rim 1), cos = light incidence; THICK_MIX blends
// thickness rings vs pure light angle. OPD_BASE seats the silver 0th order at the center.
const float CHROMA_OPD_BASE  = 0.10;
const float CHROMA_THICK_MIX = 0.55;
const float CHROMA_RIM_POW   = 3.0;
// Bevel depth is driven by a superellipse (power-4) normalized distance, NOT the box SDF: the box
// SDF depth (= distance to the nearest edge) has ridge lines exactly on the diagonals, so its rings
// stack into a corner-to-corner X at high gain/full-bleed. |q|^4 iso-contours are smooth rounded
// rectangles with no diagonal ridge, and their gradient is the bevel direction. The box SDF still
// owns the lens MASK (early-out + alpha) so the cut shape is unchanged. Power 4 on |q|<=~1.4 is fp-safe.
const float CHROMA_SE_POW    = 4.0;

half4 main(float2 xy) {
    float2 halfDim = lensSize * 0.5;
    float r = min(cornerRadius, min(halfDim.x, halfDim.y));

    float2 p = xy - lensCenter;
    float sdf = boxRoundedSDF(p, halfDim, r);

    if (sdf > SMOOTH_EDGE_PX) {
        return content.eval(xy);
    }

    half4 pixel = content.eval(xy);

    // --- bevel field from a superellipse (smooth, no diagonal ridge); mask stays box-SDF ---
    float minHalf = min(halfDim.x, halfDim.y);
    float2 cLightVec = normalize(iLight);
    // f: superellipse normalized distance (0 at center, 1 at the axis rim); its iso-contours are the
    // rounded-rect thin-film rings. cDir: normalized gradient of f = the outward bevel direction. The
    // gradient vanishes only at the exact center; the 1e-4 bias keeps normalize() finite there (a
    // sub-pixel point, invisible). s2 restores the sign the abs() folded away.
    float2 q  = abs(p) / max(halfDim, float2(1.0));
    float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
    float f  = pow(pow(q.x, CHROMA_SE_POW) + pow(q.y, CHROMA_SE_POW), 1.0 / CHROMA_SE_POW);
    float2 cDir = normalize(
        s2 * float2(CHROMA_SE_POW * pow(q.x, CHROMA_SE_POW - 1.0) / max(halfDim.x, 1.0),
                    CHROMA_SE_POW * pow(q.y, CHROMA_SE_POW - 1.0) / max(halfDim.y, 1.0))
        + float2(1.0e-4, 1.0e-4));
    float t       = clamp(f, 0.0, 1.0);
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
    // Blend done in half space: cOnWhite multiply on a white card = pure rainbow (screen would erase
    // it), cOnSrc screen glow over an opaque photo (multiply would darken it).
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
// thin-film: thickness = bevel depth (center 0 -> rim 1), cos = light incidence; THICK_MIX blends
// thickness rings vs pure light angle. OPD_BASE seats the silver 0th order at the center.
const float CHROMA_OPD_BASE  = 0.10;
const float CHROMA_THICK_MIX = 0.55;
const float CHROMA_RIM_POW   = 3.0;
// Bevel depth is driven by a superellipse (power-4) normalized distance, NOT the box SDF: the box
// SDF depth (= distance to the nearest edge) has ridge lines exactly on the diagonals, so its rings
// stack into a corner-to-corner X at high gain/full-bleed. |q|^4 iso-contours are smooth rounded
// rectangles with no diagonal ridge, and their gradient is the bevel direction. The box SDF still
// owns the lens MASK (early-out + alpha) so the cut shape is unchanged. Power 4 on |q|<=~1.4 is fp-safe.
const float CHROMA_SE_POW    = 4.0;

half4 main(float2 xy) {
    float2 halfDim = lensSize * 0.5;
    float r = min(cornerRadius, min(halfDim.x, halfDim.y));

    float2 p = xy - lensCenter;
    float sdf = boxRoundedSDF(p, halfDim, r);

    if (sdf > SMOOTH_EDGE_PX) {
        return content.eval(xy);
    }

    half4 pixel = content.eval(xy);

    // --- bevel field from a superellipse (smooth, no diagonal ridge); mask stays box-SDF ---
    float minHalf = min(halfDim.x, halfDim.y);
    float2 cLightVec = normalize(iLight);
    // f: superellipse normalized distance (0 at center, 1 at the axis rim); its iso-contours are the
    // rounded-rect thin-film rings. cDir: normalized gradient of f = the outward bevel direction. The
    // gradient vanishes only at the exact center; the 1e-4 bias keeps normalize() finite there (a
    // sub-pixel point, invisible). s2 restores the sign the abs() folded away.
    float2 q  = abs(p) / max(halfDim, float2(1.0));
    float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
    float f  = pow(pow(q.x, CHROMA_SE_POW) + pow(q.y, CHROMA_SE_POW), 1.0 / CHROMA_SE_POW);
    float2 cDir = normalize(
        s2 * float2(CHROMA_SE_POW * pow(q.x, CHROMA_SE_POW - 1.0) / max(halfDim.x, 1.0),
                    CHROMA_SE_POW * pow(q.y, CHROMA_SE_POW - 1.0) / max(halfDim.y, 1.0))
        + float2(1.0e-4, 1.0e-4));
    float t       = clamp(f, 0.0, 1.0);
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
    // Blend done in half space: cOnWhite multiply on a white card = pure rainbow (screen would erase
    // it), cOnSrc screen glow over an opaque photo (multiply would darken it).
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

/**
 * AGSL Generate `main` body for the foil overlay optic. Content-free overlay: it never samples
 * content. The 5 foil/sparkle + lens + `iLight` uniforms come from the paired `FoilParams` schema (not
 * declared inline). It reads the standard `mirageTime` uniform so the codegen clock drives the
 * animated shimmer.
 */
internal const val FOIL_KERNEL_AGSL: String = """
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
    float hueF = fract(along * foilBands + foilPhase + 0.05 * mirageTime); // slow flow with time
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
    float  twinkle = 0.5 + 0.5 * sin(6.2831853 * (h + 0.3 * mirageTime)); // per-cell time shimmer
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
    float hueF = fract(along * foilBands + foilPhase + 0.05 * mirageTime); // slow flow with time
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
    float  twinkle = 0.5 + 0.5 * sin(6.2831853 * (h + 0.3 * mirageTime)); // per-cell time shimmer
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
