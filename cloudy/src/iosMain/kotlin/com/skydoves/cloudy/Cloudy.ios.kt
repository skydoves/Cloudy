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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.filterWithName
import platform.Foundation.setValue
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage

@Composable
public actual fun Modifier.cloudy(
  radius: Int,
  enabled: Boolean,
  onStateChanged: (CloudyState) -> Unit
): Modifier {
  if (!enabled) {
    return this
  }

  var cachedBlurredBitmap by remember { mutableStateOf<PlatformBitmap?>(null) }
  var isProcessing by remember { mutableStateOf(false) }

  return this.drawWithContent {
    drawContent()

    cachedBlurredBitmap?.let { blurred ->
      // Draw the cached blurred version
      drawIntoCanvas { canvas ->
        val bitmap = blurred.image.asComposeImageBitmap()
        canvas.drawImage(bitmap, topLeft = androidx.compose.ui.geometry.Offset.Zero)
      }
    }
  }.also {
    LaunchedEffect(radius, size) {
      if (!isProcessing) {
        isProcessing = true
        onStateChanged(CloudyState.Loading)

        try {
          val blurredBitmap = withContext(Dispatchers.Default) {
            createBlurredBitmap(radius.toFloat())
          }

          cachedBlurredBitmap = blurredBitmap
          onStateChanged(CloudyState.Success(blurredBitmap))
        } catch (e: Exception) {
          onStateChanged(CloudyState.Error(e))
        } finally {
          isProcessing = false
        }
      }
    }
  }
}

/**
 * Creates a blurred bitmap using Core Image for optimal performance on iOS.
 */
private suspend fun createBlurredBitmap(radius: Float): PlatformBitmap? {
  return withContext(Dispatchers.Default) {
    try {
      // Create a simple colored image for demonstration
      // In a real implementation, you would capture the actual content
      val size = CGSizeMake(100.0, 100.0)
      UIGraphicsBeginImageContextWithOptions(size, false, 0.0)

      val context = UIGraphicsGetCurrentContext()
      context?.let { ctx ->
        // Fill with a semi-transparent color
        platform.CoreGraphics.CGContextSetRGBFillColor(ctx, 0.5, 0.5, 0.5, 0.8)
        platform.CoreGraphics.CGContextFillRect(ctx, CGRectMake(0.0, 0.0, 100.0, 100.0))
      }

      val baseImage = UIGraphicsGetImageFromCurrentImageContext()
      UIGraphicsEndImageContext()

      baseImage?.let { image ->
        applyGaussianBlur(image, radius)?.toPlatformBitmap()
      }
    } catch (e: Exception) {
      null
    }
  }
}

/**
 * Applies Gaussian blur to UIImage using Core Image.
 */
private fun applyGaussianBlur(image: UIImage, radius: Float): UIImage? {
  return try {
    // Convert UIImage to CIImage
    val ciImage = CIImage.imageWithCGImage(image.CGImage ?: return null)

    // Create Gaussian blur filter
    val blurFilter = CIFilter.filterWithName("CIGaussianBlur") ?: return null
    blurFilter.setValue(ciImage, forKey = "inputImage")
    blurFilter.setValue(radius, forKey = "inputRadius")

    // Get output image
    val outputImage = blurFilter.outputImage ?: return null

    // Create context and render
    val context = CIContext()
    val cgImage = context.createCGImage(outputImage, fromRect = outputImage.extent)

    cgImage?.let { UIImage.imageWithCGImage(it) }
  } catch (e: Exception) {
    null
  }
}
