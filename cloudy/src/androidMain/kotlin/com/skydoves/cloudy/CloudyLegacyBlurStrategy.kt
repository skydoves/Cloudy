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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.skydoves.cloudy.internals.render.RenderScriptToolkit
import com.skydoves.cloudy.internals.render.iterativeBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Trailing idle window used to coalesce capture requests (slider drag / scroll).
 * A leading capture still fires immediately on an idle -> active transition so the
 * first change is shown without waiting for the debounce to elapse.
 */
private const val CAPTURE_DEBOUNCE_MILLIS: Long = 120L

/**
 * Upper bound on how many times we re-capture after a fully transparent (not-yet-drawn) capture
 * before giving up. Caps the retry so a node whose content is genuinely transparent does not
 * invalidate every frame forever; a handful of frames is enough to cover the lay-out/compose
 * race for a cell scrolling into a lazy list.
 */
private const val MAX_EMPTY_CAPTURE_RETRIES: Int = 5

/**
 * Resolves the capture/native downscale factor (the "radius -> scale ramp") for a user
 * radius. Small radii capture at a higher resolution so a thin blur does not visibly
 * degrade; larger radii tolerate more downscaling for performance. The same factor scales
 * the native blur radius, so the on-screen blur amount stays consistent with the radius.
 */
internal fun blurScaleForRadius(radius: Int): Float = when {
  radius <= 6 -> 1.0f
  radius <= 12 -> 0.5f
  else -> 0.25f
}

/**
 * Blur strategy for API 30 and below that captures content into a bitmap
 * and applies the native CPU blur.
 *
 * ## ANR mitigation pipeline (issues #44 / #33)
 *
 * The blur itself already runs off-main on [Dispatchers.Default]. The remaining
 * input-dispatch ANRs on Android 8.x came from two things this strategy now avoids:
 *
 * 1. A *full-res* bitmap readback on the main thread on *every captured frame*. PATH A
 *    (record-at-scale) records the content into the [GraphicsLayer] at the SMALL capture
 *    size up front (see below), so [GraphicsLayer.toImageBitmap] reads back an already-small
 *    bitmap — there is no full-res snapshot and no main-thread [Bitmap.createScaledBitmap].
 *    The readback is ~16x smaller at captureScale=0.25. It also no longer runs per frame: a
 *    trailing debounce + in-flight gate coalesce a slider drag / scroll burst into a bounded
 *    number of (now small) readbacks rather than one per drawn frame.
 * 2. A capture storm while dragging the radius slider / scrolling. We now coalesce
 *    requests with a trailing time debounce, an in-flight gate (<=1 running + <=1
 *    queued), and we never cancel an in-flight capture/blur.
 *
 * ## Chosen capture + blur pipeline (PATH A: record-at-scale)
 *
 * Instead of recording the layer at the node size and downscaling the readback, we record
 * the content directly at the capture resolution during the LIVE draw pass: inside [draw] we
 * call `captureLayer.record(size = small)` and, within that block, `scale(captureScale,
 * captureScale, pivot = Offset.Zero) { drawContent() }`. The GPU performs the downscale while
 * it rasters the recorded display list on the render thread, so by the time the coroutine
 * calls [GraphicsLayer.toImageBitmap] the bitmap is already capture-resolution — no
 * [Bitmap.createScaledBitmap] on the main thread. The origin pivot ([Offset.Zero]) is load
 * bearing: the default `center` pivot is computed against the SMALL drawContext size and
 * shifts the scaled content off-canvas, leaving the corners empty (which
 * [isTransparentBitmap] then rejects as a "transparent" capture).
 *
 * We then run a *plain* [RenderScriptToolkit.blur] on the already-small bitmap with a radius
 * scaled to the capture ([blurScaleForRadius] ramp), so the native-blur cost still drops with
 * the radius. This avoids the redundant double-downscale that would happen if we also let
 * [RenderScriptToolkit.backgroundBlur] re-downscale, while still getting a gamma-correct
 * native blur. When the scaled radius exceeds the native single-pass limit (25) we fall back
 * to [iterativeBlur]. The cached blurred bitmap is small, so [draw] upscales it back to the
 * node size with bilinear filtering on serve.
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

  // ---- cache ----
  private var blurredBitmap: PlatformBitmap? = null
  private var cachedBlurRadius: Int = -1

  // ---- in-flight gate (intervention 1) ----
  // The radius/content the in-flight capture is producing for. When a newer request
  // arrives while a capture is running we only remember the latest one (<=1 queued);
  // the running job re-runs ONCE on completion if the queued state differs from what
  // it just produced, so the final radius/content is always eventually rendered.
  private var isProcessing: Boolean = false
  private var queuedRadius: Int = -1
  private var queuedContentDirty: Boolean = false
  private var hasQueuedRequest: Boolean = false
  private var blurJob: Job? = null
  private var contentMayHaveChanged: Boolean = false

  // A capture can come back fully transparent when the node's content is not laid out/drawn
  // yet on the very first frames it is composed (common for cells scrolling into a lazy list).
  // That is transient, so we re-capture on the next frame instead of giving up — but bounded,
  // so a node whose content is genuinely transparent does not invalidate forever.
  private var emptyCaptureRetries: Int = 0

  // ---- debounce (intervention 4) ----
  private var debounceJob: Job? = null
  private var idle: Boolean = true

  fun updateRadius(newRadius: Int) {
    if (radius == newRadius) return
    radius = newRadius
    if (cachedBlurRadius != radius) {
      contentMayHaveChanged = true
    }
    // Do NOT cancel the in-flight capture/blur. Route this request through the same
    // debounce + gate as content changes so a slider drag coalesces to one capture.
    if (isAttached) {
      scheduleCapture()
    }
  }

  fun onUpdate() {
    contentMayHaveChanged = true
    // A real content change resets the empty-capture retry budget: the next transparent
    // capture is a fresh lay-out race, not the same one we already gave up on.
    emptyCaptureRetries = 0
    if (isAttached) {
      scheduleCapture()
    }
  }

  /**
   * Coalesces capture requests (intervention 4).
   *
   * On an idle -> active transition a leading capture fires immediately so the first
   * change shows without latency. Either way we (re)arm a trailing timer: while changes
   * keep arriving it keeps sliding (last-write-wins); after [CAPTURE_DEBOUNCE_MILLIS] of
   * quiet it fires a trailing capture (so the final radius/content is rendered) and
   * returns the node to idle (so the next burst again gets an immediate leading capture).
   * The timer only ever cancels the debounce delay, never an in-flight capture/blur.
   */
  private fun scheduleCapture() {
    val wasIdle = idle
    idle = false
    if (wasIdle) {
      // Leading edge: show the first change immediately.
      requestCapture()
    }
    // Trailing edge: restart the settle timer (slides while changes keep coming).
    debounceJob?.cancel()
    debounceJob = coroutineScope.launch(Dispatchers.Main) {
      delay(CAPTURE_DEBOUNCE_MILLIS)
      debounceJob = null
      idle = true
      // Ensure the last state in the burst is captured (no-op if leading already covered it).
      requestCapture()
    }
  }

  /**
   * Funnels a debounced request through the in-flight gate (intervention 1).
   *
   * If a capture is already running, the request is recorded as the single queued
   * request (latest wins) and the running job re-runs once on completion. Otherwise a
   * redraw is requested so [draw] launches the capture.
   */
  private fun requestCapture() {
    if (isProcessing) {
      queuedRadius = radius
      queuedContentDirty = contentMayHaveChanged
      hasQueuedRequest = true
    } else if (isAttached) {
      invalidateDraw()
    }
  }

  /**
   * Draws the modifier's content, applying a blur when requested and managing asynchronous
   * scaled capture, caching, gating, debouncing, and state callbacks.
   *
   * If the configured radius is <= 0 the content is drawn directly without blur and no state
   * callback is invoked. When a positive radius is set, a valid cached blurred bitmap is
   * upscaled on serve; otherwise the current content is captured at scale and a native CPU
   * blur is scheduled off-main.
   */
  override fun ContentDrawScope.draw() {
    if (radius <= 0) {
      drawContent()
      return
    }

    val cached = blurredBitmap
    val validCache = if (cached != null && !cached.bitmap.isRecycled) cached else null

    // Fast path (intervention "2-skip-record"): on a pure cache hit we do NOT record a
    // throwaway GraphicsLayer. Just upscale the small cached bitmap and return.
    if (validCache != null && cachedBlurRadius == radius && !contentMayHaveChanged &&
      !isProcessing
    ) {
      drawCachedUpscaled(validCache)
      onStateChanged.invoke(CloudyState.Success.Captured(validCache))
      return
    }

    // A capture is already in flight: do NOT record a throwaway GraphicsLayer (the scaled
    // drawContent() is real GPU work). Just draw interim content and return — the in-flight gate
    // re-runs the capture on completion if a newer request was queued meanwhile.
    if (isProcessing) {
      if (validCache != null) {
        drawCachedUpscaled(validCache)
      } else {
        drawContent()
      }
      return
    }

    // We are going to capture. Record ONCE, at the SMALL capture size (PATH A: record-at-scale).
    //
    // The capture downscale and the native-blur radius scale are the SAME ramp, so the
    // on-screen blur amount stays faithful to the requested radius (no quality floor).
    val nodeWidth = size.width.roundToInt().coerceAtLeast(1)
    val nodeHeight = size.height.roundToInt().coerceAtLeast(1)
    val captureScale = blurScaleForRadius(radius)
    val capturedRadius = radius
    // The recorded display list is sized to the capture resolution; the GPU does the downscale
    // while it rasters on the render thread, so toImageBitmap() reads back an already-small
    // bitmap (no full-res snapshot, no main-thread createScaledBitmap).
    val captureWidth = max(1, (nodeWidth * captureScale).roundToInt())
    val captureHeight = max(1, (nodeHeight * captureScale).roundToInt())

    val graphicsContext = requireGraphicsContext()
    val graphicsLayer = graphicsContext.createGraphicsLayer()
    graphicsLayer.record(size = IntSize(captureWidth, captureHeight)) {
      // pivot = Offset.Zero (origin) so the scaled content fills [0, captureW]x[0, captureH]
      // exactly. The default `center` pivot is computed against the SMALL drawContext size and
      // shifts the content off-canvas, emptying the corners (which isTransparentBitmap rejects).
      scale(captureScale, captureScale, pivot = Offset.Zero) {
        this@draw.drawContent()
      }
    }

    // Show the freshest available content while the (new) blur is being produced.
    //
    // Self-content blur differs from backdrop blur here. The background/backdrop path can draw
    // nothing (transparent) on a cold start because the backdrop behind it shows through — there
    // is no hole. This path blurs the node's OWN content, so drawing nothing leaves a blank hole
    // (the gray cells when new cells scroll into a LazyVerticalGrid). So:
    //   - re-blurring WITH a valid (stale) cache: keep serving the stale blur — no flash.
    //   - first appearance with NO cache: draw the live UNBLURRED content so the cell is never a
    //     hole; the blur swaps in when ready (PATH A keeps that pre-first-blur window short).
    if (validCache != null) {
      drawCachedUpscaled(validCache)
    } else {
      // First appearance, no cache yet: draw the live UNBLURRED content (full-res) so the cell
      // is never a blank hole. The blur swaps in when ready (path A makes that window short).
      // Must be drawContent() (full-res live canvas), NOT drawLayer(graphicsLayer) — graphicsLayer
      // is the SMALL capture-only layer (recorded at captureScale); drawing it would be tiny/blurry.
      drawContent()
    }

    isProcessing = true
    hasQueuedRequest = false
    onStateChanged.invoke(CloudyState.Loading)

    val node = this@CloudyModifierNode

    // start = ATOMIC so the body — and therefore the finally that releases [graphicsLayer] and
    // resets [isProcessing] — is guaranteed to run even if the node detaches (cancelling
    // coroutineScope) before this launch dispatches on the main loop. The layer and the
    // isProcessing flag are allocated/set synchronously above in the live draw pass, so without
    // ATOMIC a cancel-before-first-dispatch would leak the layer and wedge the gate. With ATOMIC
    // the body runs, the first suspension point (toImageBitmap) throws CancellationException if
    // cancelled, and the finally still releases.
    blurJob =
      coroutineScope.launch(Dispatchers.Main, start = kotlinx.coroutines.CoroutineStart.ATOMIC) {
        try {
          // (intervention 2) Materialize the recorded layer via the known-good
          // GraphicsLayer.toImageBitmap(). PATH A already recorded at the capture resolution,
          // so this reads back an already-small bitmap directly — no full-res snapshot and no
          // main-thread createScaledBitmap. The readback is ~16x smaller at captureScale=0.25,
          // and the debounce + in-flight gate keep even this small readback off the per-frame
          // path.
          val capturedBitmap: Bitmap = try {
            captureSmallLayer(graphicsLayer)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            onStateChanged.invoke(CloudyState.Error(e))
            return@launch
          }

          // (intervention 3) Native blur on the already-small bitmap.
          //
          // The capture is already downscaled, so we run a PLAIN RenderScriptToolkit.blur
          // here (NOT backgroundBlur) to avoid a redundant second downscale. The user
          // radius is scaled to the capture resolution and clamped to the native single
          // pass range [1, 25]; anything larger falls back to iterativeBlur.
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

          // Free the intermediate capture bitmap now that the native blur has read it. The blur
          // wrote into a separate `output` bitmap (blurResult), so this does not touch the result.
          if (!capturedBitmap.isRecycled) {
            capturedBitmap.recycle()
          }

          val result = blurResult?.toPlatformBitmap()
            ?: throw RuntimeException("Blur processing returned null")

          if (node.isAttached) {
            val isEmpty = isTransparentBitmap(result.bitmap)
            if (isEmpty) {
              // Transient cold-start: content was not drawn yet when we captured. Retry on the
              // next frame (bounded) instead of getting stuck unblurred forever — the finally
              // block below re-invalidates while emptyCaptureRetries is under the cap.
              contentMayHaveChanged = true
              emptyCaptureRetries++
            } else {
              emptyCaptureRetries = 0
              cachedBlurRadius = capturedRadius
              blurredBitmap = result
              // Content is consistent with what we just produced unless a newer request
              // was queued while we were running.
              if (!hasQueuedRequest) {
                contentMayHaveChanged = false
              }
              node.invalidateDraw()
              onStateChanged.invoke(CloudyState.Success.Captured(result))
            }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          onStateChanged.invoke(CloudyState.Error(e))
        } finally {
          // Release the captured GraphicsLayer on EVERY path (no leaks).
          graphicsContext.releaseGraphicsLayer(graphicsLayer)
          isProcessing = false
          blurJob = null
          // (intervention 1) Re-run ONCE if the queued state diverged from what we just
          // produced, so the final radius/content is never dropped.
          val queuedRerun = hasQueuedRequest &&
            (queuedRadius != cachedBlurRadius || queuedContentDirty || contentMayHaveChanged)
          // Retry an empty (not-yet-drawn) capture on the next frame, bounded so a genuinely
          // transparent node does not invalidate forever.
          val retryEmpty = emptyCaptureRetries in 1..MAX_EMPTY_CAPTURE_RETRIES
          hasQueuedRequest = false
          if ((queuedRerun || retryEmpty) && node.isAttached) {
            node.invalidateDraw()
          }
        }
      }
  }

  /**
   * Draws the (small) cached blurred bitmap upscaled to the node size with bilinear
   * filtering so the served frame is not blocky.
   */
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

  /**
   * Materializes the SMALL recorded [GraphicsLayer] (PATH A: record-at-scale) into a
   * software ARGB_8888 bitmap.
   *
   * The capture goes through [GraphicsLayer.toImageBitmap], which correctly snapshots the
   * recorded RenderNode (on API 26/27 via an ImageReader-backed software readback; on API 28+
   * via a hardware [android.graphics.Picture] bitmap). This is the known-good path: unlike
   * replaying the layer into a software [android.graphics.Canvas] from outside the live draw
   * pass — which re-executes the recorded `drawContent()` block when there is nothing to draw
   * and yields a fully transparent capture — `toImageBitmap()` reads back what was actually
   * recorded.
   *
   * Because PATH A already recorded the content at the capture resolution (the layer was
   * recorded with `record(size = small)` and a `scale(captureScale, .., pivot = Offset.Zero)`
   * transform during the live draw pass), the readback is already capture-resolution: there is
   * NO [Bitmap.createScaledBitmap] here, so no full-res downscale runs on the main thread.
   *
   * On API 28+ `toImageBitmap()` can return a [Bitmap.Config.HARDWARE] bitmap, which has no
   * pixel storage accessible to `getPixel`/RenderScript; we copy it to ARGB_8888 first. That
   * copy is now cheap because the bitmap is already small.
   */
  private suspend fun captureSmallLayer(layer: GraphicsLayer): Bitmap {
    var bmp = layer.toImageBitmap().asAndroidBitmap()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      bmp.config == Bitmap.Config.HARDWARE
    ) {
      bmp = bmp.copy(Bitmap.Config.ARGB_8888, false)
        ?: throw RuntimeException("Failed to copy HARDWARE bitmap to ARGB_8888")
    }
    return bmp
  }

  override fun onDetach() {
    debounceJob?.cancel()
    debounceJob = null
    blurJob?.cancel()
    blurJob = null
    super.onDetach()
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
