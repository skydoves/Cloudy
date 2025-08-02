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

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * Uses Core Image filters for blur processing on iOS, with graphics layer content capture
 * to blur the actual composable content instead of placeholder images.
 * @param radius The blur radius in pixels. Higher values create more blur.
 * @param enabled Whether the blur effect is enabled. When false, returns the original modifier unchanged.
 * @param onStateChanged Callback that receives updates about the blur processing state.
 * @return Modified Modifier with blur effect applied.
 */
@Composable
public actual fun Modifier.cloudy(
  @IntRange(from = 0) radius: Int,
  enabled: Boolean,
  onStateChanged: (CloudyState) -> Unit
): Modifier {
  require(radius >= 0) { "Blur radius must be non-negative, but was $radius" }

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

  /**
   * Sets up inspectable properties for the "cloudy" modifier, exposing the blur radius for inspection tools.
   */
  override fun InspectorInfo.inspectableProperties() {
    name = "cloudy"
    properties["cloudy"] = radius
  }

  /**
   * Creates a new instance of `CloudyModifierNode` with the specified blur radius and state change callback.
   *
   * @return A `CloudyModifierNode` configured with the current radius and callback.
   */
  override fun create(): CloudyModifierNode = CloudyModifierNode(
    radius = radius,
    onStateChanged = onStateChanged
  )

  /**
   * Updates the blur radius of the given [CloudyModifierNode] to match the current value.
   *
   * @param node The modifier node whose blur radius will be updated.
   */
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

  /**
   * Captures the composable content, draws it, and asynchronously applies a blur effect using Core Image.
   *
   * Notifies the blur processing state via the `onStateChanged` callback, including loading, success with the blurred bitmap, or error states.
   * Ensures proper graphics layer management and lifecycle-aware coroutine usage.
   */
  override fun ContentDrawScope.draw() {
    val graphicsLayer = requireGraphicsContext().createGraphicsLayer()

    // Record the actual composable content into the graphics layer
    graphicsLayer.record {
      // Draw the contents of the composable into the graphics layer
      this@draw.drawContent()
    }

    // Draw the original content
    drawLayer(graphicsLayer)

    onStateChanged.invoke(CloudyState.Loading)

    // Use the node's coroutine scope for proper lifecycle management
    coroutineScope.launch(Dispatchers.Main.immediate) {
      try {
        val contentBitmap = graphicsLayer.toImageBitmap()

        val blurredBitmap = withContext(Dispatchers.Default) {
          createBlurredBitmapFromContent(contentBitmap, radius.toFloat())
        }?.let { bitmap ->
          bitmap.also {
            // Draw the blurred result
            val imageBitmap = it.toUIImage().asImageBitmap()
            imageBitmap?.let { bmp -> drawImage(bmp) }
          }
        } ?: throw RuntimeException("Couldn't capture a bitmap from the composable tree")

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
 * Generates a blurred bitmap from the provided composable content using Core Image Gaussian blur.
 *
 * Converts the given `ImageBitmap` to a `UIImage`, applies a Gaussian blur with the specified radius,
 * and returns the resulting blurred image as a `PlatformBitmap`. Returns null if any step fails.
 *
 * @param contentBitmap The composable content to blur.
 * @param radius The blur radius to apply.
 * @return The blurred bitmap, or null if the blur operation fails.
 */
private suspend fun createBlurredBitmapFromContent(
  contentBitmap: ImageBitmap,
  radius: Float
): PlatformBitmap? {
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
 * Applies a Gaussian blur to the given UIImage using Core Image.
 *
 * Converts the input image to a CIImage, applies a CIGaussianBlur filter with the specified radius,
 * and returns the resulting blurred UIImage. Returns null if the blur operation fails at any step.
 *
 * @param image The UIImage to blur.
 * @param radius The blur radius to use for the Gaussian blur filter.
 * @return The blurred UIImage, or null if the operation fails.
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
