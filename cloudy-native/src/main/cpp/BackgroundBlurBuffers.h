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

#ifndef ANDROID_RENDERSCRIPT_TOOLKIT_BACKGROUNDBLURBUFFERS_H
#define ANDROID_RENDERSCRIPT_TOOLKIT_BACKGROUNDBLURBUFFERS_H

#include <cstdint>
#include <mutex>
#include <vector>

namespace renderscript {

/**
 * Scratch buffers reused across backgroundBlur calls to minimize allocations.
 *
 * Owned by the RenderScriptToolkit instance (see RenderScriptToolkit.h) so the memory is
 * released when the toolkit is destroyed rather than living for the whole process. The mutex
 * guards the whole crop -> blur -> scale-up pipeline: backgroundBlur may run on multiple
 * Dispatchers.Default workers at once and its crop/scale steps run outside TaskProcessor's task
 * mutex, so these shared buffers would otherwise race.
 */
struct BackgroundBlurBuffers {
    std::mutex mutex;
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

}  // namespace renderscript

#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_BACKGROUNDBLURBUFFERS_H
