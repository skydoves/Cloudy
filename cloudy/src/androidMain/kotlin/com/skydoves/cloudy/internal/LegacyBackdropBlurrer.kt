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
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.tracing.trace
import com.skydoves.cloudy.CloudyState
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.PlatformBitmap
import com.skydoves.cloudy.dispose
import com.skydoves.cloudy.internals.SkySnapshot
import com.skydoves.cloudy.internals.render.RenderScriptToolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "LegacyBackdropBlur"

/**
 * The API < 31 CPU blurrer for the backdrop (non-null sky) path — the former
 * `CloudyBackgroundModifierNode.drawWithBitmap` region, extracted intact (crop -> scale -> native
 * background blur -> mask -> scale up, completion-based coalescing with a single-slot queue, never
 * cancel an in-flight blur). Algorithm, thresholds, coroutine structure, bitmap lifecycle, and
 * invalidate patterns are 1-byte unchanged; only the seams changed: graphics/scope/invalidate come
 * through the [EffectNode], and it reports state via [lastState] (relayed by the owning BlurStrategy).
 *
 * It draws only the blurred bitmap; clip/tint/highlight are applied by the node's [PostProcess] around
 * this draw (they used to be `drawCachedWithOverlays`). Capture reads the backdrop [GraphicsLayer] the
 * way the old node did — not through `recordSource`, which the async bitmap pipeline cannot consume.
 */
internal class LegacyBackdropBlurrer {

  private var blurredBitmap: PlatformBitmap? = null
  private var cachedContentVersion: Long = -1L
  private var isProcessing: Boolean = false
  private var blurJob: Job? = null
  private var queuedVersion: Long = -1L

  var lastState: CloudyState = CloudyState.Nothing
    private set

  /**
   * Draws the blurred backdrop (or interim/cached) content. [layer] is the sky's captured backdrop;
   * [snapshot] carries radius/offset/progressive; [contentVersion] is the sky's current version.
   */
  fun ContentDrawScope.draw(
    node: EffectNode,
    layer: GraphicsLayer,
    snapshot: SkySnapshot,
    contentVersion: Long,
  ) {
    val currentVersion = contentVersion
    val cached = blurredBitmap
    val cacheValid = cached != null &&
      !cached.bitmap.isRecycled &&
      cachedContentVersion == currentVersion

    if (cacheValid) {
      drawCached(cached)
      lastState = CloudyState.Success.Captured(cached)
      return
    }

    if (cached != null && !cached.bitmap.isRecycled) {
      drawCached(cached)
    }
    // No cache: draw nothing (transparent) - blur will appear when ready

    if (isProcessing) {
      queuedVersion = currentVersion
      return
    }

    if (currentVersion == cachedContentVersion) {
      return
    }

    isProcessing = true
    lastState = CloudyState.Loading
    node.invalidate()

    val capturedSnapshot = snapshot.copy()
    val processingVersion = currentVersion
    val outputWidth = capturedSnapshot.childWidth.toInt()
    val outputHeight = capturedSnapshot.childHeight.toInt()

    blurJob = node.coroutineScope.launch(Dispatchers.Main) {
      try {
        val capturedBitmap: Bitmap = try {
          layer.toImageBitmap().asAndroidBitmap()
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          Log.e(TAG, "Failed to capture layer as bitmap", e)
          lastState = CloudyState.Error(e)
          node.invalidate()
          return@launch
        }

        val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
          capturedBitmap.config == Bitmap.Config.HARDWARE
        ) {
          try {
            capturedBitmap.copy(Bitmap.Config.ARGB_8888, true)
          } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to convert HARDWARE bitmap", e)
            lastState = CloudyState.Error(e)
            node.invalidate()
            return@launch
          }
        } else {
          capturedBitmap
        }

        if (softwareBitmap == null) {
          lastState = CloudyState.Error(RuntimeException("Failed to create software bitmap"))
          node.invalidate()
          return@launch
        }

        val outputBitmap = Bitmap.createBitmap(
          outputWidth,
          outputHeight,
          Bitmap.Config.ARGB_8888,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          outputBitmap.isPremultiplied = true
        }

        val progressiveDir = when (capturedSnapshot.direction) {
          SkySnapshot.ProgressiveDirection.TOP_TO_BOTTOM ->
            RenderScriptToolkit.ProgressiveDirection.TOP_TO_BOTTOM

          SkySnapshot.ProgressiveDirection.BOTTOM_TO_TOP ->
            RenderScriptToolkit.ProgressiveDirection.BOTTOM_TO_TOP

          SkySnapshot.ProgressiveDirection.EDGES ->
            RenderScriptToolkit.ProgressiveDirection.EDGES

          SkySnapshot.ProgressiveDirection.NONE ->
            RenderScriptToolkit.ProgressiveDirection.NONE
        }

        // Clamp so cropX+outputWidth (and Y) stay within the source; otherwise an edge child or a
        // subpixel overshoot fails backgroundBlur's require() and strands the blur in Error.
        val cropX = capturedSnapshot.offsetX.toInt()
          .coerceIn(0, (softwareBitmap.width - outputWidth).coerceAtLeast(0))
        val cropY = capturedSnapshot.offsetY.toInt()
          .coerceIn(0, (softwareBitmap.height - outputHeight).coerceAtLeast(0))

        val success = withContext(Dispatchers.Default) {
          trace("Cloudy.backgroundBlur") {
            RenderScriptToolkit.backgroundBlur(
              srcBitmap = softwareBitmap,
              dstBitmap = outputBitmap,
              cropX = cropX,
              cropY = cropY,
              radius = capturedSnapshot.radius.coerceIn(1, 25),
              scale = 0.25f,
              progressiveDirection = progressiveDir,
              fadeStart = capturedSnapshot.fadeStart,
              fadeEnd = capturedSnapshot.fadeEnd,
            )
          }
        }

        if (!success) {
          Log.e(TAG, "Native background blur failed")
          lastState = CloudyState.Error(RuntimeException("Native blur processing failed"))
          node.invalidate()
          return@launch
        }

        if (!softwareBitmap.isRecycled) softwareBitmap.recycle()
        if (softwareBitmap !== capturedBitmap && !capturedBitmap.isRecycled) {
          capturedBitmap.recycle()
        }

        if (node.isAttached) {
          blurredBitmap?.dispose()
          blurredBitmap = PlatformBitmap(outputBitmap)
          cachedContentVersion = processingVersion
          lastState = CloudyState.Success.Captured(blurredBitmap!!)

          val queued = queuedVersion
          queuedVersion = -1L
          if (queued != -1L && queued != cachedContentVersion) {
            Log.d(TAG, "Processing queued version: $queued (cached: $cachedContentVersion)")
          }
          node.invalidate()
        }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Background blur failed", e)
        lastState = CloudyState.Error(e)
        node.invalidate()
      } catch (e: LinkageError) {
        // The native RenderScript-toolkit lib is missing/unloadable: degrade to Error instead of
        // crashing (a LinkageError is not an Exception, so it would otherwise escape this coroutine).
        Log.e(TAG, "Background blur failed", e)
        lastState = CloudyState.Error(e)
        node.invalidate()
      } finally {
        isProcessing = false
        blurJob = null
      }
    }
  }

  /** Draws the cached blurred bitmap scaled to the node size; clip/tint/highlight are the node's PostProcess. */
  private fun ContentDrawScope.drawCached(cached: PlatformBitmap) {
    drawImage(
      image = cached.bitmap.asImageBitmap(),
      dstSize = IntSize(size.width.toInt(), size.height.toInt()),
    )
  }

  fun dispose() {
    blurJob?.cancel()
    blurJob = null
    blurredBitmap?.dispose()
    blurredBitmap = null
    isProcessing = false
    cachedContentVersion = -1L
    queuedVersion = -1L
  }
}
