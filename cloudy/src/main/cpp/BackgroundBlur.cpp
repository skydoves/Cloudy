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
static BackgroundBlurBuffers gBuffers;

/**
 * Crops and scales down a region from the source image.
 * Uses bilinear interpolation for quality.
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

            // Bilinear interpolation for each channel
            for (int c = 0; c < 4; c++) {
                const float p00 = src[(srcY0 * srcWidth + srcX0) * 4 + c];
                const float p10 = src[(srcY0 * srcWidth + srcX1) * 4 + c];
                const float p01 = src[(srcY1 * srcWidth + srcX0) * 4 + c];
                const float p11 = src[(srcY1 * srcWidth + srcX1) * 4 + c];

                const float top = p00 + (p10 - p00) * xFrac;
                const float bottom = p01 + (p11 - p01) * xFrac;
                const float value = top + (bottom - top) * yFrac;

                dst[(y * dstWidth + x) * 4 + c] = static_cast<uint8_t>(std::clamp(value, 0.0f, 255.0f));
            }
        }
    }
}

/**
 * Scales up an image using bilinear interpolation.
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

            // Bilinear interpolation for each channel
            for (int c = 0; c < 4; c++) {
                const float p00 = src[(srcY0 * srcWidth + srcX0) * 4 + c];
                const float p10 = src[(srcY0 * srcWidth + srcX1) * 4 + c];
                const float p01 = src[(srcY1 * srcWidth + srcX0) * 4 + c];
                const float p11 = src[(srcY1 * srcWidth + srcX1) * 4 + c];

                const float top = p00 + (p10 - p00) * xFrac;
                const float bottom = p01 + (p11 - p01) * xFrac;
                const float value = top + (bottom - top) * yFrac;

                dst[(y * dstWidth + x) * 4 + c] = static_cast<uint8_t>(std::clamp(value, 0.0f, 255.0f));
            }
        }
    }
}

/**
 * Applies a progressive alpha mask to create fade effect.
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

    for (size_t y = 0; y < height; y++) {
        float normalizedY = static_cast<float>(y) / (height - 1);
        float alpha = 1.0f;

        switch (direction) {
            case RenderScriptToolkit::ProgressiveDirection::TOP_TO_BOTTOM:
                // Full opacity at top, fade to transparent at bottom
                if (normalizedY <= fadeStart) {
                    alpha = 1.0f;
                } else if (normalizedY >= fadeEnd) {
                    alpha = 0.0f;
                } else {
                    alpha = 1.0f - (normalizedY - fadeStart) / (fadeEnd - fadeStart);
                }
                break;

            case RenderScriptToolkit::ProgressiveDirection::BOTTOM_TO_TOP:
                // Full opacity at bottom, fade to transparent at top
                if (normalizedY >= fadeStart) {
                    alpha = 1.0f;
                } else if (normalizedY <= fadeEnd) {
                    alpha = 0.0f;
                } else {
                    alpha = (normalizedY - fadeEnd) / (fadeStart - fadeEnd);
                }
                break;

            case RenderScriptToolkit::ProgressiveDirection::EDGES:
                // Fade at both edges
                if (normalizedY < fadeStart) {
                    alpha = normalizedY / fadeStart;
                } else if (normalizedY > fadeEnd) {
                    alpha = (1.0f - normalizedY) / (1.0f - fadeEnd);
                } else {
                    alpha = 1.0f;
                }
                break;

            default:
                break;
        }

        // Apply alpha to entire row
        for (size_t x = 0; x < width; x++) {
            size_t idx = (y * width + x) * 4;
            // Multiply alpha channel
            buffer[idx + 3] = static_cast<uint8_t>(buffer[idx + 3] * alpha);
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
        ALOGE("backgroundBlur: scale must be 0-1, got %f", scale);
        return false;
    }
    if (cropWidth == 0 || cropHeight == 0) {
        ALOGE("backgroundBlur: crop dimensions are zero");
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

    // Step 3: Apply progressive mask (in-place)
    applyProgressiveMask(
        gBuffers.blurOutput.data(), scaledWidth, scaledHeight,
        progressiveDir, fadeStart, fadeEnd
    );

    // Step 4: Scale up to output
    scaleUp(
        gBuffers.blurOutput.data(), scaledWidth, scaledHeight,
        dst, cropWidth, cropHeight
    );

    return true;
}

}  // namespace renderscript
