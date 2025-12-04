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

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.skydoves.cloudy.internals.render.iterativeBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android implementation of the cloudy modifier that applies blur effects to composables.
 *
 * This implementation uses different blur strategies based on the Android API level:
 *
 * ## API 31+ (Android 12+)
 * Uses [RenderEffect.createBlurEffect] for GPU-accelerated blur processing.
 * - Processed on the RenderThread (no UI thread blocking)
 * - No bitmap extraction for maximum performance
 * - Returns [CloudyState.Success.Applied]
 *
 * ## API 30 and below
 * Uses native C++ iterative blur with graphics layer capture.
 * - CPU-based processing with NEON/SIMD optimizations
 * - Captures composable content and applies blur
 * - Returns [CloudyState.Success.Captured] with the blurred bitmap
 *
 * ## Sigma Conversion
 * The blur radius is converted to sigma using: `sigma = radius / 2.0`
 *
 * @param radius The blur radius in pixels. Higher values create more blur.
 * @param enabled Whether the blur effect is enabled. When false, returns the original modifier unchanged.
 * @param onStateChanged Callback that receives updates about the blur processing state.
 * @return Modified Modifier with blur effect applied.
 */
@Composable
public actual fun Modifier.cloudy(
  @IntRange(from = 0) radius: Int,
  enabled: Boolean,
  onStateChanged: (CloudyState) -> Unit,
): Modifier {
  require(radius >= 0) { "Blur radius must be non-negative, but was $radius" }

  if (!enabled) {
    return this
  }

  // This local inspection preview only works over Android 12.
  if (LocalInspectionMode.current) {
    return this.blur(radius = radius.dp)
  }

  // API 31+ (Android 12+): Use RenderEffect for GPU-accelerated blur
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    return this.cloudyWithRenderEffect(radius, onStateChanged)
  }

  // API 30 and below: Use native CPU blur with bitmap capture
  return this then CloudyModifierNodeElement(
    radius = radius,
    onStateChanged = onStateChanged,
  )
}

/**
 * Applies GPU-accelerated blur using RenderEffect (API 31+).
 *
 * This implementation operates directly in the rendering pipeline without
 * extracting bitmaps, providing optimal performance.
 */
@Composable
private fun Modifier.cloudyWithRenderEffect(
  radius: Int,
  onStateChanged: (CloudyState) -> Unit,
): Modifier {
  if (radius == 0) {
    onStateChanged.invoke(CloudyState.Success.Applied)
    return this
  }

  // Convert radius to sigma: sigma = radius / 2.0
  val sigma = radius / 2.0f

  // Notify that GPU blur is being applied (no bitmap available)
  onStateChanged.invoke(CloudyState.Success.Applied)

  return this.graphicsLayer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      renderEffect = RenderEffect
        .createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP)
        .asComposeRenderEffect()
    }
  }
}

private data class CloudyModifierNodeElement(
  val radius: Int = 10,
  val onStateChanged: (CloudyState) -> Unit = {},
) : ModifierNodeElement<CloudyModifierNode>() {

  override fun InspectorInfo.inspectableProperties() {
    name = "cloudy"
    properties["cloudy"] = radius
  }

  override fun create(): CloudyModifierNode = CloudyModifierNode(
    radius = radius,
    onStateChanged = onStateChanged,
  )

  override fun update(node: CloudyModifierNode) {
    node.radius = radius
  }
}

/**
 * The modifier node for CPU-based blur processing (API 30 and below).
 *
 * This class captures composable content using graphics layers,
 * applies iterative blur using native C++ code, and returns the
 * blurred bitmap via [CloudyState.Success.Captured].
 */
private class CloudyModifierNode(
  var radius: Int = 10,
  private val onStateChanged: (CloudyState) -> Unit = {},
) : Modifier.Node(),
  DrawModifierNode {

  private var cachedOutput: PlatformBitmap? by mutableStateOf(null)

  override fun ContentDrawScope.draw() {
    val graphicsLayer = requireGraphicsContext().createGraphicsLayer()

    graphicsLayer.record {
      this@draw.drawContent()
    }

    drawLayer(graphicsLayer)

    if (radius <= 0) {
      onStateChanged.invoke(CloudyState.Success.Applied)
      requireGraphicsContext().releaseGraphicsLayer(graphicsLayer)
      return
    }

    onStateChanged.invoke(CloudyState.Loading)

    coroutineScope.launch(Dispatchers.Main.immediate) {
      try {
        val targetBitmap: Bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
          .copy(Bitmap.Config.ARGB_8888, true)

        val out = if (cachedOutput == null || cachedOutput?.width != targetBitmap.width ||
          cachedOutput?.height != targetBitmap.height
        ) {
          cachedOutput?.dispose()
          targetBitmap.toPlatformBitmap().createCompatible().also { cachedOutput = it }
        } else {
          cachedOutput!!
        }

        val blurredBitmap = iterativeBlur(
          androidBitmap = targetBitmap,
          outputBitmap = out.toAndroidBitmap(),
          radius = radius,
        ).await()?.let { bitmap ->
          bitmap.toPlatformBitmap().also {
            drawImage(bitmap.asImageBitmap())
          }
        }
          ?: throw RuntimeException(
            "Failed to capture bitmap from composable tree: blur processing returned null",
          )

        // Return Captured state with the blurred bitmap
        onStateChanged.invoke(CloudyState.Success.Captured(blurredBitmap))
      } catch (e: Exception) {
        onStateChanged.invoke(CloudyState.Error(e))
      } finally {
        requireGraphicsContext().releaseGraphicsLayer(graphicsLayer)
      }
    }
  }
}
