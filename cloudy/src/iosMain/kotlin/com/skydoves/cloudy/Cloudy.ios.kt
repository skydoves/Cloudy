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
@file:OptIn(ExperimentalForeignApi::class)

package com.skydoves.cloudy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.Foundation.setValue
import platform.UIKit.UIImage

/**
 * iOS implementation of the cloudy modifier that applies blur effects to composables.
 * This is the actual implementation for the expect function declared in commonMain.
 * 
 * Uses Core Image filters for blur processing on iOS, with graphics layer content capture
 * to blur the actual composable content instead of placeholder images.
 * 
 * @param radius The blur radius in pixels. Higher values create more blur.
 * @param enabled Whether the blur effect is enabled. When false, returns the original modifier unchanged.
 * @param onStateChanged Callback that receives updates about the blur processing state.
 * @return Modified Modifier with blur effect applied.
 */
@Composable
public actual fun Modifier.cloudy(
  radius: Int,
  enabled: Boolean,
  onStateChanged: (CloudyState) -> Unit
): Modifier {
  if (!enabled) {
    return this
  }

  return this then CloudyModifierNodeElement(
    radius = radius,
    onStateChanged = onStateChanged
  )
}

private data class CloudyModifierNodeElement(
  val radius: Int = 10,
  val onStateChanged: (CloudyState) -> Unit = {}
) : ModifierNodeElement<CloudyModifierNode>() {

  override fun InspectorInfo.inspectableProperties() {
    name = "cloudy"
    properties["cloudy"] = radius
  }

  override fun create(): CloudyModifierNode = CloudyModifierNode(
    radius = radius,
    onStateChanged = onStateChanged
  )

  override fun update(node: CloudyModifierNode) {
    node.radius = radius
  }
}

/**
 * The actual modifier node that handles the blur drawing operations for iOS.
 * This class captures the actual composable content and applies Core Image blur filters.
 */
private class CloudyModifierNode(
  var radius: Int = 10,
  private val onStateChanged: (CloudyState) -> Unit = {}
) : DrawModifierNode, Modifier.Node() {

  private var cachedBlurredBitmap: PlatformBitmap? by mutableStateOf(null)

  override fun ContentDrawScope.draw() {
    val graphicsLayer = requireGraphicsContext().createGraphicsLayer()

    // Record the actual composable content into the graphics layer
    graphicsLayer.record {
      this@draw.drawContent()
    }

    // Draw the original content
    drawLayer(graphicsLayer)

    onStateChanged.invoke(CloudyState.Loading)

    // Launch blur processing in background
    // Note: We need to be careful about coroutine scope in Modifier.Node
    kotlinx.coroutines.MainScope().launch {
      try {
        val contentBitmap = graphicsLayer.toImageBitmap()
        
        val blurredBitmap = withContext(Dispatchers.Default) {
          createBlurredBitmapFromContent(contentBitmap, radius.toFloat())
        }

        cachedBlurredBitmap = blurredBitmap
        
        // Draw the blurred overlay
        blurredBitmap?.let { blurred ->
          val imageBitmap = blurred.toUIImage().asImageBitmap()
          
          imageBitmap?.let { bitmap ->
            drawImage(
              bitmap,
              topLeft = Offset.Zero
            )
          }
        }

        onStateChanged.invoke(CloudyState.Success(blurredBitmap))
      } catch (e: Exception) {
        onStateChanged.invoke(CloudyState.Error(e))
      } finally {
        requireGraphicsContext().releaseGraphicsLayer(graphicsLayer)
      }
    }
  }
}

/**
 * Creates a blurred bitmap from actual composable content using Core Image.
 * 
 * This function takes the captured content from a graphics layer and applies 
 * Gaussian blur using Core Image filters for optimal performance on iOS.
 * 
 * @param contentBitmap The actual content to blur, captured from graphics layer.
 * @param radius The blur radius to apply to the image.
 * @return A PlatformBitmap containing the blurred result, or null if the operation fails.
 */
private suspend fun createBlurredBitmapFromContent(contentBitmap: ImageBitmap, radius: Float): PlatformBitmap? {
  return withContext(Dispatchers.Default) {
    try {
      // Convert Compose ImageBitmap to UIImage
      // Note: This is a simplified conversion - in production you might need
      // a more sophisticated approach depending on your specific requirements
      val uiImage = contentBitmap.toUIImage()
      
      // Apply Gaussian blur to the actual content
      uiImage?.let { image ->
        applyGaussianBlur(image, radius)?.toPlatformBitmap()
      }
    } catch (_: Exception) {
      null
    }
  }
}

/**
 * Applies Gaussian blur to UIImage using Core Image filters.
 * 
 * This function converts the input UIImage to CIImage, applies a CIGaussianBlur filter
 * with the specified radius, and converts the result back to UIImage. Core Image
 * provides hardware-accelerated image processing on iOS devices.
 * 
 * @param image The source UIImage to blur.
 * @param radius The blur radius for the Gaussian blur filter.
 * @return The blurred UIImage, or null if the blur operation fails.
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
  } catch (_: Exception) {
    null
  }
}
