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
package com.skydoves.cloudy.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Samples the sky's captured [GraphicsLayer] through a rasterized snapshot instead of a live
 * `drawLayer(sky.backgroundLayer)`, for the **mirage backdrop** (`Modifier.mirage(sky) { … }`).
 *
 * ## Why a snapshot (the captureToImage / PixelCopy crash)
 * The backdrop node is a DESCENDANT of the sky recorder, so `sky.backgroundLayer`'s displaylist embeds
 * this node's own RenderNode by pointer. If this node's draw records `drawLayer(sky.backgroundLayer)` —
 * directly for the raw pass-through, or into a filter layer under a content-bound `RenderEffect` — that
 * closes a cyclic RenderNode graph: `skyLayer -> (this node's layer) -> skyLayer`.
 *
 * On-screen HWUI walks `prepareTreeImpl` damage-scoped and survives the cycle, but `captureToImage()` /
 * `PixelCopy` forces a full-tree re-walk with no cycle guard, overflowing the RenderThread stack
 * (https://github.com/skydoves/Cloudy/issues/112, PixelCopy variant). The frame-time `Sky.isCapturing`
 * guard cannot fix it: it only skips this node's draw during the sky's OWN capture pass, but the
 * back-edge is re-recorded on every normal draw afterward and stays live for any later capture of the
 * window.
 *
 * Drawing a **bitmap** (`drawImage`) instead of `drawLayer` embeds pixels, not a RenderNode — no
 * back-edge, no cycle. This is the same acyclic shape the cloudy blur backdrop uses (a snapshot bitmap):
 * it async-captures the sky layer to an [ImageBitmap] (coalesced on the sky's `contentVersion`), then
 * draws the sampled sub-region of that bitmap wherever the caller used to `drawLayer` the live sky layer.
 *
 * ## Cost
 * - Idle (content static): the cached bitmap is reused; each frame just `drawImage`s the sampled region.
 * - Content changing (scroll / animation): the capture is driven synchronously within [requestIfStale]
 *   ([captureInline]) whenever the platform's `toImageBitmap()` doesn't actually suspend (true on Android
 *   API 28+, whose impl is a synchronous `Picture` rasterize — see [BackdropClearBlurrer] for the
 *   equivalent blur-backdrop machinery and why an async `launch` alone left the shown snapshot lagging
 *   the content during a fast scroll). If the platform's snapshot impl genuinely suspends, [captureInline]
 *   returns without adopting a bitmap and the coalesced async path below picks it up next frame.
 */
internal class MirageBackdropSnapshot {

  private var snapshot: ImageBitmap? = null
  private var cachedContentVersion: Long = -1L

  // In-flight capture coalescing: never cancel a running capture; queue the newest version instead.
  private var isCapturing: Boolean = false
  private var captureJob: Job? = null
  private var queuedVersion: Long = -1L

  /**
   * Refreshes the cached snapshot when [contentVersion] changed. Tries a synchronous inline capture
   * first ([captureInline]); if the platform's `toImageBitmap()` actually suspends, falls back to the
   * coalesced async path, and the previously cached bitmap keeps being drawn via [drawSampledRegion]
   * until a capture lands.
   */
  fun requestIfStale(
    coroutineScope: CoroutineScope,
    layer: GraphicsLayer,
    contentVersion: Long,
    invalidate: () -> Unit,
  ) {
    if (contentVersion == cachedContentVersion) return
    if (!captureInline(layer, contentVersion)) {
      requestCapture(coroutineScope, layer, contentVersion, invalidate)
    }
  }

  // Runs the `suspend fun toImageBitmap()` to completion synchronously when the platform's impl doesn't
  // actually suspend (Android API 28+'s LayerSnapshotV28 never does — see BackdropClearBlurrer's KDoc
  // for the full mechanism). Returns true once a bitmap is adopted this frame; false if the impl
  // suspended, letting the caller fall back to the coalesced async path.
  private fun captureInline(layer: GraphicsLayer, contentVersion: Long): Boolean {
    val bitmap = try {
      layer.captureImageBitmapOrNull()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // Leave the previous snapshot in place; the cold-start "no snapshot" case draws the node's own
      // content instead (the node handles a null backdrop layer before reaching here).
      return true
    } ?: return false
    snapshot = bitmap
    cachedContentVersion = contentVersion
    return true
  }

  private fun GraphicsLayer.captureImageBitmapOrNull(): ImageBitmap? {
    var result: Result<ImageBitmap>? = null
    val continuation = object : Continuation<ImageBitmap> {
      override val context: CoroutineContext = EmptyCoroutineContext
      override fun resumeWith(outcome: Result<ImageBitmap>) {
        result = outcome
      }
    }
    (suspend { toImageBitmap() }).startCoroutine(continuation)
    return result?.getOrThrow()
  }

  private fun requestCapture(
    coroutineScope: CoroutineScope,
    layer: GraphicsLayer,
    contentVersion: Long,
    invalidate: () -> Unit,
  ) {
    if (isCapturing) {
      queuedVersion = contentVersion
      return
    }
    isCapturing = true
    captureJob = coroutineScope.launch(Dispatchers.Main) {
      try {
        // toImageBitmap() rasterizes the sky layer to a detached bitmap (a Picture-backed hardware
        // bitmap on API 31+): reference-free pixels, the whole point of this path.
        snapshot = layer.toImageBitmap()
        cachedContentVersion = contentVersion
        invalidate()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Leave the previous snapshot in place; the cold-start "no snapshot" case draws the node's own
        // content instead (the node handles a null backdrop layer before reaching here).
        invalidate()
      } finally {
        isCapturing = false
        captureJob = null
        val queued = queuedVersion
        queuedVersion = -1L
        if (queued != -1L && queued != cachedContentVersion) {
          requestCapture(coroutineScope, layer, queued, invalidate)
        }
      }
    }
  }

  /** True once a snapshot has been captured at least once (so the caller can fall back before then). */
  fun hasSnapshot(): Boolean = snapshot != null

  /** Samples [offset]..[offset]+size out of the cached snapshot into the current draw target. */
  fun DrawScope.drawSampledRegion(offset: Offset, size: IntSize) {
    val bitmap = snapshot ?: return
    val srcW = size.width.coerceAtMost(bitmap.width)
    val srcH = size.height.coerceAtMost(bitmap.height)
    val srcX = offset.x.toInt().coerceIn(0, (bitmap.width - srcW).coerceAtLeast(0))
    val srcY = offset.y.toInt().coerceIn(0, (bitmap.height - srcH).coerceAtLeast(0))
    drawImage(
      image = bitmap,
      srcOffset = IntOffset(srcX, srcY),
      srcSize = IntSize(srcW, srcH),
      dstOffset = IntOffset.Zero,
      dstSize = size,
    )
  }

  fun dispose() {
    captureJob?.cancel()
    captureJob = null
    isCapturing = false
    queuedVersion = -1L
    snapshot = null
    cachedContentVersion = -1L
  }
}
