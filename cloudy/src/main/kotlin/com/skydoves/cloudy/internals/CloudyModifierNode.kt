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
package com.skydoves.cloudy.internals

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.offset
import com.skydoves.cloudy.CloudyState
import com.skydoves.cloudy.internals.render.iterativeBlur
import kotlinx.coroutines.runBlocking

@Composable
public fun Modifier.cloudy(
  radius: Int = 10,
  onStateChanged: (CloudyState) -> Unit = {}
): Modifier {
  return this then CloudyModifierNodeElement(
    graphicsLayer = rememberGraphicsLayer(),
    radius = radius,
    onStateChanged = onStateChanged
  )
}

private data class CloudyModifierNodeElement(
  private val graphicsLayer: GraphicsLayer,
  val radius: Int = 10,
  val onStateChanged: (CloudyState) -> Unit = {}
) : ModifierNodeElement<CloudyModifierNode>() {

  override fun InspectorInfo.inspectableProperties() {
    name = "cloudy"
    properties["cloudy"] = radius
  }

  override fun create(): CloudyModifierNode = CloudyModifierNode(
    graphicsLayer = graphicsLayer,
    radius = radius,
    onStateChanged = onStateChanged
  )

  override fun update(node: CloudyModifierNode) {
    node.radius = radius
  }
}

private class CloudyModifierNode(
  val graphicsLayer: GraphicsLayer,
  var radius: Int = 10,
  private val onStateChanged: (CloudyState) -> Unit = {}
) : LayoutModifierNode, GlobalPositionAwareModifierNode, DrawModifierNode, Modifier.Node() {

  private var layoutInfo = LayoutInfo()

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    layoutInfo = LayoutInfo(
      xOffset = coordinates.positionInWindow().x.toInt(),
      yOffset = coordinates.positionInWindow().y.toInt(),
      width = coordinates.size.width,
      height = coordinates.size.height
    )
  }

  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints
  ): MeasureResult {
    val placeable = measurable.measure(constraints.offset())
    return layout(placeable.width, placeable.height) {
      placeable.placeRelative(0, 0)
    }
  }

  override fun ContentDrawScope.draw() {
    // call record to capture the content in the graphics layer
    graphicsLayer.record {
      // draw the contents of the composable into the graphics layer
      this@draw.drawContent()
    }

    onStateChanged.invoke(CloudyState.Loading)

    try {
      val targetBitmap: Bitmap = runBlocking {
        graphicsLayer.toImageBitmap().asAndroidBitmap()
          .copy(Bitmap.Config.ARGB_8888, true)
      } ?: throw RuntimeException("Couldn't capture a bitmap from the composable tree")

      val blurredBitmap = iterativeBlur(
        androidBitmap = targetBitmap,
        radius = radius
      )?.apply {
        drawImage(this.asImageBitmap())
      }

      onStateChanged.invoke(CloudyState.Success(blurredBitmap))
    } catch (e: Exception) {
      onStateChanged.invoke(CloudyState.Error(e))
    }
  }
}
