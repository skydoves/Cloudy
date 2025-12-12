/*
 * Copyright (C) 2024 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

#include "RenderScriptToolkit.h"
#include "TaskProcessor.h"
#include "Utils.h"

namespace renderscript {

#define LOG_TAG "renderscript.toolkit.BackgroundBlur"

/**
 * sRGB Gamma correction lookup tables for correct bilinear interpolation.
 * Bilinear interpolation must be performed in linear space, not gamma-encoded sRGB.
 */
class GammaLUT {
public:
    float srgbToLinear[256];
    uint8_t linearToSrgb[4096];  // 12-bit precision for smooth gradients

    GammaLUT() {
        // Build sRGB to linear LUT
        for (int i = 0; i < 256; i++) {
            float s = i / 255.0f;
            if (s <= 0.04045f) {
                srgbToLinear[i] = s / 12.92f;
            } else {
                srgbToLinear[i] = std::pow((s + 0.055f) / 1.055f, 2.4f);
            }
        }

        // Build linear to sRGB LUT (12-bit input for smooth gradients)
        for (int i = 0; i < 4096; i++) {
            float linear = i / 4095.0f;
            float srgb;
            if (linear <= 0.0031308f) {
                srgb = linear * 12.92f;
            } else {
                srgb = 1.055f * std::pow(linear, 1.0f / 2.4f) - 0.055f;
            }
            linearToSrgb[i] = static_cast<uint8_t>(std::clamp(srgb * 255.0f + 0.5f, 0.0f, 255.0f));
        }
    }

    inline uint8_t toSrgb(float linear) const {
        int idx = static_cast<int>(std::clamp(linear * 4095.0f + 0.5f, 0.0f, 4095.0f));
        return linearToSrgb[idx];
    }
};

// Global LUT instance (constructed once)
static const GammaLUT gGammaLUT;

/**
 * Internal buffer manager for background blur operations.
 * Reuses buffers across calls to minimize allocations.
 */
class BackgroundBlurBuffers {
public:
    std::vector<uint8_t> scaledInput;
    std::vector<uint8_t> blurOutput;

    void ensureCapacity(size_t scaledSize, size_t blurSize) {
        if (scaledInput.size() < scaledSize) {
            scaledInput.resize(scaledSize);
        }
        if (blurOutput.size() < blurSize) {
            blurOutput.resize(blurSize);
        }
    }
};

// Global buffer instance for reuse
static thread_local BackgroundBlurBuffers gBuffers;

/**
 * Crops and scales down a region from the source image.
 * Uses gamma-correct bilinear interpolation (sRGB → linear → interpolate → sRGB).
 */
static void cropAndScaleDown(
    const uint8_t* src, size_t srcWidth, size_t srcHeight,
    size_t cropX, size_t cropY, size_t cropWidth, size_t cropHeight,
    uint8_t* dst, size_t dstWidth, size_t dstHeight
) {
    const float scaleX = static_cast<float>(cropWidth) / dstWidth;
    const float scaleY = static_cast<float>(cropHeight) / dstHeight;

    for (size_t y = 0; y < dstHeight; y++) {
        const float srcYf = cropY + y * scaleY;
        const size_t srcY0 = std::min(static_cast<size_t>(srcYf), srcHeight - 1);
        const size_t srcY1 = std::min(srcY0 + 1, srcHeight - 1);
        const float yFrac = srcYf - srcY0;

        for (size_t x = 0; x < dstWidth; x++) {
            const float srcXf = cropX + x * scaleX;
            const size_t srcX0 = std::min(static_cast<size_t>(srcXf), srcWidth - 1);
            const size_t srcX1 = std::min(srcX0 + 1, srcWidth - 1);
            const float xFrac = srcXf - srcX0;

            // Gamma-correct bilinear interpolation for RGB channels
            for (int c = 0; c < 3; c++) {
                // Convert sRGB to linear
                const float p00 = gGammaLUT.srgbToLinear[src[(srcY0 * srcWidth + srcX0) * 4 + c]];
                const float p10 = gGammaLUT.srgbToLinear[src[(srcY0 * srcWidth + srcX1) * 4 + c]];
                const float p01 = gGammaLUT.srgbToLinear[src[(srcY1 * srcWidth + srcX0) * 4 + c]];
                const float p11 = gGammaLUT.srgbToLinear[src[(srcY1 * srcWidth + srcX1) * 4 + c]];

                // Bilinear interpolation in linear space
                const float top = p00 + (p10 - p00) * xFrac;
                const float bottom = p01 + (p11 - p01) * xFrac;
                const float linear = top + (bottom - top) * yFrac;

                // Convert back to sRGB
                dst[(y * dstWidth + x) * 4 + c] = gGammaLUT.toSrgb(linear);
            }

            // Alpha channel: linear interpolation (no gamma)
            {
                const float p00 = src[(srcY0 * srcWidth + srcX0) * 4 + 3];
                const float p10 = src[(srcY0 * srcWidth + srcX1) * 4 + 3];
                const float p01 = src[(srcY1 * srcWidth + srcX0) * 4 + 3];
                const float p11 = src[(srcY1 * srcWidth + srcX1) * 4 + 3];

                const float top = p00 + (p10 - p00) * xFrac;
                const float bottom = p01 + (p11 - p01) * xFrac;
                const float value = top + (bottom - top) * yFrac;

                dst[(y * dstWidth + x) * 4 + 3] = static_cast<uint8_t>(std::clamp(value, 0.0f, 255.0f));
            }
        }
    }
}

/**
 * Scales up an image using gamma-correct bilinear interpolation.
 */
static void scaleUp(
    const uint8_t* src, size_t srcWidth, size_t srcHeight,
    uint8_t* dst, size_t dstWidth, size_t dstHeight
) {
    const float scaleX = static_cast<float>(srcWidth) / dstWidth;
    const float scaleY = static_cast<float>(srcHeight) / dstHeight;

    for (size_t y = 0; y < dstHeight; y++) {
        const float srcYf = y * scaleY;
        const size_t srcY0 = std::min(static_cast<size_t>(srcYf), srcHeight - 1);
        const size_t srcY1 = std::min(srcY0 + 1, srcHeight - 1);
        const float yFrac = srcYf - srcY0;

        for (size_t x = 0; x < dstWidth; x++) {
            const float srcXf = x * scaleX;
            const size_t srcX0 = std::min(static_cast<size_t>(srcXf), srcWidth - 1);
            const size_t srcX1 = std::min(srcX0 + 1, srcWidth - 1);
            const float xFrac = srcXf - srcX0;

            // Gamma-correct bilinear interpolation for RGB channels
            for (int c = 0; c < 3; c++) {
                // Convert sRGB to linear
                const float p00 = gGammaLUT.srgbToLinear[src[(srcY0 * srcWidth + srcX0) * 4 + c]];
                const float p10 = gGammaLUT.srgbToLinear[src[(srcY0 * srcWidth + srcX1) * 4 + c]];
                const float p01 = gGammaLUT.srgbToLinear[src[(srcY1 * srcWidth + srcX0) * 4 + c]];
                const float p11 = gGammaLUT.srgbToLinear[src[(srcY1 * srcWidth + srcX1) * 4 + c]];

                // Bilinear interpolation in linear space
                const float top = p00 + (p10 - p00) * xFrac;
                const float bottom = p01 + (p11 - p01) * xFrac;
                const float linear = top + (bottom - top) * yFrac;

                // Convert back to sRGB
                dst[(y * dstWidth + x) * 4 + c] = gGammaLUT.toSrgb(linear);
            }

            // Alpha channel: linear interpolation (no gamma)
            {
                const float p00 = src[(srcY0 * srcWidth + srcX0) * 4 + 3];
                const float p10 = src[(srcY0 * srcWidth + srcX1) * 4 + 3];
                const float p01 = src[(srcY1 * srcWidth + srcX0) * 4 + 3];
                const float p11 = src[(srcY1 * srcWidth + srcX1) * 4 + 3];

                const float top = p00 + (p10 - p00) * xFrac;
                const float bottom = p01 + (p11 - p01) * xFrac;
                const float value = top + (bottom - top) * yFrac;

                dst[(y * dstWidth + x) * 4 + 3] = static_cast<uint8_t>(std::clamp(value, 0.0f, 255.0f));
            }
        }
    }
}

/**
 * Applies a progressive alpha mask to create fade effect.
 * Uses PREMULTIPLIED ALPHA: RGB channels are also multiplied by alpha.
 * This is required for correct compositing on Android (ARGB_8888 is premultiplied).
 * Modifies the buffer in-place.
 */
static void applyProgressiveMask(
    uint8_t* buffer, size_t width, size_t height,
    RenderScriptToolkit::ProgressiveDirection direction,
    float fadeStart, float fadeEnd
) {
    if (direction == RenderScriptToolkit::ProgressiveDirection::NONE) {
        return;
    }

    // Prevent division by zero
    const float heightDenom = (height > 1) ? static_cast<float>(height - 1) : 1.0f;

    for (size_t y = 0; y < height; y++) {
        float normalizedY = static_cast<float>(y) / heightDenom;
        float alpha = 1.0f;

        switch (direction) {
            case RenderScriptToolkit::ProgressiveDirection::TOP_TO_BOTTOM:
                // Full opacity at top, fade to transparent at bottom
                if (normalizedY <= fadeStart) {
                    alpha = 1.0f;
                } else if (normalizedY >= fadeEnd) {
                    alpha = 0.0f;
                } else {
                    float range = fadeEnd - fadeStart;
                    alpha = (range > 0.0f) ? 1.0f - (normalizedY - fadeStart) / range : 1.0f;
                }
                break;

            case RenderScriptToolkit::ProgressiveDirection::BOTTOM_TO_TOP:
                // Full opacity at bottom, fade to transparent at top
                if (normalizedY >= fadeStart) {
                    alpha = 1.0f;
                } else if (normalizedY <= fadeEnd) {
                    alpha = 0.0f;
                } else {
                    float range = fadeStart - fadeEnd;
                    alpha = (range > 0.0f) ? (normalizedY - fadeEnd) / range : 1.0f;
                }
                break;

            case RenderScriptToolkit::ProgressiveDirection::EDGES:
                // Fade at both edges - fixed boundary conditions
                if (normalizedY <= fadeStart) {
                    // Top edge fade: 0 at y=0, 1 at y=fadeStart
                    alpha = (fadeStart > 0.0f) ? normalizedY / fadeStart : 1.0f;
                } else if (normalizedY >= fadeEnd) {
                    // Bottom edge fade: 1 at y=fadeEnd, 0 at y=1
                    alpha = (fadeEnd < 1.0f) ? (1.0f - normalizedY) / (1.0f - fadeEnd) : 1.0f;
                } else {
                    alpha = 1.0f;
                }
                break;

            default:
                break;
        }

        // Clamp alpha to valid range to prevent overflow/underflow
        alpha = std::clamp(alpha, 0.0f, 1.0f);

        // Apply PREMULTIPLIED alpha to entire row
        // Both RGB and A channels are multiplied by alpha
        // Use rounding (+0.5f) instead of truncation for better precision
        for (size_t x = 0; x < width; x++) {
            size_t idx = (y * width + x) * 4;
            buffer[idx + 0] = static_cast<uint8_t>(buffer[idx + 0] * alpha + 0.5f);  // R
            buffer[idx + 1] = static_cast<uint8_t>(buffer[idx + 1] * alpha + 0.5f);  // G
            buffer[idx + 2] = static_cast<uint8_t>(buffer[idx + 2] * alpha + 0.5f);  // B
            buffer[idx + 3] = static_cast<uint8_t>(buffer[idx + 3] * alpha + 0.5f);  // A
        }
    }
}

bool RenderScriptToolkit::backgroundBlur(
    const uint8_t* src, uint8_t* dst,
    size_t srcWidth, size_t srcHeight,
    size_t cropX, size_t cropY,
    size_t cropWidth, size_t cropHeight,
    int radius, float scale,
    ProgressiveDirection progressiveDir,
    float fadeStart, float fadeEnd
) {
    // Validate parameters
    if (src == nullptr || dst == nullptr) {
        ALOGE("backgroundBlur: null buffer");
        return false;
    }
    if (cropX + cropWidth > srcWidth || cropY + cropHeight > srcHeight) {
        ALOGE("backgroundBlur: crop region exceeds source bounds");
        return false;
    }
    if (radius < 1 || radius > 25) {
        ALOGE("backgroundBlur: radius must be 1-25, got %d", radius);
        return false;
    }
    if (scale <= 0.0f || scale > 1.0f) {
        ALOGE("backgroundBlur: scale must be > 0 and <= 1, got %f", scale);
        return false;
    }
    if (cropWidth == 0 || cropHeight == 0) {
        ALOGE("backgroundBlur: crop dimensions are zero");
        return false;
    }
    if (fadeStart < 0.0f || fadeStart > 1.0f || fadeEnd < 0.0f || fadeEnd > 1.0f) {
        ALOGE("backgroundBlur: fadeStart and fadeEnd must be in [0.0, 1.0], got %f, %f", fadeStart, fadeEnd);
        return false;
    }
    if (progressiveDir == ProgressiveDirection::TOP_TO_BOTTOM && fadeStart >= fadeEnd) {
        ALOGE("backgroundBlur: TOP_TO_BOTTOM requires fadeStart < fadeEnd, got %f >= %f", fadeStart, fadeEnd);
        return false;
    }
    if (progressiveDir == ProgressiveDirection::BOTTOM_TO_TOP && fadeEnd >= fadeStart) {
        ALOGE("backgroundBlur: BOTTOM_TO_TOP requires fadeEnd < fadeStart, got %f >= %f", fadeEnd, fadeStart);
        return false;
    }
    if (progressiveDir == ProgressiveDirection::EDGES && fadeStart >= fadeEnd) {
        ALOGE("backgroundBlur: EDGES requires fadeStart < fadeEnd, got %f >= %f", fadeStart, fadeEnd);
        return false;
    }

    // Calculate scaled dimensions
    const size_t scaledWidth = std::max(static_cast<size_t>(cropWidth * scale), size_t(1));
    const size_t scaledHeight = std::max(static_cast<size_t>(cropHeight * scale), size_t(1));
    const size_t scaledSize = scaledWidth * scaledHeight * 4;

    // Ensure buffers have sufficient capacity
    gBuffers.ensureCapacity(scaledSize, scaledSize);

    // Step 1: Crop and scale down
    cropAndScaleDown(
        src, srcWidth, srcHeight,
        cropX, cropY, cropWidth, cropHeight,
        gBuffers.scaledInput.data(), scaledWidth, scaledHeight
    );

    // Step 2: Apply blur
    // Adjust radius for scaled image
    int scaledRadius = std::max(static_cast<int>(radius * scale), 1);
    scaledRadius = std::min(scaledRadius, 25);

    blur(
        gBuffers.scaledInput.data(),
        gBuffers.blurOutput.data(),
        scaledWidth, scaledHeight,
        4,  // RGBA
        scaledRadius,
        nullptr
    );

    // Step 3: Scale up to output FIRST
    // This prevents alpha gradient interpolation artifacts
    scaleUp(
        gBuffers.blurOutput.data(), scaledWidth, scaledHeight,
        dst, cropWidth, cropHeight
    );

    // Step 4: Apply progressive mask on FINAL full-resolution output
    // This ensures crisp alpha gradients without interpolation artifacts
    applyProgressiveMask(
        dst, cropWidth, cropHeight,
        progressiveDir, fadeStart, fadeEnd
    );

    return true;
}

}  // namespace renderscript
