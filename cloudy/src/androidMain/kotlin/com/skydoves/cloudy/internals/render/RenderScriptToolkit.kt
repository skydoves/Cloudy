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
@file:Suppress("unused")

package com.skydoves.cloudy.internals.render

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

// This string is used for error messages.
private const val externalName = "RenderScript Toolkit"

/**
 * A collection of high-performance graphic utility functions like blur and blend.
 *
 * This toolkit provides ten image manipulation functions: blend, blur, color matrix, convolve,
 * histogram, histogramDot, lut, lut3d, resize, and YUV to RGB. These functions execute
 * multithreaded on the CPU.
 *
 * Most of the functions have two variants: one that manipulates Bitmaps, the other ByteArrays.
 * For ByteArrays, you need to specify the width and height of the data to be processed, as
 * well as the number of bytes per pixel. For most use cases, this will be 4.
 *
 * The Toolkit creates a thread pool that's used for processing the functions. The threads live
 * for the duration of the application. They can be destroyed by calling the method shutdown().
 *
 * This library is thread safe. You can call methods from different poolThreads. The functions will
 * execute sequentially.
 *
 * A native C++ version of this Toolkit is available. Check the RenderScriptToolkit.h file in the
 * cpp directory.
 *
 * This toolkit can be used as a replacement for most RenderScript Intrinsic functions. Compared
 * to RenderScript, it's simpler to use and more than twice as fast on the CPU. However RenderScript
 * Intrinsics allow more flexibility for the type of allocation supported. In particular, this
 * toolkit does not support allocations of floats.
 */
public object RenderScriptToolkit {
  /**
   * Blurs an image.
   *
   * Performs a Gaussian blur of an image and returns result in a ByteArray buffer. A variant of
   * this method is available to blur Bitmaps.
   *
   * The radius determines which pixels are used to compute each blurred pixels. This Toolkit
   * accepts values between 1 and 25. Larger values create a more blurred effect but also
   * take longer to compute. When the radius extends past the edge, the edge pixel will
   * be used as replacement for the pixel that's out off boundary.
   *
   * Each input pixel can either be represented by four bytes (RGBA format) or one byte
   * for the less common blurring of alpha channel only image.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY. NOTE: The output buffer will still be full size, with the
   * section that's not blurred all set to 0. This is to stay compatible with RenderScript.
   *
   * The source buffer should be large enough for sizeX * sizeY * mVectorSize bytes. It has a
   * row-major layout.
   *
   * @param inputArray The buffer of the image to be blurred.
   * @param vectorSize Either 1 or 4, the number of bytes in each cell, i.e. A vs. RGBA.
   * @param sizeX The width of both buffers, as a number of 1 or 4 byte cells.
   * @param sizeY The height of both buffers, as a number of 1 or 4 byte cells.
   * @param radius The radius of the pixels used to blur, a value from 1 to 25.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The blurred pixels, a ByteArray of size.
   */
  @JvmOverloads
  internal fun blur(
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    radius: Int = 5,
    restriction: Range2d? = null
  ): ByteArray {
    require(vectorSize == 1 || vectorSize == 4) {
      "$externalName blur. The vectorSize should be 1 or 4. $vectorSize provided."
    }
    require(inputArray.size >= sizeX * sizeY * vectorSize) {
      "$externalName blur. inputArray is too small for the given dimensions. " + "$sizeX*$sizeY*$vectorSize < ${inputArray.size}."
    }
    require(radius in 1..25) {
      "$externalName blur. The radius should be between 1 and 25. $radius provided."
    }
    validateRestriction("blur", sizeX, sizeY, restriction)

    val outputArray = ByteArray(inputArray.size)
    nativeBlur(
      nativeHandle,
      inputArray,
      vectorSize,
      sizeX,
      sizeY,
      radius,
      outputArray,
      restriction
    )
    return outputArray
  }

  /**
   * Blurs an image.
   *
   * Performs a Gaussian blur of a Bitmap and returns result as a Bitmap. A variant of
   * this method is available to blur ByteArrays.
   *
   * The radius determines which pixels are used to compute each blurred pixels. This Toolkit
   * accepts values between 1 and 25. Larger values create a more blurred effect but also
   * take longer to compute. When the radius extends past the edge, the edge pixel will
   * be used as replacement for the pixel that's out off boundary.
   *
   * This method supports input Bitmap of config ARGB_8888 and ALPHA_8. Bitmaps with a stride
   * different than width * vectorSize are not currently supported. The returned Bitmap has the
   * same config.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY. NOTE: The output Bitmap will still be full size, with the
   * section that's not blurred all set to 0. This is to stay compatible with RenderScript.
   *
   * @param inputBitmap The buffer of the image to be blurred.
   * @return The blurred Bitmap.
   */
  @JvmOverloads
  public fun blur(
    inputBitmap: Bitmap?,
    outputBitmap: Bitmap,
    @androidx.annotation.IntRange(from = 0, to = 25) radius: Int = 5,
    restriction: Range2d? = null
  ): Bitmap? {
    inputBitmap ?: return null
    validateBitmap("blur", inputBitmap)
    if (radius == 0) return inputBitmap
    require(radius in 1..25) {
      "$externalName blur. The radius should be between 1 and 25. $radius provided."
    }
    validateRestriction("blur", inputBitmap.width, inputBitmap.height, restriction)

    nativeBlurBitmap(nativeHandle, inputBitmap, outputBitmap, radius, restriction)
    return outputBitmap
  }

  /**
   * Identity matrix that can be passed to the {@link RenderScriptToolkit::colorMatrix} method.
   *
   * Using this matrix will result in no change to the pixel through multiplication although
   * the pixel value can still be modified by the add vector, or transformed to a different
   * format.
   */
  internal val identityMatrix: FloatArray
    get() = floatArrayOf(
      1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f
    )

  private var nativeHandle: Long = 0

  init {
    System.loadLibrary("renderscript-toolkit")
    nativeHandle = createNative()
  }

  /**
   * Shutdown the thread pool.
   *
   * Waits for the threads to complete their work and destroys them.
   *
   * An application should call this method only if it is sure that it won't call the
   * toolkit again, as it is irreversible.
   */
  internal fun shutdown() {
    destroyNative(nativeHandle)
    nativeHandle = 0
  }

  private external fun createNative(): Long

  private external fun destroyNative(nativeHandle: Long)

  private external fun nativeBlur(
    nativeHandle: Long,
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    radius: Int,
    outputArray: ByteArray,
    restriction: Range2d?
  )

  private external fun nativeBlurBitmap(
    nativeHandle: Long,
    inputBitmap: Bitmap,
    outputBitmap: Bitmap,
    radius: Int,
    restriction: Range2d?
  )
}

/**
 * Define a range of data to process.
 *
 * This class is used to restrict a [RenderScriptToolkit] operation to a rectangular subset of the input
 * tensor.
 *
 * @property startX The index of the first value to be included on the X axis.
 * @property endX The index after the last value to be included on the X axis.
 * @property startY The index of the first value to be included on the Y axis.
 * @property endY The index after the last value to be included on the Y axis.
 */
public data class Range2d(
  val startX: Int,
  val endX: Int,
  val startY: Int,
  val endY: Int
) {
  internal constructor() : this(0, 0, 0, 0)
}

/**
 * A collection of 3D RGBA data values with spatial dimensions.
 * This class provides indexed access to RGBA byte values in a 3D array structure.
 * @property values The byte array containing RGBA data (4 bytes per pixel).
 * @property sizeX The width dimension.
 * @property sizeY The height dimension.
 * @property sizeZ The depth dimension.
 */
internal class Rgba3dArray(val values: ByteArray, val sizeX: Int, val sizeY: Int, val sizeZ: Int) {
  init {
    require(values.size >= sizeX * sizeY * sizeZ * 4)
  }

  operator fun get(x: Int, y: Int, z: Int): ByteArray {
    val index = indexOfVector(x, y, z)
    return ByteArray(4) { values[index + it] }
  }

  operator fun set(x: Int, y: Int, z: Int, value: ByteArray) {
    require(value.size == 4)
    val index = indexOfVector(x, y, z)
    for (i in 0..3) {
      values[index + i] = value[i]
    }
  }

  /**
   * Calculates the starting index in the underlying byte array for the RGBA vector at the specified (x, y, z) coordinates.
   *
   * @param x The x-coordinate within the 3D array.
   * @param y The y-coordinate within the 3D array.
   * @param z The z-coordinate within the 3D array.
   * @return The index of the first byte of the RGBA vector at the given coordinates.
   * @throws IllegalArgumentException if any coordinate is out of bounds.
   */
  private fun indexOfVector(x: Int, y: Int, z: Int): Int {
    require(x in 0 until sizeX)
    require(y in 0 until sizeY)
    require(z in 0 until sizeZ)
    return ((z * sizeY + y) * sizeX + x) * 4
  }
}

/**
 * Validates that a bitmap is compatible with RenderScriptToolkit operations.
 *
 * Checks that the bitmap uses a supported configuration (ARGB_8888, or ALPHA_8 if allowed) and that its row stride matches the expected value for its width and pixel format.
 *
 * @param function The name of the calling function, used for error reporting.
 * @param inputBitmap The bitmap to validate.
 * @param alphaAllowed Whether ALPHA_8 bitmaps are permitted in addition to ARGB_8888.
 * @throws IllegalArgumentException If the bitmap configuration or row stride is invalid.
 */
internal fun validateBitmap(
  function: String,
  inputBitmap: Bitmap,
  alphaAllowed: Boolean = true
) {
  if (alphaAllowed) {
    require(
      inputBitmap.config == Bitmap.Config.ARGB_8888 || inputBitmap.config == Bitmap.Config.ALPHA_8
    ) {
      "$externalName. $function supports only ARGB_8888 and ALPHA_8 bitmaps. " + "${inputBitmap.config} provided."
    }
  } else {
    require(inputBitmap.config == Bitmap.Config.ARGB_8888) {
      "$externalName. $function supports only ARGB_8888. " + "${inputBitmap.config} provided."
    }
  }
  require(inputBitmap.width * vectorSize(inputBitmap) == inputBitmap.rowBytes) {
    "$externalName $function. Only bitmaps with rowSize equal to the width * vectorSize are " + "currently supported. Provided were rowBytes=${inputBitmap.rowBytes}, " + "width={${inputBitmap.width}, and vectorSize=${
    vectorSize(
      inputBitmap
    )
    }."
  }
}

/**
 * Creates a new bitmap with the same dimensions and configuration as the given bitmap.
 *
 * @param inputBitmap The bitmap to match in size and configuration.
 * @return A new bitmap instance compatible with the input bitmap.
 */
internal fun createCompatibleBitmap(inputBitmap: Bitmap) =
  Bitmap.createBitmap(inputBitmap.width, inputBitmap.height, inputBitmap.config!!)

/**
 * Validates that a given restriction range is within the bounds of the specified data dimensions.
 *
 * Throws an IllegalArgumentException if the restriction is out of bounds or if the start indices are not less than the end indices.
 */
internal fun validateRestriction(
  tag: String,
  sizeX: Int,
  sizeY: Int,
  restriction: Range2d? = null
) {
  if (restriction == null) return
  require(restriction.startX < sizeX && restriction.endX <= sizeX) {
    "$externalName $tag. sizeX should be greater than restriction.startX and greater " + "or equal to restriction.endX. $sizeX, ${restriction.startX}, " + "and ${restriction.endX} were provided respectively."
  }
  require(restriction.startY < sizeY && restriction.endY <= sizeY) {
    "$externalName $tag. sizeY should be greater than restriction.startY and greater " + "or equal to restriction.endY. $sizeY, ${restriction.startY}, " + "and ${restriction.endY} were provided respectively."
  }
  require(restriction.startX < restriction.endX) {
    "$externalName $tag. Restriction startX should be less than endX. " + "${restriction.startX} and ${restriction.endX} were provided respectively."
  }
  require(restriction.startY < restriction.endY) {
    "$externalName $tag. Restriction startY should be less than endY. " + "${restriction.startY} and ${restriction.endY} were provided respectively."
  }
}

/**
 * Determines the number of bytes per pixel for a supported bitmap configuration.
 *
 * Returns 4 for ARGB_8888 and 1 for ALPHA_8 bitmap configs.
 *
 * @param bitmap The bitmap whose pixel size is to be determined.
 * @return The number of bytes per pixel.
 * @throws IllegalArgumentException If the bitmap configuration is not ARGB_8888 or ALPHA_8.
 */
internal fun vectorSize(bitmap: Bitmap): Int {
  return when (bitmap.config) {
    Bitmap.Config.ARGB_8888 -> 4
    Bitmap.Config.ALPHA_8 -> 1
    else -> throw IllegalArgumentException(
      "$externalName. Only ARGB_8888 and ALPHA_8 Bitmap are supported."
    )
  }
}

/**
 * Performs Gaussian blur on a bitmap with a radius greater than 25 by applying multiple blur passes asynchronously.
 *
 * The blur operation is split into several passes of radius 25 and a final pass for any remainder, as the underlying blur implementation supports a maximum radius of 25 per pass.
 *
 * @param androidBitmap The source bitmap to blur.
 * @param outputBitmap The bitmap where the blurred result is written.
 * @param radius The total blur radius to achieve.
 * @return A Deferred resolving to the blurred bitmap, or null if the operation fails.
 */
internal fun CoroutineScope.iterativeBlur(
  androidBitmap: Bitmap,
  outputBitmap: Bitmap,
  radius: Int
): Deferred<Bitmap?> = async {
  val iterate = radius / 25
  val remainder = radius % 25

  var bitmap: Bitmap? = if (remainder == 0) {
    androidBitmap
  } else {
    RenderScriptToolkit.blur(
      inputBitmap = androidBitmap,
      outputBitmap = outputBitmap,
      radius = remainder
    )
  }

  repeat(iterate) {
    bitmap = RenderScriptToolkit.blur(
      inputBitmap = bitmap,
      outputBitmap = outputBitmap,
      radius = 25
    )
  }

  bitmap
}
