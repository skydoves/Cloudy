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

/*
 * Shared shader preamble text the [MirageCompiler] prepends before a codegen kernel body.
 *
 * The preamble owns the lens geometry/color helpers (`boxRoundedSDF`, `lensNormalDirection`,
 * `toBrightness`, `processColor`) and the shared edge consts a [Composite][OpticCategory.Composite]
 * or [Generate][OpticCategory.Generate] kernel may call. It declares **no** `main` and **no**
 * standard uniforms — the compiler emits the standard-uniform declarations on demand (only the ones
 * a kernel actually references), and the kernel owns the single entry point.
 *
 * [Colorize][OpticCategory.Colorize] kernels are point-wise (`kernel(float2 p, half4 src)`) and do
 * not read the lens field, so they receive no preamble at all — including it would only add dead
 * helpers.
 *
 * The helper text is a verbatim carry-over of the `PREAMBLE_HELPERS_*` consts (byte-for-byte, no
 * rewrite): the geometry/color formulas are a bit-exact clone of `LiquidGlassShaderSource.kt`, and
 * the compiler pipeline must preserve them unchanged. AGSL and SKSL are byte-identical here (the two
 * languages share this surface), kept as two consts to leave room for a future divergence without a
 * code change.
 */

/** AGSL lens helpers + shared consts a Composite / Generate kernel may call. */
internal const val MIRAGE_PREAMBLE_HELPERS_AGSL: String = """
const float SMOOTH_EDGE_PX = 1.5;
const float SEAM_BLEND_PX = 24.0;  // diagonal-seam blend half-width (px); larger = softer interior crease

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

/** SKSL lens helpers — byte-identical to [MIRAGE_PREAMBLE_HELPERS_AGSL] (AGSL/SKSL parity). */
internal const val MIRAGE_PREAMBLE_HELPERS_SKSL: String = """
const float SMOOTH_EDGE_PX = 1.5;
const float SEAM_BLEND_PX = 24.0;  // diagonal-seam blend half-width (px); larger = softer interior crease

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

/** Returns the lens-helper preamble for [dialect]. */
internal fun miragePreambleHelpers(dialect: Dialect): String = when (dialect) {
  Dialect.Agsl -> MIRAGE_PREAMBLE_HELPERS_AGSL
  Dialect.Sksl -> MIRAGE_PREAMBLE_HELPERS_SKSL
}
