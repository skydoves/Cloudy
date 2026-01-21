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
uniform shader content;

const float SMOOTH_EDGE_PX = 1.5;

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

    // Specular rim highlight
    if (edge > 0.0) {
        float rimBlend = smoothstep(-edge * 10.0, 0.0, sdf);
        float2 lightVec = normalize(float2(-1.0, -1.0));
        float specular = abs(dot(normal, lightVec));
        pixel.rgb += half3(rimBlend * specular * edge);
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
uniform shader content;

const float SMOOTH_EDGE_PX = 1.5;

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

    // Specular rim highlight
    if (edge > 0.0) {
        float rimBlend = smoothstep(-edge * 10.0, 0.0, sdf);
        float2 lightVec = normalize(float2(-1.0, -1.0));
        float specular = abs(dot(normal, lightVec));
        pixel.rgb += half3(rimBlend * specular * edge);
    }

    // Anti-aliased edge transition
    float alpha = 1.0 - smoothstep(-SMOOTH_EDGE_PX * 0.5, SMOOTH_EDGE_PX * 0.5, sdf);
    half4 bg = content.eval(xy);
    return mix(bg, pixel, alpha);
}
"""
}
