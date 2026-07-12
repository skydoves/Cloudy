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
@file:OptIn(ExperimentalMirage::class)

package com.skydoves.cloudy.internal

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.skydoves.cloudy.CloudyState
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.PlatformBitmap
import com.skydoves.cloudy.dispose
import com.skydoves.cloudy.internals.render.RenderScriptToolkit
import com.skydoves.cloudy.internals.render.iterativeBlur
import com.skydoves.cloudy.toPlatformBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Trailing idle window used to coalesce capture requests (slider drag / scroll). Moved verbatim from
 * the former CloudyLegacyBlurStrategy.
 */
private const val CAPTURE_DEBOUNCE_MILLIS: Long = 120L

/** Upper bound on empty-capture retries before giving up. Moved verbatim. */
private const val MAX_EMPTY_CAPTURE_RETRIES: Int = 5

/**
 * Resolves the capture/native downscale factor (the "radius -> scale ramp") for a user radius: small
 * radii capture at higher resolution so a thin blur doesn't visibly degrade; larger radii tolerate
 * more downscaling. The same factor scales the native blur radius so the on-screen amount is faithful.
 * Moved verbatim from the former CloudyLegacyBlurStrategy.
 */
internal fun blurScaleForRadius(radius: Int): Float = when {
  radius <= 6 -> 1.0f
  radius <= 12 -> 0.5f
  else -> 0.25f
}

/**
 * The API < 31 CPU blurrer for the content-source (null sky) foreground path — the former
 * `CloudyModifierNode` body, extracted intact (record-at-scale PATH A capture, in-flight gate,
 * trailing debounce, empty-capture retry). Algorithm, thresholds, coroutine structure, bitmap
 * lifecycle, and invalidate patterns are 1-byte unchanged; only the seams changed: it reads the
 * graphics context / coroutine scope / invalidate through the [EffectNode] handed to [draw], and it
 * reports its [CloudyState] via [lastState] (relayed by the owning BlurStrategy) instead of the old
 * `onStateChanged` callback.
 *
 * Capture is self-owned (it records the node's own content at scale via [recordSource]); it does not
 * chain through the spine's GraphicsLayer pool because the blur is asynchronous + bitmap-backed.
 */
internal class LegacyForegroundBlurrer {

  private var blurredBitmap: PlatformBitmap? = null
  private var cachedBlurRadius: Int = -1

  private var isProcessing: Boolean = false
  private var queuedRadius: Int = -1
  private var queuedContentDirty: Boolean = false
  private var hasQueuedRequest: Boolean = false
  private var blurJob: Job? = null
  private var contentMayHaveChanged: Boolean = false
  private var emptyCaptureRetries: Int = 0

  private var debounceJob: Job? = null
  private var idle: Boolean = true

  // Radius seen on the last draw; a change routes through the same debounce + gate as content changes.
  private var lastSeenRadius: Int = -1

  var lastState: CloudyState = CloudyState.Nothing
    private set

  /**
   * Draws the blurred (or interim) content. [recordSource] is the node's own content (the spine's
   * content source); this blurrer records it at the capture scale itself. Detects a radius change
   * and schedules a coalesced capture, exactly as the former node's `updateRadius` did.
   */
  fun ContentDrawScope.draw(
    node: EffectNode,
    radius: Int,
    recordSource: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
  ) {
    // Radius change → route through the same debounce + gate (the former updateRadius path).
    if (radius != lastSeenRadius) {
      if (lastSeenRadius != -1 && cachedBlurRadius != radius) {
        contentMayHaveChanged = true
      }
      lastSeenRadius = radius
      if (node.isAttached) scheduleCapture(node)
    }

    if (radius <= 0) {
      drawContent()
      lastState = CloudyState.Nothing
      return
    }

    val cached = blurredBitmap
    val validCache = if (cached != null && !cached.bitmap.isRecycled) cached else null

    // Fast path: on a pure cache hit do NOT record a throwaway GraphicsLayer. Upscale and return.
    if (validCache != null && cachedBlurRadius == radius && !contentMayHaveChanged &&
      !isProcessing
    ) {
      drawCachedUpscaled(validCache)
      lastState = CloudyState.Success.Captured(validCache)
      return
    }

    // A capture is already in flight: draw interim content and return.
    if (isProcessing) {
      if (validCache != null) {
        drawCachedUpscaled(validCache)
      } else {
        drawContent()
      }
      return
    }

    // We are going to capture. Record ONCE, at the SMALL capture size (PATH A: record-at-scale).
    val nodeWidth = size.width.roundToInt().coerceAtLeast(1)
    val nodeHeight = size.height.roundToInt().coerceAtLeast(1)
    val captureScale = blurScaleForRadius(radius)
    val capturedRadius = radius
    val captureWidth = max(1, (nodeWidth * captureScale).roundToInt())
    val captureHeight = max(1, (nodeHeight * captureScale).roundToInt())

    val graphicsContext = node.graphicsContext()
    val graphicsLayer = graphicsContext.createGraphicsLayer()
    graphicsLayer.record(size = IntSize(captureWidth, captureHeight)) {
      // pivot = Offset.Zero (origin) so the scaled content fills [0, captureW]x[0, captureH] exactly.
      scale(captureScale, captureScale, pivot = Offset.Zero) {
        recordSource()
      }
    }

    // Show the freshest available content while the (new) blur is being produced.
    if (validCache != null) {
      drawCachedUpscaled(validCache)
    } else {
      // First appearance, no cache yet: draw the live UNBLURRED content (full-res) so the cell is
      // never a blank hole. Must be recordSource() (full-res live canvas), NOT drawLayer(graphicsLayer).
      recordSource()
    }

    isProcessing = true
    hasQueuedRequest = false
    lastState = CloudyState.Loading
    node.invalidate()

    // start = ATOMIC so the finally that releases the layer + resets isProcessing runs even if the
    // node detaches (cancelling coroutineScope) before this launch dispatches.
    blurJob = node.coroutineScope.launch(
      Dispatchers.Main,
      start = kotlinx.coroutines.CoroutineStart.ATOMIC,
    ) {
      try {
        val capturedBitmap: Bitmap = try {
          captureSmallLayer(graphicsLayer)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          lastState = CloudyState.Error(e)
          node.invalidate()
          return@launch
        }

        val blurResult: Bitmap? = withContext(Dispatchers.Default) {
          val output: Bitmap = capturedBitmap.copy(Bitmap.Config.ARGB_8888, true)
          val scaledRadius = (capturedRadius * captureScale).roundToInt().coerceAtLeast(1)
          if (scaledRadius <= 25) {
            RenderScriptToolkit.blur(
              inputBitmap = capturedBitmap,
              outputBitmap = output,
              radius = scaledRadius,
            )
          } else {
            iterativeBlur(
              androidBitmap = capturedBitmap,
              outputBitmap = output,
              radius = scaledRadius,
            ).await()
          }
        }

        if (!capturedBitmap.isRecycled) {
          capturedBitmap.recycle()
        }

        val result = blurResult?.toPlatformBitmap()
          ?: throw RuntimeException("Blur processing returned null")

        if (node.isAttached) {
          val isEmpty = isTransparentBitmap(result.bitmap)
          if (isEmpty) {
            contentMayHaveChanged = true
            emptyCaptureRetries++
          } else {
            emptyCaptureRetries = 0
            cachedBlurRadius = capturedRadius
            blurredBitmap = result
            if (!hasQueuedRequest) {
              contentMayHaveChanged = false
            }
            lastState = CloudyState.Success.Captured(result)
            node.invalidate()
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        lastState = CloudyState.Error(e)
        node.invalidate()
      } catch (e: LinkageError) {
        // The native RenderScript-toolkit lib is missing/unloadable: degrade to Error instead of
        // crashing (a LinkageError is not an Exception, so it would otherwise escape this coroutine).
        lastState = CloudyState.Error(e)
        node.invalidate()
      } finally {
        graphicsContext.releaseGraphicsLayer(graphicsLayer)
        isProcessing = false
        blurJob = null
        val queuedRerun = hasQueuedRequest &&
          (queuedRadius != cachedBlurRadius || queuedContentDirty || contentMayHaveChanged)
        val retryEmpty = emptyCaptureRetries in 1..MAX_EMPTY_CAPTURE_RETRIES
        hasQueuedRequest = false
        if ((queuedRerun || retryEmpty) && node.isAttached) {
          node.invalidate()
        }
      }
    }
  }

  private fun scheduleCapture(node: EffectNode) {
    val wasIdle = idle
    idle = false
    if (wasIdle) {
      requestCapture(node)
    }
    debounceJob?.cancel()
    debounceJob = node.coroutineScope.launch(Dispatchers.Main) {
      delay(CAPTURE_DEBOUNCE_MILLIS)
      debounceJob = null
      idle = true
      requestCapture(node)
    }
  }

  private fun requestCapture(node: EffectNode) {
    if (isProcessing) {
      queuedRadius = lastSeenRadius
      queuedContentDirty = contentMayHaveChanged
      hasQueuedRequest = true
    } else if (node.isAttached) {
      node.invalidate()
    }
  }

  private fun ContentDrawScope.drawCachedUpscaled(cached: PlatformBitmap) {
    drawImage(
      image = cached.bitmap.asImageBitmap(),
      srcOffset = IntOffset.Zero,
      srcSize = IntSize(cached.bitmap.width, cached.bitmap.height),
      dstOffset = IntOffset.Zero,
      dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
      filterQuality = FilterQuality.Low,
    )
  }

  private suspend fun captureSmallLayer(layer: GraphicsLayer): Bitmap {
    var bmp = layer.toImageBitmap().asAndroidBitmap()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      bmp.config == Bitmap.Config.HARDWARE
    ) {
      val hardware = bmp
      bmp = hardware.copy(Bitmap.Config.ARGB_8888, false)
        ?: throw RuntimeException("Failed to copy HARDWARE bitmap to ARGB_8888")
      // Recycle the source HARDWARE bitmap only after the copy succeeded (it is never returned).
      hardware.recycle()
    }
    return bmp
  }

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

  fun dispose() {
    debounceJob?.cancel()
    debounceJob = null
    blurJob?.cancel()
    blurJob = null
    blurredBitmap?.dispose()
    blurredBitmap = null
    isProcessing = false
  }
}
