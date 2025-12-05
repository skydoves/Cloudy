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

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import com.skydoves.cloudy.internals.render.iterativeBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Blur strategy for API 30 and below that captures content into a bitmap
 * and applies the native iterative blur.
 */
internal object CloudyLegacyBlurStrategy : CloudyBlurStrategy {

  @SuppressLint("ModifierFactoryUnreferencedReceiver")
  @Composable
  override fun apply(
    modifier: Modifier,
    radius: Int,
    onStateChanged: (CloudyState) -> Unit,
    debugTag: String,
  ): Modifier = modifier.then(
    CloudyModifierNodeElement(
      radius = radius,
      onStateChanged = onStateChanged,
    ),
  )
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
    node.onUpdate()
    node.updateRadius(radius)
  }
}

/**
 * Modifier node responsible for bitmap capturing and CPU blur processing.
 */
private class CloudyModifierNode(
  radius: Int = 10,
  private val onStateChanged: (CloudyState) -> Unit = {},
) : Modifier.Node(),
  DrawModifierNode {

  var radius: Int = radius
    private set

  private var blurredBitmap: PlatformBitmap? = null
  private var cachedBlurRadius: Int = -1
  private var isProcessing: Boolean = false
  private var pendingInvalidateRequest: Boolean = false
  private var blurJob: Job? = null
  private var contentMayHaveChanged: Boolean = false

  fun updateRadius(newRadius: Int) {
    if (radius == newRadius) return
    radius = newRadius
    blurJob?.cancel()
    blurJob = null
    isProcessing = false
    pendingInvalidateRequest = false
    if (cachedBlurRadius != radius) {
      contentMayHaveChanged = true
    }
    if (isAttached) {
      invalidateDraw()
    }
  }

  fun onUpdate() {
    contentMayHaveChanged = true
    if (isProcessing) {
      pendingInvalidateRequest = true
    } else if (isAttached) {
      invalidateDraw()
    }
  }

  override fun ContentDrawScope.draw() {
    val graphicsContext = requireGraphicsContext()
    val graphicsLayer = graphicsContext.createGraphicsLayer()

    graphicsLayer.record {
      this@draw.drawContent()
    }

    if (radius <= 0) {
      drawLayer(graphicsLayer)
      graphicsContext.releaseGraphicsLayer(graphicsLayer)
      onStateChanged.invoke(CloudyState.Success.Applied)
      return
    }

    val cached = blurredBitmap
    if (cached != null && !cached.bitmap.isRecycled) {
      drawImage(cached.bitmap.asImageBitmap())
    } else {
      drawLayer(graphicsLayer)
    }

    if (cached != null && !cached.bitmap.isRecycled &&
      cachedBlurRadius == radius && !contentMayHaveChanged
    ) {
      graphicsContext.releaseGraphicsLayer(graphicsLayer)
      onStateChanged.invoke(CloudyState.Success.Captured(cached))
      return
    }

    if (!isProcessing) {
      isProcessing = true
      pendingInvalidateRequest = false
      onStateChanged.invoke(CloudyState.Loading)
      val currentRadius = radius
      val node = this@CloudyModifierNode

      blurJob = coroutineScope.launch(Dispatchers.Main) {
        try {
          val capturedBitmap: Bitmap = try {
            graphicsLayer.toImageBitmap().asAndroidBitmap()
          } catch (_: Exception) {
            return@launch
          }

          val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            capturedBitmap.config == Bitmap.Config.HARDWARE
          ) {
            try {
              capturedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            } catch (_: Exception) {
              null
            }
          } else {
            capturedBitmap
          }

          graphicsContext.releaseGraphicsLayer(graphicsLayer)

          if (softwareBitmap == null) {
            return@launch
          }

          val blurResult = withContext(Dispatchers.Default) {
            val outputBitmap: Bitmap =
              softwareBitmap.toPlatformBitmap().createCompatible().toAndroidBitmap()
            iterativeBlur(
              androidBitmap = softwareBitmap,
              outputBitmap = outputBitmap,
              radius = currentRadius,
            ).await()
          }

          val result = blurResult?.toPlatformBitmap()
            ?: throw RuntimeException("Blur processing returned null")

          if (node.isAttached) {
            val isEmpty = isTransparentBitmap(result.bitmap)
            if (isEmpty) {
              isProcessing = false
              contentMayHaveChanged = true
            } else {
              cachedBlurRadius = currentRadius
              blurredBitmap = result
              contentMayHaveChanged = false
              node.invalidateDraw()
              onStateChanged.invoke(CloudyState.Success.Captured(result))
            }
          }
        } catch (e: Exception) {
          if (!graphicsLayer.isReleased) {
            graphicsContext.releaseGraphicsLayer(graphicsLayer)
          }
          onStateChanged.invoke(CloudyState.Error(e))
        } finally {
          isProcessing = false
          blurJob = null
          if (pendingInvalidateRequest) {
            pendingInvalidateRequest = false
            if (node.isAttached) {
              node.invalidateDraw()
            }
          }
        }
      }
    } else {
      pendingInvalidateRequest = true
      graphicsContext.releaseGraphicsLayer(graphicsLayer)
    }
  }

  private fun isTransparentBitmap(bitmap: Bitmap, grid: Int = 4): Boolean {
    if (bitmap.width == 0 || bitmap.height == 0) return true
    val maxX = bitmap.width - 1
    val maxY = bitmap.height - 1
    var nonZeroAlpha = 0

    for (row in 0 until grid) {
      val y = (row.toFloat() / (grid - 1) * maxY).toInt().coerceIn(0, maxY)
      for (col in 0 until grid) {
        val x = (col.toFloat() / (grid - 1) * maxX).toInt().coerceIn(0, maxX)
        val pixel = bitmap.getPixel(x, y)
        val alpha = (pixel shr 24) and 0xFF
        if (alpha > 0) nonZeroAlpha++
      }
    }
    return nonZeroAlpha == 0
  }
}
