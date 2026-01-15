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
 * - Rounded rectangle distance field for crisp edges
 * - Surface gradient-based refraction and curve distortion
 * - Chromatic dispersion (RGB channel separation)
 * - Saturation, contrast, and tint adjustments
 * - Edge lighting and anti-aliasing
 *
 * **Note:** For blur effects, use [Modifier.cloudy] separately. This shader focuses on
 * lens distortion and can be combined with Cloudy's blur for a complete frosted glass look.
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
uniform float curve;
uniform float dispersion;
uniform float saturation;
uniform float contrast;
uniform float4 tint;
uniform float edge;
uniform shader content;

const float ANTIALIAS_RADIUS = 1.5;

// Calculate distance from point to rounded rectangle boundary
// Returns negative inside, positive outside
float roundedRectDistance(float2 point, float2 boxExtent, float radius) {
    float2 offsetFromCorner = abs(point) - boxExtent + float2(radius);
    float outsideDistance = length(max(offsetFromCorner, 0.0));
    float insideDistance = min(max(offsetFromCorner.x, offsetFromCorner.y), 0.0);
    return outsideDistance + insideDistance - radius;
}

// Calculate outward-pointing surface gradient at a point
float2 calculateSurfaceGradient(float2 point, float2 boxExtent, float radius) {
    float2 innerOffset = abs(point) - boxExtent + float2(radius);
    float2 signVector = float2(
        point.x >= 0.0 ? 1.0 : -1.0,
        point.y >= 0.0 ? 1.0 : -1.0
    );

    // Outside the inner rounded region
    if (max(innerOffset.x, innerOffset.y) > 0.0) {
        float2 clampedOffset = max(innerOffset, 0.0);
        return signVector * normalize(clampedOffset);
    }

    // Inside - gradient points toward nearest edge
    if (innerOffset.x > innerOffset.y) {
        return float2(signVector.x, 0.0);
    }
    return float2(0.0, signVector.y);
}

// Compute perceived luminance using Rec. 709 coefficients
float getLuminance(half3 rgb) {
    return dot(rgb, half3(0.2126, 0.7152, 0.0722));
}

// Apply color grading: saturation, contrast, and tint overlay
half3 applyColorGrading(half3 inputColor, float satLevel, float contrastLevel, float4 tintOverlay) {
    // Saturation adjustment via luminance mixing
    float gray = getLuminance(inputColor);
    half3 saturatedColor = half3(clamp(mix(half3(gray), inputColor, satLevel), 0.0, 1.0));

    // Contrast adjustment around middle gray
    half3 contrastedColor = half3(clamp((saturatedColor - 0.5) * contrastLevel + 0.5, 0.0, 1.0));

    // Blend with tint based on tint alpha
    return mix(contrastedColor, half3(tintOverlay.rgb), tintOverlay.a);
}

half4 main(float2 fragCoord) {
    float2 lensCenter = mouse;
    float2 halfExtent = lensSize * 0.5;
    float clampedRadius = min(cornerRadius, min(halfExtent.x, halfExtent.y));

    // Get position relative to lens center
    float2 localPos = fragCoord - lensCenter;
    float dist = roundedRectDistance(localPos, halfExtent, clampedRadius);

    // Early exit for pixels clearly outside the lens
    if (dist > ANTIALIAS_RADIUS) {
        return content.eval(fragCoord);
    }

    // Calculate surface gradient for refraction direction
    float2 surfaceDir = calculateSurfaceGradient(localPos, halfExtent, clampedRadius);

    // Apply lens refraction effect
    float2 samplingCoord = fragCoord;
    if (refraction > 0.0 && curve > 0.0) {
        float minExtent = min(halfExtent.x, halfExtent.y);
        float normalizedDepth = clamp(-dist / (minExtent * refraction), 0.0, 1.0);
        float sphericalFactor = 1.0 - normalizedDepth;
        float bendAmount = 1.0 - sqrt(1.0 - sphericalFactor * sphericalFactor);
        float displacement = bendAmount * curve * minExtent;
        samplingCoord = fragCoord - displacement * surfaceDir;
    }

    // Sample with chromatic dispersion (RGB channel separation)
    half4 sampledColor;
    if (dispersion > 0.0) {
        // Dispersion offset based on distance from center (cubic falloff for realism)
        float2 normalizedPos = localPos / halfExtent;
        float2 chromaticShift = dispersion * normalizedPos * normalizedPos * normalizedPos * min(halfExtent.x, halfExtent.y) * 0.1;

        // Separate RGB channels for chromatic dispersion
        float2 redCoord = samplingCoord - chromaticShift;
        float2 greenCoord = samplingCoord;
        float2 blueCoord = samplingCoord + chromaticShift;

        // Validate red/blue samples are within lens bounds
        float redDist = roundedRectDistance(redCoord - lensCenter, halfExtent, clampedRadius);
        float blueDist = roundedRectDistance(blueCoord - lensCenter, halfExtent, clampedRadius);

        half4 greenSample = content.eval(greenCoord);
        half4 redSample = (redDist <= 0.0) ? content.eval(redCoord) : greenSample;
        half4 blueSample = (blueDist <= 0.0) ? content.eval(blueCoord) : greenSample;

        sampledColor = half4(redSample.r, greenSample.g, blueSample.b, greenSample.a);
    } else {
        sampledColor = content.eval(samplingCoord);
    }

    // Fallback if alpha is zero (transparent region)
    if (sampledColor.a <= 0.0) {
        sampledColor = content.eval(fragCoord);
    }

    // Apply color grading
    sampledColor.rgb = applyColorGrading(sampledColor.rgb, saturation, contrast, tint);

    // Edge rim lighting effect
    if (edge > 0.0) {
        float rimFactor = smoothstep(-edge * 10.0, 0.0, dist);
        float2 lightDir = normalize(float2(-1.0, -1.0));
        float lightIntensity = abs(dot(surfaceDir, lightDir));
        sampledColor.rgb += half3(rimFactor * lightIntensity * edge);
    }

    // Smooth alpha blend at edges for anti-aliasing
    float edgeAlpha = 1.0 - smoothstep(-ANTIALIAS_RADIUS * 0.5, ANTIALIAS_RADIUS * 0.5, dist);

    half4 originalColor = content.eval(fragCoord);
    return mix(originalColor, sampledColor, edgeAlpha);
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
uniform float curve;
uniform float dispersion;
uniform float saturation;
uniform float contrast;
uniform float4 tint;
uniform float edge;
uniform shader content;

const float ANTIALIAS_RADIUS = 1.5;

// Calculate distance from point to rounded rectangle boundary
// Returns negative inside, positive outside
float roundedRectDistance(float2 point, float2 boxExtent, float radius) {
    float2 offsetFromCorner = abs(point) - boxExtent + float2(radius);
    float outsideDistance = length(max(offsetFromCorner, 0.0));
    float insideDistance = min(max(offsetFromCorner.x, offsetFromCorner.y), 0.0);
    return outsideDistance + insideDistance - radius;
}

// Calculate outward-pointing surface gradient at a point
float2 calculateSurfaceGradient(float2 point, float2 boxExtent, float radius) {
    float2 innerOffset = abs(point) - boxExtent + float2(radius);
    float2 signVector = float2(
        point.x >= 0.0 ? 1.0 : -1.0,
        point.y >= 0.0 ? 1.0 : -1.0
    );

    // Outside the inner rounded region
    if (max(innerOffset.x, innerOffset.y) > 0.0) {
        float2 clampedOffset = max(innerOffset, 0.0);
        return signVector * normalize(clampedOffset);
    }

    // Inside - gradient points toward nearest edge
    if (innerOffset.x > innerOffset.y) {
        return float2(signVector.x, 0.0);
    }
    return float2(0.0, signVector.y);
}

// Compute perceived luminance using Rec. 709 coefficients
float getLuminance(half3 rgb) {
    return dot(rgb, half3(0.2126, 0.7152, 0.0722));
}

// Apply color grading: saturation, contrast, and tint overlay
half3 applyColorGrading(half3 inputColor, float satLevel, float contrastLevel, float4 tintOverlay) {
    // Saturation adjustment via luminance mixing
    float gray = getLuminance(inputColor);
    half3 saturatedColor = half3(clamp(mix(half3(gray), inputColor, satLevel), 0.0, 1.0));

    // Contrast adjustment around middle gray
    half3 contrastedColor = half3(clamp((saturatedColor - 0.5) * contrastLevel + 0.5, 0.0, 1.0));

    // Blend with tint based on tint alpha
    return mix(contrastedColor, half3(tintOverlay.rgb), tintOverlay.a);
}

half4 main(float2 fragCoord) {
    float2 lensCenter = mouse;
    float2 halfExtent = lensSize * 0.5;
    float clampedRadius = min(cornerRadius, min(halfExtent.x, halfExtent.y));

    // Get position relative to lens center
    float2 localPos = fragCoord - lensCenter;
    float dist = roundedRectDistance(localPos, halfExtent, clampedRadius);

    // Early exit for pixels clearly outside the lens
    if (dist > ANTIALIAS_RADIUS) {
        return content.eval(fragCoord);
    }

    // Calculate surface gradient for refraction direction
    float2 surfaceDir = calculateSurfaceGradient(localPos, halfExtent, clampedRadius);

    // Apply lens refraction effect
    float2 samplingCoord = fragCoord;
    if (refraction > 0.0 && curve > 0.0) {
        float minExtent = min(halfExtent.x, halfExtent.y);
        float normalizedDepth = clamp(-dist / (minExtent * refraction), 0.0, 1.0);
        float sphericalFactor = 1.0 - normalizedDepth;
        float bendAmount = 1.0 - sqrt(1.0 - sphericalFactor * sphericalFactor);
        float displacement = bendAmount * curve * minExtent;
        samplingCoord = fragCoord - displacement * surfaceDir;
    }

    // Sample with chromatic dispersion (RGB channel separation)
    half4 sampledColor;
    if (dispersion > 0.0) {
        // Dispersion offset based on distance from center (cubic falloff for realism)
        float2 normalizedPos = localPos / halfExtent;
        float2 chromaticShift = dispersion * normalizedPos * normalizedPos * normalizedPos * min(halfExtent.x, halfExtent.y) * 0.1;

        // Separate RGB channels for chromatic dispersion
        float2 redCoord = samplingCoord - chromaticShift;
        float2 greenCoord = samplingCoord;
        float2 blueCoord = samplingCoord + chromaticShift;

        // Validate red/blue samples are within lens bounds
        float redDist = roundedRectDistance(redCoord - lensCenter, halfExtent, clampedRadius);
        float blueDist = roundedRectDistance(blueCoord - lensCenter, halfExtent, clampedRadius);

        half4 greenSample = content.eval(greenCoord);
        half4 redSample = (redDist <= 0.0) ? content.eval(redCoord) : greenSample;
        half4 blueSample = (blueDist <= 0.0) ? content.eval(blueCoord) : greenSample;

        sampledColor = half4(redSample.r, greenSample.g, blueSample.b, greenSample.a);
    } else {
        sampledColor = content.eval(samplingCoord);
    }

    // Fallback if alpha is zero (transparent region)
    if (sampledColor.a <= 0.0) {
        sampledColor = content.eval(fragCoord);
    }

    // Apply color grading
    sampledColor.rgb = applyColorGrading(sampledColor.rgb, saturation, contrast, tint);

    // Edge rim lighting effect
    if (edge > 0.0) {
        float rimFactor = smoothstep(-edge * 10.0, 0.0, dist);
        float2 lightDir = normalize(float2(-1.0, -1.0));
        float lightIntensity = abs(dot(surfaceDir, lightDir));
        sampledColor.rgb += half3(rimFactor * lightIntensity * edge);
    }

    // Smooth alpha blend at edges for anti-aliasing
    float edgeAlpha = 1.0 - smoothstep(-ANTIALIAS_RADIUS * 0.5, ANTIALIAS_RADIUS * 0.5, dist);

    half4 originalColor = content.eval(fragCoord);
    return mix(originalColor, sampledColor, edgeAlpha);
}
"""
}
