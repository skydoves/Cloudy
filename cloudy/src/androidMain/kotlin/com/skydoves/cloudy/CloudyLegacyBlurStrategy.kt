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
import kotlin.coroutines.cancellation.CancellationException

/**
 * Blur strategy for API 30 and below that captures content into a bitmap
 * and applies the native iterative blur.
 */
internal object CloudyLegacyBlurStrategy : CloudyBlurStrategy {

  /**
   * Attach a legacy blur modifier (for API 30 and below) configured with the given radius and state callback.
   *
   * @param radius The blur radius to apply.
   * @param onStateChanged Callback invoked with blur processing state updates.
   * @return The original [Modifier] with a `CloudyModifierNodeElement` appended that applies the configured blur.
   */
  @SuppressLint("ModifierFactoryUnreferencedReceiver")
  @Composable
  override fun apply(
    modifier: Modifier,
    radius: Int,
    onStateChanged: (CloudyState) -> Unit,
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

  /**
   * Registers inspector metadata for the cloudy modifier.
   *
   * Sets the inspector `name` to "cloudy" and exposes a `cloudy` property containing the current `radius`.
   */
  override fun InspectorInfo.inspectableProperties() {
    name = "cloudy"
    properties["cloudy"] = radius
  }

  /**
   * Create a CloudyModifierNode configured with this element's radius and state callback.
   *
   * @return A CloudyModifierNode initialized with the element's `radius` and `onStateChanged` callback.
   */
  override fun create(): CloudyModifierNode = CloudyModifierNode(
    radius = radius,
    onStateChanged = onStateChanged,
  )

  /**
   * Synchronizes the provided node with this element's state.
   *
   * Updates the node's content-change state and sets its blur radius to match this element.
   *
   * @param node The CloudyModifierNode to update.
   */
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

  /**
   * Draws the modifier's content, applying a blur when requested and managing asynchronous capture, caching, and state callbacks.
   *
   * If the configured radius is less than or equal to zero, the content is drawn directly without blur and no state callback is invoked.
   * When a positive radius is set, a cached blurred bitmap is used when valid; otherwise the current content is captured and an asynchronous CPU blur is scheduled.
   * During processing the state is reported as Loading; on successful capture the state is reported as Success.Captured with the resulting bitmap; on failure the state is reported as Error.
   *
   * The method updates internal cache state, may request redraws, and launches background work to produce blurred results when needed.
   */
  override fun ContentDrawScope.draw() {
    val graphicsContext = requireGraphicsContext()
    val graphicsLayer = graphicsContext.createGraphicsLayer()

    graphicsLayer.record {
      this@draw.drawContent()
    }

    if (radius <= 0) {
      drawLayer(graphicsLayer)
      graphicsContext.releaseGraphicsLayer(graphicsLayer)
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
          } catch (e: Exception) {
            if (e is CancellationException) throw e
            onStateChanged.invoke(CloudyState.Error(e))
            return@launch
          }

          val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            capturedBitmap.config == Bitmap.Config.HARDWARE
          ) {
            try {
              capturedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            } catch (e: Exception) {
              onStateChanged.invoke(CloudyState.Error(e))
              return@launch
            }
          } else {
            capturedBitmap
          }

          if (softwareBitmap == null) {
            onStateChanged.invoke(
              CloudyState.Error(RuntimeException("Failed to create software bitmap")),
            )
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
          if (e is CancellationException) throw e
          onStateChanged.invoke(CloudyState.Error(e))
        } finally {
          graphicsContext.releaseGraphicsLayer(graphicsLayer)
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

  /**
   * Determine whether a bitmap is fully transparent by sampling a grid of pixels.
   *
   * If the bitmap has zero width or height this function returns `true`. The function samples
   * `grid × grid` points evenly across the bitmap (including edges) and checks each sampled
   * pixel's alpha channel.
   *
   * @param bitmap The bitmap to inspect.
   * @param grid The number of rows and columns to sample; higher values increase sampling density.
   * @return `true` if no sampled pixel has an alpha value greater than zero, `false` otherwise.
   */
  private fun isTransparentBitmap(bitmap: Bitmap, grid: Int = 4): Boolean {
    if (bitmap.width == 0 || bitmap.height == 0) return true
    if (grid <= 1) return false
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
