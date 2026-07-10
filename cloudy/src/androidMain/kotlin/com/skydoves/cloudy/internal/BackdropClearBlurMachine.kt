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

import android.graphics.RenderEffect
import android.graphics.Shader
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.skydoves.cloudy.CloudyState
import com.skydoves.cloudy.ExperimentalMirage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

private const val TAG = "BackdropClearBlur"

/**
 * API 31+ backdrop GPU blur that samples the sky through a **rasterized snapshot**, not a live
 * `drawLayer(sky.backgroundLayer)`.
 *
 * ## Why a snapshot (the cyclic-RenderNode crash)
 * The obvious backdrop blur records `drawLayer(sky.backgroundLayer)` into a blur [GraphicsLayer] and
 * draws that layer under a blur `RenderEffect`. But the backdrop node is a DESCENDANT of the sky
 * recorder, so the sky's captured layer (`backgroundLayer`) transitively references this node's blur
 * layer, which references `backgroundLayer` back — a cyclic `RenderNode` graph. On-screen HWUI walks it
 * damage-scoped and survives, but a `captureToImage` / PixelCopy forces a full-tree `prepareTreeImpl`
 * re-walk that has NO cycle guard and overflows the RenderThread stack
 * (https://github.com/skydoves/Cloudy/issues/112). The frame-time `Sky.isCapturing` guard cannot fix it:
 * the blur layer keeps a stale back-edge that the guard's draw skip never clears, and the same frame's
 * on-screen draw re-records it anyway.
 *
 * Drawing a **bitmap** (`drawImage`) instead of `drawLayer` embeds pixels, not a RenderNode — no
 * back-edge, no cycle. This is exactly why the API < 31 CPU path ([LegacyBackdropBlurMachine], which
 * `drawImage`s a blurred bitmap) never crashed. This machine mirrors that structure but keeps the GPU
 * blur: it async-captures the whole sky layer to an [ImageBitmap] (coalesced on the sky's
 * `contentVersion`, the key the CPU path uses), then draws the sampled sub-region of that bitmap into
 * the blur layer and applies the blur `RenderEffect` to it.
 *
 * ## Cost
 * - Idle (content static): the cached bitmap is reused; each frame just `drawImage`s the sampled region
 *   under the effect — no capture, cheaper than re-walking the sky subtree through `drawLayer`.
 * - Content changing (scroll / animation): one `toImageBitmap()` readback per content version, the same
 *   cadence and cost the CPU path already ships. One frame of staleness on a content change (invisible
 *   for a backdrop blur), never a stale blur while idle.
 */
internal class BackdropClearBlurMachine {

  // The reusable layer the sampled bitmap region is recorded into and the blur RenderEffect is applied
  // to. Drawn each frame; its source is a bitmap (no RenderNode child), so it is never part of a cycle.
  private var blurLayer: GraphicsLayer? = null
  private var cachedBlurEffect: ComposeRenderEffect? = null
  private var cachedBlurRadius: Float = -1f

  // The last full sky snapshot and the version it was captured at (coalescing key).
  private var snapshot: ImageBitmap? = null
  private var cachedContentVersion: Long = -1L

  // In-flight capture coalescing: never cancel a running capture; queue the newest version instead.
  private var isCapturing: Boolean = false
  private var captureJob: Job? = null
  private var queuedVersion: Long = -1L

  var lastState: CloudyState = CloudyState.Nothing
    private set

  /**
   * Draws the blurred backdrop for the Clear (API 31+) path. Captures [layer] (the sky's backdrop) to a
   * bitmap async when [contentVersion] changes, then draws the [offset]-sampled sub-region of the cached
   * snapshot into the blur layer and applies the blur effect. Size is the node size.
   */
  fun ContentDrawScope.draw(
    node: WeatherNode,
    layer: GraphicsLayer,
    radius: Int,
    offset: Offset,
    contentVersion: Long,
  ) {
    val width = size.width.toInt()
    val height = size.height.toInt()
    if (width <= 0 || height <= 0) return

    // Refresh the snapshot when the backdrop content changed (or on the first draw). The capture is
    // async and coalesced; the current cached bitmap keeps drawing until the new one lands.
    if (contentVersion != cachedContentVersion) {
      requestCapture(node, layer, contentVersion)
    }

    val bitmap = snapshot
    if (bitmap == null) {
      // Cold start: no snapshot yet. Draw nothing (transparent); the blur appears when the first
      // capture lands. Matches the CPU path's cold-start behavior.
      lastState = CloudyState.Loading
      return
    }

    // Sample the node region out of the full snapshot, clamped so an edge node stays in bounds.
    val srcW = width.coerceAtMost(bitmap.width)
    val srcH = height.coerceAtMost(bitmap.height)
    val srcX = offset.x.toInt().coerceIn(0, (bitmap.width - srcW).coerceAtLeast(0))
    val srcY = offset.y.toInt().coerceIn(0, (bitmap.height - srcH).coerceAtLeast(0))

    // radius 0 is passthrough (no blur): draw the sampled bitmap region straight into the node. Still a
    // bitmap draw (drawImage), never `drawLayer(sky.backgroundLayer)`, so it stays acyclic / capture-safe.
    if (radius <= 0) {
      drawImage(
        image = bitmap,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(width, height),
      )
      lastState = CloudyState.Success.Applied
      return
    }

    val blurLayer =
      blurLayer ?: node.graphicsContext().createGraphicsLayer().also { blurLayer = it }
    val blurRadius = radius.toFloat()
    val blurEffect = if (cachedBlurEffect == null || cachedBlurRadius != blurRadius) {
      RenderEffect
        .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
        .asComposeRenderEffect()
        .also {
          cachedBlurEffect = it
          cachedBlurRadius = blurRadius
        }
    } else {
      cachedBlurEffect
    }

    // Record the sampled bitmap region (pixels, NOT drawLayer) into the blur layer, then apply the blur
    // RenderEffect and draw it. No RenderNode back-edge → acyclic → capture-safe.
    blurLayer.record(IntSize(width, height)) {
      drawImage(
        image = bitmap,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(width, height),
      )
    }
    if (blurLayer.renderEffect != blurEffect) {
      blurLayer.renderEffect = blurEffect
    }
    drawLayer(blurLayer)
    lastState = CloudyState.Success.Applied
  }

  private fun requestCapture(node: WeatherNode, layer: GraphicsLayer, contentVersion: Long) {
    if (isCapturing) {
      queuedVersion = contentVersion
      return
    }
    isCapturing = true
    captureJob = node.coroutineScope.launch(Dispatchers.Main) {
      try {
        // toImageBitmap() rasterizes the sky layer to a detached bitmap (a Picture-backed hardware
        // bitmap on API 31+): reference-free pixels, the whole point of this path.
        snapshot = layer.toImageBitmap()
        cachedContentVersion = contentVersion
        if (node.isAttached) node.invalidate()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to capture backdrop layer", e)
        lastState = CloudyState.Error(e)
        if (node.isAttached) node.invalidate()
      } finally {
        isCapturing = false
        captureJob = null
        val queued = queuedVersion
        queuedVersion = -1L
        if (queued != -1L && queued != cachedContentVersion && node.isAttached) {
          // A newer version arrived while capturing: re-run for it now.
          requestCapture(node, layer, queued)
        }
      }
    }
  }

  fun dispose(node: WeatherNode) {
    captureJob?.cancel()
    captureJob = null
    isCapturing = false
    queuedVersion = -1L
    snapshot = null
    cachedContentVersion = -1L
    blurLayer?.let { node.graphicsContext().releaseGraphicsLayer(it) }
    blurLayer = null
    cachedBlurEffect = null
    cachedBlurRadius = -1f
  }
}
