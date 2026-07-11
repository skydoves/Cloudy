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

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
   * Draws the blit-filtered backdrop for this frame: the fresh cache if present, otherwise the
   * [recordRaw] placeholder region while an async capture + blit runs (single-slot gate, latest key
   * wins).
   *
   * Two sources by design: [recordRaw] is drawn on-screen, so it must be acyclic (a snapshot bitmap, not
   * a live `drawLayer` of the sky) or `captureToImage`/`PixelCopy` cycles the RenderNode graph (issue
   * #112). [recordInput] is recorded into an offscreen layer that is captured to a bitmap and released
   * within this call — never reachable from the on-screen tree, so it stays a live `drawLayer` to keep
   * the blit input the freshest possible backdrop pixels (a version-keyed cache must not blit a stale or
   * not-yet-ready snapshot, which would stick until the next content change).
   *
   * @param blit the GLES filter transform (`ImageBitmap -> ImageBitmap`), uniforms already recorded.
   * @param contentVersion the backdrop's discrete-change counter; a new value invalidates the cache.
   * @param recordRaw records the offset backdrop region for the on-screen placeholder (acyclic).
   * @param recordInput records the offset backdrop region for the offscreen blit input (may be live).
   * @param invalidate schedules a redraw when a capture completes.
   */
  fun ContentDrawScope.draw(
    context: GraphicsContext,
    scope: CoroutineScope,
    blit: suspend (ImageBitmap) -> ImageBitmap,
    contentVersion: Long,
    recordRaw: DrawScope.() -> Unit,
    recordInput: DrawScope.() -> Unit,
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
    recordRaw()
    if (inFlight) return

    val layer = context.createGraphicsLayer()
    layer.record(size = IntSize(w, h)) { recordInput() }

    inFlight = true
    // toImageBitmap() runs on the node's (main) scope; blit is suspend and pins its GL round-trip to
    // GlEnv's single GL-thread dispatcher itself, so this launch needs no withContext to leave the main
    // thread — the GL work never runs here.
    job = scope.launch {
      try {
        val input = layer.toImageBitmap()
        val output = blit(input)
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
