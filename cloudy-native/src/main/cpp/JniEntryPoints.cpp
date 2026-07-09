/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <android/bitmap.h>
#include <cassert>
#include <cstdint>
#include <jni.h>

#include "RenderScriptToolkit.h"
#include "Utils.h"

#define LOG_TAG "renderscript.toolkit.JniEntryPoints"

using namespace renderscript;

namespace {
// Constraints inherited from ScriptIntrinsicBlur, mirrored by the toolkit's own checks in Blur.cpp.
constexpr int kMaxBlurRadius = 25;         // ScriptIntrinsicBlur caps the radius at 25.
constexpr jint kVectorSizeA8 = 1;          // single-channel alpha (A_8).
constexpr jint kVectorSizeRgba8888 = 4;    // four-channel color (RGBA_8888).
}  // namespace

/**
 * I compared using env->GetPrimitiveArrayCritical vs. env->GetByteArrayElements to get access
 * to the underlying data. On Pixel 4, it's actually faster to not use critical. The code is left
 * here if you want to experiment. Note that USE_CRITICAL could block the garbage collector.
 */
// #define USE_CRITICAL

class ByteArrayGuard {
private:
    JNIEnv *env;
    jbyteArray array;
    jbyte *data;

public:
    ByteArrayGuard(JNIEnv *env, jbyteArray array) : env{env}, array{array} {
#ifdef USE_CRITICAL
        data = reinterpret_cast<jbyte*>(env->GetPrimitiveArrayCritical(array, nullptr));
#else
        data = env->GetByteArrayElements(array, nullptr);
#endif
    }

    ~ByteArrayGuard() {
        // Get*ArrayElements can fail; passing its null result to a Release call is UB (CheckJNI aborts).
        if (data == nullptr) {
            return;
        }
#ifdef USE_CRITICAL
        env->ReleasePrimitiveArrayCritical(array, data, 0);
#else
        env->ReleaseByteArrayElements(array, data, 0);
#endif
    }

    uint8_t *get() { return reinterpret_cast<uint8_t *>(data); }

    // Get*ArrayElements returns null on failure; the kernel would deref it.
    bool isValid() const { return data != nullptr; }
};

class IntArrayGuard {
private:
    JNIEnv *env;
    jintArray array;
    jint *data;

public:
    IntArrayGuard(JNIEnv *env, jintArray array) : env{env}, array{array} {
#ifdef USE_CRITICAL
        data = reinterpret_cast<jint*>(env->GetPrimitiveArrayCritical(array, nullptr));
#else
        data = env->GetIntArrayElements(array, nullptr);
#endif
    }

    ~IntArrayGuard() {
#ifdef USE_CRITICAL
        env->ReleasePrimitiveArrayCritical(array, data, 0);
#else
        env->ReleaseIntArrayElements(array, data, 0);
#endif
    }

    int *get() { return reinterpret_cast<int *>(data); }
};

class FloatArrayGuard {
private:
    JNIEnv *env;
    jfloatArray array;
    jfloat *data;

public:
    FloatArrayGuard(JNIEnv *env, jfloatArray array) : env{env}, array{array} {
#ifdef USE_CRITICAL
        data = reinterpret_cast<jfloat*>(env->GetPrimitiveArrayCritical(array, nullptr));
#else
        data = env->GetFloatArrayElements(array, nullptr);
#endif
    }

    ~FloatArrayGuard() {
#ifdef USE_CRITICAL
        env->ReleasePrimitiveArrayCritical(array, data, 0);
#else
        env->ReleaseFloatArrayElements(array, data, 0);
#endif
    }

    float *get() { return reinterpret_cast<float *>(data); }
};

class BitmapGuard {
private:
    JNIEnv *env;
    jobject bitmap;
    AndroidBitmapInfo info;
    int bytesPerPixel;
    void *bytes;
    bool valid;

public:
    BitmapGuard(JNIEnv *env, jobject jBitmap) : env{env}, bitmap{jBitmap}, bytes{nullptr} {
        valid = false;
        if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
            ALOGE("AndroidBitmap_getInfo failed");
            return;
        }
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
            info.format != ANDROID_BITMAP_FORMAT_A_8) {
            ALOGE("AndroidBitmap in the wrong format");
            return;
        }
        bytesPerPixel = info.stride / info.width;
        if (bytesPerPixel != 1 && bytesPerPixel != 4) {
            ALOGE("Expected a vector size of 1 or 4. Got %d. Extra padding per line not currently "
                  "supported",
                  bytesPerPixel);
            return;
        }
        if (AndroidBitmap_lockPixels(env, bitmap, &bytes) != ANDROID_BITMAP_RESULT_SUCCESS) {
            ALOGE("AndroidBitmap_lockPixels failed");
            return;
        }
        valid = true;
    }

    ~BitmapGuard() {
        if (valid) {
            AndroidBitmap_unlockPixels(env, bitmap);
        }
    }

    uint8_t *get() const {
        // assert() is a no-op under NDEBUG (release), so callers must gate on isValid().
        assert(valid);
        return reinterpret_cast<uint8_t *>(bytes);
    }

    bool isValid() const { return valid; }

    int width() const { return info.width; }

    int height() const { return info.height; }

    int vectorSize() const { return bytesPerPixel; }
};

/**
 * Copies the content of Kotlin Range2d object into the equivalent C++ struct.
 */
class RestrictionParameter {
private:
    bool isNull;
    Restriction restriction;

public:
    RestrictionParameter(JNIEnv *env, jobject jRestriction) : isNull{jRestriction == nullptr} {
        if (isNull) {
            return;
        }
        /* TODO Measure how long FindClass and related functions take. Consider passing the
         * four values instead. This would also require setting the default when Range2D is null.
         */
        jclass restrictionClass = env->FindClass("com/skydoves/cloudy/internals/render/Range2d");
        if (restrictionClass == nullptr) {
            // FindClass left a ClassNotFoundException/NoClassDefFoundError pending; it must be
            // cleared before any further JNI call on this thread or that call is undefined.
            env->ExceptionClear();
            ALOGE("RenderScriptToolit. Internal error. Could not find the Kotlin Range2d class.");
            isNull = true;
            return;
        }
        jfieldID startXId = env->GetFieldID(restrictionClass, "startX", "I");
        jfieldID startYId = env->GetFieldID(restrictionClass, "startY", "I");
        jfieldID endXId = env->GetFieldID(restrictionClass, "endX", "I");
        jfieldID endYId = env->GetFieldID(restrictionClass, "endY", "I");
        // A single missing field (e.g. after R8 renaming) leaves a NoSuchFieldError pending and a
        // null id; calling GetIntField with either is UB. Degrade to "no restriction" instead.
        if (startXId == nullptr || startYId == nullptr || endXId == nullptr || endYId == nullptr) {
            env->ExceptionClear();
            env->DeleteLocalRef(restrictionClass);
            ALOGE("RenderScriptToolit. Internal error. Could not find a Range2d field.");
            isNull = true;
            return;
        }
        restriction.startX = env->GetIntField(jRestriction, startXId);
        restriction.startY = env->GetIntField(jRestriction, startYId);
        restriction.endX = env->GetIntField(jRestriction, endXId);
        restriction.endY = env->GetIntField(jRestriction, endYId);
        env->DeleteLocalRef(restrictionClass);
    }

    Restriction *get() { return isNull ? nullptr : &restriction; }
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_skydoves_cloudy_internals_render_RenderScriptToolkit_createNative(JNIEnv * /*env*/,
                                                                              jobject /*thiz*/) {
    return reinterpret_cast<jlong>(new RenderScriptToolkit());
}

extern "C" JNIEXPORT void JNICALL
Java_com_skydoves_cloudy_internals_render_RenderScriptToolkit_destroyNative(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong native_handle) {
    RenderScriptToolkit *toolkit = reinterpret_cast<RenderScriptToolkit *>(native_handle);
    delete toolkit;
}

extern "C" JNIEXPORT void JNICALL
Java_com_skydoves_cloudy_internals_render_RenderScriptToolkit_nativeBlur(
        JNIEnv *env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array, jint vectorSize,
        jint size_x, jint size_y, jint radius, jbyteArray output_array, jobject restriction) {
    RenderScriptToolkit *toolkit = reinterpret_cast<RenderScriptToolkit *>(native_handle);

    // Validate arguments before touching the buffers: the kernels index in and out purely from
    // size_x/size_y/vectorSize, so bogus values (or arrays too small for those dimensions) would
    // read past the array bounds and crash. This path has no in-tree caller, so it must defend
    // itself against arbitrary external input.
    const bool hasValidDimensions = size_x > 0 && size_y > 0;
    const bool hasValidVectorSize =
            vectorSize == kVectorSizeA8 || vectorSize == kVectorSizeRgba8888;
    const bool hasValidRadius = radius > 0 && radius <= kMaxBlurRadius;
    if (!hasValidDimensions || !hasValidVectorSize || !hasValidRadius) {
        ALOGE("nativeBlur: invalid arguments (size_x=%d size_y=%d vectorSize=%d radius=%d)", size_x,
              size_y, vectorSize, radius);
        return;
    }

    // GetArrayLength returns jsize (int32_t), so no array can hold more than INT32_MAX bytes.
    // Reject any dimensions whose required byte count would exceed that before the final multiply,
    // which also keeps requiredBytes from overflowing int64_t (size_x*size_y alone can approach
    // 2^62, and the extra *vectorSize would push it past INT64_MAX into signed-overflow UB).
    const int64_t pixelCount = static_cast<int64_t>(size_x) * static_cast<int64_t>(size_y);
    if (pixelCount > INT32_MAX / vectorSize) {
        ALOGE("nativeBlur: dimensions too large (size_x=%d size_y=%d vectorSize=%d)", size_x, size_y,
              vectorSize);
        return;
    }
    const int64_t requiredBytes = pixelCount * vectorSize;
    const int64_t inputLength = env->GetArrayLength(input_array);
    const int64_t outputLength = env->GetArrayLength(output_array);
    if (inputLength < requiredBytes || outputLength < requiredBytes) {
        ALOGE("nativeBlur: array too small for dimensions (need %lld, input=%lld, output=%lld)",
              static_cast<long long>(requiredBytes), static_cast<long long>(inputLength),
              static_cast<long long>(outputLength));
        return;
    }

    RestrictionParameter restrict{env, restriction};
    ByteArrayGuard input{env, input_array};
    ByteArrayGuard output{env, output_array};
    if (!input.isValid() || !output.isValid()) {
        ALOGE("nativeBlur: failed to access input/output byte array");
        return;
    }

    toolkit->blur(input.get(), output.get(), size_x, size_y, vectorSize, radius, restrict.get());
}

extern "C" JNIEXPORT void JNICALL
Java_com_skydoves_cloudy_internals_render_RenderScriptToolkit_nativeBlurBitmap(
        JNIEnv *env, jobject /*thiz*/, jlong native_handle, jobject input_bitmap,
        jobject output_bitmap, jint radius, jobject restriction) {
    RenderScriptToolkit *toolkit = reinterpret_cast<RenderScriptToolkit *>(native_handle);
    RestrictionParameter restrict{env, restriction};
    BitmapGuard input{env, input_bitmap};
    BitmapGuard output{env, output_bitmap};
    if (!input.isValid() || !output.isValid()) {
        ALOGE("nativeBlurBitmap: invalid input/output bitmap");
        return;
    }

    toolkit->blur(input.get(), output.get(), input.width(), input.height(), input.vectorSize(),
                  radius, restrict.get());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_skydoves_cloudy_internals_render_RenderScriptToolkit_nativeBackgroundBlur(
        JNIEnv *env, jobject /*thiz*/, jlong native_handle,
        jobject src_bitmap, jobject dst_bitmap,
        jint cropX, jint cropY,
        jint radius, jfloat scale,
        jint progressiveDirection, jfloat fadeStart, jfloat fadeEnd) {
    RenderScriptToolkit *toolkit = reinterpret_cast<RenderScriptToolkit *>(native_handle);
    BitmapGuard src{env, src_bitmap};
    BitmapGuard dst{env, dst_bitmap};

    // Must precede vectorSize(): on an invalid guard bytesPerPixel is
    // uninitialized, so reading vectorSize() first would be UB.
    if (!src.isValid() || !dst.isValid()) {
        ALOGE("nativeBackgroundBlur: invalid src/dst bitmap");
        return JNI_FALSE;
    }

    if (src.vectorSize() != 4 || dst.vectorSize() != 4) {
        ALOGE("backgroundBlur requires ARGB_8888 bitmaps");
        return JNI_FALSE;
    }

    RenderScriptToolkit::ProgressiveDirection dir =
        static_cast<RenderScriptToolkit::ProgressiveDirection>(progressiveDirection);

    bool result = toolkit->backgroundBlur(
        src.get(), dst.get(),
        src.width(), src.height(),
        cropX, cropY,
        dst.width(), dst.height(),
        radius, scale,
        dir, fadeStart, fadeEnd
    );

    return result ? JNI_TRUE : JNI_FALSE;
}