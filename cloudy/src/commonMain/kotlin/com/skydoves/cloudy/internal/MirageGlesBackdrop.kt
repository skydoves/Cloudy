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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Runs a single [FilterApplication.Blit] filter over a backdrop region asynchronously. The Blit path
 * (the Android GLES band) cannot be a synchronous `RenderEffect`, and `GraphicsLayer.toImageBitmap()`
 * is `suspend`, so the transform happens off the draw pass: record the region, capture it, run the
 * blit, cache the result, redraw. It mirrors the legacy backdrop-blur shape but is dialect-agnostic —
 * every type here is common Compose; the platform GL work lives entirely inside the [Blit] closure.
 *
 * ## Scope: backdrop only, single stage
 * Keyed on the backdrop's discrete [contentVersion]; a self-lit node has no such key, so it
 * stays a no-op. One stage renders (the common backdrop-material case); extra Blit stages are ignored.
 *
 * Held by the backdrop node, released on detach ([release]).
 */
internal class MirageGlesBackdrop {

  private var cached: ImageBitmap? = null
  private var cachedVersion: Long = Long.MIN_VALUE
  private var inFlight = false
  private var job: Job? = null

  /**
   * Draws the blit-filtered backdrop for this frame: the fresh cache if present, otherwise the raw
   * [recordSource] region while an async capture + blit runs (single-slot gate, latest key wins).
   *
   * @param blit the GLES filter transform (`ImageBitmap -> ImageBitmap`), uniforms already recorded.
   * @param contentVersion the backdrop's discrete-change counter; a new value invalidates the cache.
   * @param recordSource records the offset backdrop region (same block the sync chain uses).
   * @param invalidate schedules a redraw when a capture completes.
   */
  fun ContentDrawScope.draw(
    context: androidx.compose.ui.graphics.GraphicsContext,
    scope: CoroutineScope,
    blit: (ImageBitmap) -> ImageBitmap,
    contentVersion: Long,
    recordSource: DrawScope.() -> Unit,
    invalidate: () -> Unit,
  ) {
    val w = size.width.roundToInt().coerceAtLeast(1)
    val h = size.height.roundToInt().coerceAtLeast(1)

    // Cache hit only when the version AND both dimensions match — compared field-by-field, never packed
    // into one number (a packed key can collide when a resize and a version bump land on the same frame,
    // matching a stale bitmap to a new request). The bitmap's own size is the size it was captured at.
    cached?.takeIf { cachedVersion == contentVersion && it.width == w && it.height == h }?.let {
      drawImage(
        image = it,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(it.width, it.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(w, h),
      )
      return
    }

    // No fresh cache: show the raw region so the node is never blank, then launch a capture if idle.
    recordSource()
    if (inFlight) return

    val layer = context.createGraphicsLayer()
    layer.record(size = IntSize(w, h)) { recordSource() }

    inFlight = true
    // toImageBitmap() must run on the node's (main) scope; the blit's GL round-trip blocks, so push it
    // off the main thread. Dispatchers.Default is hardcoded to match the sibling legacy backdrop-blur
    // strategy — this is a draw-node collaborator, never unit-tested in isolation, so DI would be dead
    // ceremony here. ponytail: inject a dispatcher only if a test ever needs to swap it.
    job = scope.launch {
      try {
        val input = layer.toImageBitmap()
        val output = withContext(Dispatchers.Default) { blit(input) }
        cached = output
        cachedVersion = contentVersion
        invalidate()
      } finally {
        context.releaseGraphicsLayer(layer)
        inFlight = false
      }
    }
  }

  fun release() {
    job?.cancel()
    job = null
    cached = null
    cachedVersion = Long.MIN_VALUE
    inFlight = false
  }
}
