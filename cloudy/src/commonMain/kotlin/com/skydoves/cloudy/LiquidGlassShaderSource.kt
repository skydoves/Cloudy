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
 * Shared shader source code for the Liquid Glass effect.
 *
 * This shader creates a realistic liquid glass lens effect with:
 * - SDF-based shape for crisp edges
 * - Normal-based refraction distortion
 * - Frosted glass blur effect
 * - Chromatic aberration (RGB channel separation)
 * - Edge lighting and anti-aliasing
 *
 * The shader is compatible with both AGSL (Android 13+) and SKSL (Skia platforms).
 */
public object LiquidGlassShaderSource {

  /**
   * AGSL shader source for Android API 33+.
   *
   * Uses `uniform shader content` for the input content.
   */
  public const val AGSL: String = """
uniform float2 resolution;
uniform float2 mouse;
uniform float2 lensSize;
uniform float cornerRadius;
uniform float refraction;
uniform float blur;
uniform float aberration;
uniform float saturation;
uniform float edgeBrightness;
uniform shader content;

const float AA_WIDTH_PX = 1.5;
const float BLUR_SAMPLES = 3.0;

// Signed distance function for rounded rectangle
float computeSdf(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

// Compute the gradient/normal of the SDF
float2 computeSdfNormal(float2 p, float2 halfSize, float r) {
    float2 w = abs(p) - (halfSize - r);
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    float g = max(w.x, w.y);
    float2 q = max(w, 0.0);
    float l = length(q);
    return s * ((g > 0.0) ? q / l : ((w.x > w.y) ? float2(1.0, 0.0) : float2(0.0, 1.0)));
}

half3 applyColorAdjustments(half3 color, float sat) {
    float lum = dot(color, half3(0.2126, 0.7152, 0.0722));
    return half3(clamp(mix(half3(lum), color, sat), 0.0, 1.0));
}

half4 main(float2 fragCoord) {
    float2 center = mouse;
    float2 halfSize = lensSize * 0.5;
    float r = min(cornerRadius, min(halfSize.x, halfSize.y));

    // Position relative to lens center
    float2 p = fragCoord - center;
    float sdf = computeSdf(p, halfSize, r);

    // Anti-aliasing width
    float aaWidth = AA_WIDTH_PX;

    // Outside the lens - return original content
    if (sdf > aaWidth) {
        return content.eval(fragCoord);
    }

    // Compute normal for refraction and lighting
    float2 normal = computeSdfNormal(p, halfSize, r);

    // Calculate base coordinate with refraction
    float2 baseCoord = fragCoord;
    if (refraction > 0.0) {
        float lensDepth = 1.0 - clamp(-sdf / (min(halfSize.x, halfSize.y) * refraction), 0.0, 1.0);
        float distortion = 1.0 - sqrt(1.0 - lensDepth * lensDepth);
        float normalDisplacement = distortion * -refraction * min(halfSize.x, halfSize.y) * 0.5;
        baseCoord = fragCoord + normalDisplacement * normal;
    }

    // Sample with blur and chromatic aberration
    half4 fragColor = half4(0.0);
    float sampleCount = 0.0;
    float blurRadius = blur;

    // Distance from center for aberration strength
    float2 distFromCenter = p / halfSize;
    float2 aberrationOffset = aberration * distFromCenter * distFromCenter * distFromCenter * min(halfSize.x, halfSize.y) * 0.1;

    for (float x = -BLUR_SAMPLES; x <= BLUR_SAMPLES; x++) {
        for (float y = -BLUR_SAMPLES; y <= BLUR_SAMPLES; y++) {
            float2 sampleOffset = float2(x, y) * blurRadius / BLUR_SAMPLES;

            // Sample with chromatic aberration
            float2 coordR = baseCoord + sampleOffset - aberrationOffset;
            float2 coordG = baseCoord + sampleOffset;
            float2 coordB = baseCoord + sampleOffset + aberrationOffset;

            // Check if samples are within lens bounds
            float sdfR = computeSdf(coordR - center, halfSize, r);
            float sdfB = computeSdf(coordB - center, halfSize, r);

            half4 colorG = content.eval(coordG);
            half4 colorR = (sdfR <= 0.0) ? content.eval(coordR) : colorG;
            half4 colorB = (sdfB <= 0.0) ? content.eval(coordB) : colorG;

            fragColor += half4(colorR.r, colorG.g, colorB.b, colorG.a);
            sampleCount += 1.0;
        }
    }
    fragColor /= sampleCount;

    // Handle case where sampled alpha is zero
    if (fragColor.a <= 0.0) {
        fragColor = content.eval(fragCoord);
    }

    // Apply saturation adjustment
    fragColor.rgb = applyColorAdjustments(fragColor.rgb, saturation);

    // Edge lighting based on normal
    float edgeSmooth = smoothstep(-edgeBrightness * 10.0, 0.0, sdf);
    float2 lightDirection = float2(-0.15, -0.15);
    float nDotL = abs(dot(normal, lightDirection));
    float edgeLighting = edgeSmooth * nDotL * edgeBrightness;
    fragColor.rgb += half3(edgeLighting);

    // Anti-aliased alpha at edges
    float aaAlpha = 1.0 - smoothstep(-aaWidth * 0.5, aaWidth * 0.5, sdf);

    // Blend with original content at edges
    half4 originalColor = content.eval(fragCoord);
    return mix(originalColor, fragColor, aaAlpha);
}
"""

  /**
   * SKSL shader source for Skia-based platforms (iOS, macOS, Desktop, WASM).
   *
   * Uses `uniform shader content` for the input content.
   * Note: SKSL syntax is nearly identical to AGSL for this shader.
   */
  public const val SKSL: String = """
uniform float2 resolution;
uniform float2 mouse;
uniform float2 lensSize;
uniform float cornerRadius;
uniform float refraction;
uniform float blur;
uniform float aberration;
uniform float saturation;
uniform float edgeBrightness;
uniform shader content;

const float AA_WIDTH_PX = 1.5;
const float BLUR_SAMPLES = 3.0;

// Signed distance function for rounded rectangle
float computeSdf(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

// Compute the gradient/normal of the SDF
float2 computeSdfNormal(float2 p, float2 halfSize, float r) {
    float2 w = abs(p) - (halfSize - r);
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    float g = max(w.x, w.y);
    float2 q = max(w, 0.0);
    float l = length(q);
    return s * ((g > 0.0) ? q / l : ((w.x > w.y) ? float2(1.0, 0.0) : float2(0.0, 1.0)));
}

half3 applyColorAdjustments(half3 color, float sat) {
    float lum = dot(color, half3(0.2126, 0.7152, 0.0722));
    return half3(clamp(mix(half3(lum), color, sat), 0.0, 1.0));
}

half4 main(float2 fragCoord) {
    float2 center = mouse;
    float2 halfSize = lensSize * 0.5;
    float r = min(cornerRadius, min(halfSize.x, halfSize.y));

    // Position relative to lens center
    float2 p = fragCoord - center;
    float sdf = computeSdf(p, halfSize, r);

    // Anti-aliasing width
    float aaWidth = AA_WIDTH_PX;

    // Outside the lens - return original content
    if (sdf > aaWidth) {
        return content.eval(fragCoord);
    }

    // Compute normal for refraction and lighting
    float2 normal = computeSdfNormal(p, halfSize, r);

    // Calculate base coordinate with refraction
    float2 baseCoord = fragCoord;
    if (refraction > 0.0) {
        float lensDepth = 1.0 - clamp(-sdf / (min(halfSize.x, halfSize.y) * refraction), 0.0, 1.0);
        float distortion = 1.0 - sqrt(1.0 - lensDepth * lensDepth);
        float normalDisplacement = distortion * -refraction * min(halfSize.x, halfSize.y) * 0.5;
        baseCoord = fragCoord + normalDisplacement * normal;
    }

    // Sample with blur and chromatic aberration
    half4 fragColor = half4(0.0);
    float sampleCount = 0.0;
    float blurRadius = blur;

    // Distance from center for aberration strength
    float2 distFromCenter = p / halfSize;
    float2 aberrationOffset = aberration * distFromCenter * distFromCenter * distFromCenter * min(halfSize.x, halfSize.y) * 0.1;

    for (float x = -BLUR_SAMPLES; x <= BLUR_SAMPLES; x++) {
        for (float y = -BLUR_SAMPLES; y <= BLUR_SAMPLES; y++) {
            float2 sampleOffset = float2(x, y) * blurRadius / BLUR_SAMPLES;

            // Sample with chromatic aberration
            float2 coordR = baseCoord + sampleOffset - aberrationOffset;
            float2 coordG = baseCoord + sampleOffset;
            float2 coordB = baseCoord + sampleOffset + aberrationOffset;

            // Check if samples are within lens bounds
            float sdfR = computeSdf(coordR - center, halfSize, r);
            float sdfB = computeSdf(coordB - center, halfSize, r);

            half4 colorG = content.eval(coordG);
            half4 colorR = (sdfR <= 0.0) ? content.eval(coordR) : colorG;
            half4 colorB = (sdfB <= 0.0) ? content.eval(coordB) : colorG;

            fragColor += half4(colorR.r, colorG.g, colorB.b, colorG.a);
            sampleCount += 1.0;
        }
    }
    fragColor /= sampleCount;

    // Handle case where sampled alpha is zero
    if (fragColor.a <= 0.0) {
        fragColor = content.eval(fragCoord);
    }

    // Apply saturation adjustment
    fragColor.rgb = applyColorAdjustments(fragColor.rgb, saturation);

    // Edge lighting based on normal
    float edgeSmooth = smoothstep(-edgeBrightness * 10.0, 0.0, sdf);
    float2 lightDirection = float2(-0.15, -0.15);
    float nDotL = abs(dot(normal, lightDirection));
    float edgeLighting = edgeSmooth * nDotL * edgeBrightness;
    fragColor.rgb += half3(edgeLighting);

    // Anti-aliased alpha at edges
    float aaAlpha = 1.0 - smoothstep(-aaWidth * 0.5, aaWidth * 0.5, sdf);

    // Blend with original content at edges
    half4 originalColor = content.eval(fragCoord);
    return mix(originalColor, fragColor, aaAlpha);
}
"""
}
