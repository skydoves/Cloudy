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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
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
 * This is the actual implementation for the expect function declared in commonMain.
 * * For Android 12+ devices in preview mode, it falls back to the platform's blur modifier.
 * For runtime execution, it uses a custom implementation with graphics layers and
 * native iterative blur processing for optimal performance.
 * * The implementation captures the composable content in a graphics layer, applies
 * iterative blur processing using native code, and overlays the result.
 * * @param radius The blur radius in pixels (1-25). Higher values create more blur but take longer to process.
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
  require(radius >= 0) { "Blur radius must be non-negative, but was $radius" }

  if (!enabled) {
    return this
  }

  // This local inspection preview only works over Android 12.
  if (LocalInspectionMode.current) {
    return this.blur(radius = radius.dp)
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
 * The actual modifier node that handles the blur drawing operations.
 * This class implements the core logic for capturing composable content,
 * applying blur effects, and managing the rendering lifecycle.
 * * @property radius The blur radius to apply (mutable for updates).
 * @property onStateChanged Callback function for state change notifications.
 */
private class CloudyModifierNode(
  var radius: Int = 10,
  private val onStateChanged: (CloudyState) -> Unit = {}
) : DrawModifierNode, Modifier.Node() {

  private var cachedOutput: PlatformBitmap? by mutableStateOf(null)

  override fun ContentDrawScope.draw() {
    val graphicsLayer = requireGraphicsContext().createGraphicsLayer()

    // call record to capture the content in the graphics layer
    graphicsLayer.record {
      // draw the contents of the composable into the graphics layer
      this@draw.drawContent()
    }

    drawLayer(graphicsLayer)

    onStateChanged.invoke(CloudyState.Loading)

    coroutineScope.launch(Dispatchers.Main.immediate) {
      try {
        val targetBitmap: Bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
          .copy(Bitmap.Config.ARGB_8888, true)

        val out = if (cachedOutput == null || cachedOutput?.width != targetBitmap.width || cachedOutput?.height != targetBitmap.height
        ) {
          // Dispose previous cached output
          cachedOutput?.dispose()

          targetBitmap.toPlatformBitmap().createCompatible().also { cachedOutput = it }
        } else {
          cachedOutput!!
        }

        val blurredBitmap = iterativeBlur(
          androidBitmap = targetBitmap,
          outputBitmap = out.toAndroidBitmap(),
          radius = radius
        ).await()?.let { bitmap ->
          bitmap.toPlatformBitmap().also {
            drawImage(bitmap.asImageBitmap())
          }
        } ?: throw RuntimeException("Failed to capture bitmap from composable tree: blur processing returned null")

        onStateChanged.invoke(CloudyState.Success(blurredBitmap))
      } catch (e: Exception) {
        onStateChanged.invoke(CloudyState.Error(e))
      } finally {
        requireGraphicsContext().releaseGraphicsLayer(graphicsLayer)
      }
    }
  }
}
