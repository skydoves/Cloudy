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
 * Shader text building blocks for the open [Modifier.mirage] recipe mechanism.
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
const float SEAM_BLEND_PX = 8.0;   // diagonal-seam blend half-width (px); larger = softer interior crease

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
const float SEAM_BLEND_PX = 8.0;   // diagonal-seam blend half-width (px); larger = softer interior crease

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
// OVERLAY preamble — the content-free variant prepended to MirageInputMode.Overlay recipes.
//
// Identical to the standard preamble EXCEPT the `uniform shader content;` declaration is dropped.
// Why content-free (NOT "Skia throws on unset content"): `content` is a child *sampler*, not a value
// uniform — an unbound child does not throw (Android is undefined; Skia returns a null child =
// transparent black). The real reason to omit it is that an Overlay main is a pure generator: it
// never calls `content.eval`, so keeping the declaration would be a dead sampler. The lens helpers
// (boxRoundedSDF, lensNormalDirection) and consts stay so an Overlay can still mask to the lens.
// ---------------------------------------------------------------------------------------------

/** AGSL Overlay preamble layer 1: standard uniforms WITHOUT the `content` child sampler. */
internal const val PREAMBLE_OVERLAY_UNIFORMS_AGSL: String = """
uniform float2 iResolution;
uniform float2 lensCenter;
uniform float2 lensSize;
uniform float cornerRadius;
uniform float2 iLight;
uniform float iTime;
"""

/** Full AGSL Overlay preamble (content-free uniforms + the shared helpers). */
internal const val PREAMBLE_OVERLAY_AGSL: String =
  PREAMBLE_OVERLAY_UNIFORMS_AGSL + PREAMBLE_HELPERS_AGSL

/** SKSL Overlay preamble layer 1 — byte-identical to [PREAMBLE_OVERLAY_UNIFORMS_AGSL]. */
internal const val PREAMBLE_OVERLAY_UNIFORMS_SKSL: String = """
uniform float2 iResolution;
uniform float2 lensCenter;
uniform float2 lensSize;
uniform float cornerRadius;
uniform float2 iLight;
uniform float iTime;
"""

/** Full SKSL Overlay preamble (content-free uniforms + the shared helpers). */
internal const val PREAMBLE_OVERLAY_SKSL: String =
  PREAMBLE_OVERLAY_UNIFORMS_SKSL + PREAMBLE_HELPERS_SKSL

// ---------------------------------------------------------------------------------------------
// SPECULAR recipe — the bevel-lit glint of the liquid-glass shader, repackaged as a self-contained
// recipe main(). It re-computes its own lens setup (halfDim/r/p/sdf/normal/pixel — the values the
// built-in main() carries through local scope) so the body stands alone on top of the preamble.
//
// Renames vs LiquidGlassShaderSource.kt: lightDir -> iLight (the preamble's standard light uniform).
// `resolution` is renamed iResolution in the preamble but the specular block never reads it, so the
// body never names it. The 11 spec* uniforms are NOT standard, so they are self-declared here and
// fed by MirageRecipes.Specular's bindUniforms.
//
// The specular block below (the `// --- ...` sections from seam-free direction down to the screen
// blend) is a verbatim copy of LiquidGlassShaderSource.kt:164-234 with the single lightDir->iLight
// rename; the formula/constants/operation order are byte-for-byte the original (bit-exact gate).
// Color/refraction setup uses inline default literals (refraction 0.25, curve 0.25, dispersion 0,
// saturation 1, contrast 1, tint transparent) instead of uniforms, since a pure-specular recipe has
// no reason to vary them — keeping the binding's bindUniforms to exactly the 11 spec* values.
// fp16 note: pow/sin/broad ramps are kept `float` (the originals' fp16-banding guards are preserved).
// ---------------------------------------------------------------------------------------------

/** AGSL body for [MirageRecipes.Specular] — a complete `half4 main` on top of [PREAMBLE_AGSL]. */
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

/** SKSL body for [MirageRecipes.Specular] — byte-identical to [SPECULAR_AGSL] (AGSL/SKSL parity). */
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
// CHROMATIC recipe — thin-film (Newton's-rings) iridescence, now PARAMETERIZED so one shader body
// expresses every named look (Iridescent / OilSlick / SoapBubble / MetallicFoil / Pearl) purely
// through uniform values. The #124 built-in used a `chromaticMode` float-enum + a branchless
// step-mask to pick per-look CONSTANTS; here those constants become uniforms (chromaticGain,
// chromaticKRGB, chromaticFloor, chromaticWashout, chromaticModulate, chromaticRimBoost) and the
// step-mask / isFoil branch is GONE — each look just carries its own uniform set. This is the M2
// value-preserving proof: const→uniform reproduces the four #124 looks with no in-shader branching.
//
// Physics (unchanged from #124): OPD = thickness/cos(refraction), per-channel
// 0.5 + 0.5*cos(2π·opd·kRGB) about silver, a metal floor (off-0 = metal not neon), an exp() washout
// toward silver, optional Fresnel rim boost toward the rim, and a focal-pool modulation. The whole
// hue/interference chain stays `float` (AGSL `half`/fp16 bands hard on saturated rainbows). Blend is
// alpha-branched: transparent base → multiply (pure rainbow on white); opaque base → screen glow.
//
// Uniform contract (kotlin MirageRecipes factory sets these EXACT names):
//   chromaticIntensity float   overall strength, 0 = off
//   chromaticGain      float   Newton band count (OPD scale)
//   chromaticKRGB      float4   per-channel wavenumber ratios in .xyz (declared float4 because the
//                               MirageScope has only a 4-arg uniform overload; Skia/AGSL require the
//                               write arity to match the declared size exactly — a float3 fed 4
//                               floats throws "mismatch in byte size". The body reads only .xyz.
//   chromaticFloor     float   metal floor (lower = higher contrast)
//   chromaticWashout   float   higher-order wash-out rate toward silver
//   chromaticModulate  float   0..1 focal-pool follow strength
//   chromaticRimBoost  float   Fresnel rim gain (0 = off, ~0.45 = on for metallic/pearl)
// Defaults that reproduce the M1 single-Iridescent look: gain 3, kRGB (1,1.18,1.42), floor 0.12,
// washout 0.16, modulate 1, rimBoost 0.
// ---------------------------------------------------------------------------------------------

/** AGSL body for [MirageRecipes.Chromatic] — a complete `half4 main` on top of [PREAMBLE_AGSL]. */
internal const val CHROMATIC_AGSL: String = """
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

/** SKSL body for [MirageRecipes.Chromatic] — byte-identical to [CHROMATIC_AGSL] (AGSL/SKSL parity). */
internal const val CHROMATIC_SKSL: String = """
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
// FOIL recipe — a CONTENT-FREE Overlay main (built on PREAMBLE_OVERLAY_*, never calls content.eval).
// It is a pure generator: the binding draws it with ShaderBrush + SrcOver on top of the unmodified
// content, so it returns premultiplied highlight where the lens is and transparent (a=0) elsewhere.
//
// Three independent blocks composited, then masked to the lens SDF:
//   1. glare        — a directional/radial highlight that follows iLight across the face.
//   2. thin-film    — a flowing rainbow: HSV ramp from a band projected onto the light direction
//                      (foilBands/foilPhase), tinted by a thin-film term that reuses chromaticGain.
//   3. sparkle      — a hash dot-field, ANTI-ALIASED (see below) so it never shimmers/aliases.
//
// Sparkle AA (G — the bare-hash trap): a raw step(hash) dot-field aliases hard and crawls. This one
// is AA'd three ways: (a) feature size — the hash domain is scaled by minHalf so the cell pitch in
// screen px is >= ~2px; (b) each dot's edge is a smoothstep band whose width is computed analytically
// from the cell→screen-px ratio (sparkleDensity/minHalf), NOT fwidth() — AGSL has no derivative
// functions; (c) amplitude/density start conservative (0.3 / 16). iTime drives a gentle shimmer.
// Whole hue/interference chain is float (fp16 banding on saturated rainbows).
//
// Uniform contract (kotlin Foil factory sets these EXACT names):
//   foilBands        float   rainbow band count along the light direction
//   foilPhase        float   static hue phase offset (fract domain)
//   chromaticGain    float   thin-film OPD scale (reused name)
//   sparkleDensity   float   sparkle cell count (lower = bigger, safer features)
//   sparkleAmplitude float   sparkle peak add (conservative; high values crawl)
// ---------------------------------------------------------------------------------------------

/** AGSL body for [MirageRecipes.Foil] — a content-free `half4 main` on top of [PREAMBLE_OVERLAY_AGSL]. */
internal const val FOIL_AGSL: String = """
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

/** SKSL body for [MirageRecipes.Foil] — byte-identical to [FOIL_AGSL] (AGSL/SKSL parity). */
internal const val FOIL_SKSL: String = """
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
