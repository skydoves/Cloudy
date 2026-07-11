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
import androidx.compose.ui.graphics.GraphicsContext
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

/**
 * Samples the sky's captured [GraphicsLayer] through a rasterized snapshot instead of a live
 * `drawLayer(sky.backgroundLayer)`. Shared by every backdrop node that samples the sky — the cloudy blur
 * backdrop and the mirage backdrop.
 *
 * ## Why a snapshot (the captureToImage / PixelCopy crash)
 * The backdrop node is a DESCENDANT of the sky recorder, so `sky.backgroundLayer`'s displaylist embeds
 * this node's own RenderNode by pointer. If this node's draw records `drawLayer(sky.backgroundLayer)` —
 * directly for the radius-0/scrim/raw paths, or into an effect layer for the RenderEffect/blur path —
 * that closes a cyclic RenderNode graph: `skyLayer -> (this node's layer) -> skyLayer`.
 *
 * On-screen HWUI walks `prepareTreeImpl` damage-scoped and survives the cycle, but `captureToImage()` /
 * `PixelCopy` forces a full-tree re-walk with no cycle guard, overflowing the RenderThread stack
 * (https://github.com/skydoves/Cloudy/issues/112, PixelCopy variant). The frame-time `Sky.isCapturing`
 * guard cannot fix it: it only skips this node's draw during the sky's OWN capture pass, but the
 * back-edge is re-recorded on every normal draw afterward and stays live for any later capture of the
 * window.
 *
 * Drawing a **bitmap** (`drawImage`) instead of `drawLayer` embeds pixels, not a RenderNode — no
 * back-edge, no cycle. This is structurally what the API < 31 CPU path (`drawWithBitmap`, already
 * `toImageBitmap()`-based) does, which is why it never crashed. This machine gives the API 31+ path the
 * same acyclic shape while keeping the GPU blur: it async-captures the sky layer to an [ImageBitmap]
 * (coalesced on the sky's `contentVersion`, the same key `drawWithBitmap` uses), then draws the sampled
 * sub-region of that bitmap wherever the caller used to `drawLayer` the live sky layer.
 *
 * ## Cost
 * - Idle (content static): the cached bitmap is reused; each frame just `drawImage`s the sampled region
 *   — cheaper than re-walking the sky subtree through a live `drawLayer`.
 * - Content changing (scroll / animation): one `toImageBitmap()` readback per content version, the same
 *   cadence and cost `drawWithBitmap` already pays. One frame of staleness on a content change
 *   (invisible for a backdrop blur), never a stale draw while idle.
 */
internal class BackdropClearBlurMachine {

  private var snapshot: ImageBitmap? = null
  private var cachedContentVersion: Long = -1L

  // In-flight capture coalescing: never cancel a running capture; queue the newest version instead.
  private var isCapturing: Boolean = false
  private var captureJob: Job? = null
  private var queuedVersion: Long = -1L

  /**
   * Refreshes the cached snapshot when [contentVersion] changed (async, coalesced); the previously
   * cached bitmap keeps being returned via [current] until the new capture lands.
   */
  fun requestIfStale(
    graphicsContext: GraphicsContext,
    coroutineScope: CoroutineScope,
    layer: GraphicsLayer,
    contentVersion: Long,
    invalidate: () -> Unit,
  ) {
    if (contentVersion == cachedContentVersion) return
    requestCapture(coroutineScope, layer, contentVersion, invalidate)
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
        // Leave the previous snapshot in place; the caller's normal Error state handling elsewhere
        // covers the "no snapshot at all" cold-start case.
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
