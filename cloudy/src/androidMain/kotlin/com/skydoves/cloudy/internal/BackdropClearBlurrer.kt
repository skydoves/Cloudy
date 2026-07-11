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

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.skydoves.cloudy.CloudyProgressive
import com.skydoves.cloudy.CloudyState
import com.skydoves.cloudy.ExperimentalMirage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
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
 * back-edge, no cycle. This is exactly why the API < 31 CPU path ([LegacyBackdropBlurrer], which
 * `drawImage`s a blurred bitmap) never crashed. This blurrer mirrors that structure but keeps the GPU
 * blur: it captures the whole sky layer to an [ImageBitmap] (cached on the sky's `contentVersion`, the
 * key the CPU path uses), then draws the sampled sub-region of that bitmap into the blur layer and
 * applies the blur `RenderEffect` to it.
 *
 * ## Synchronous capture (no staleness during fast scroll)
 * The capture runs INLINE within the draw. On the GPU tier (always API 31+), `GraphicsLayer`'s snapshot
 * impl is `LayerSnapshotV28`, a synchronous `Picture` rasterize that never suspends, so
 * [captureInline] drives the `suspend fun toImageBitmap()` to completion this frame and the blur shows
 * the SAME content the recorder just re-captured — zero version lag. An earlier design posted the
 * capture on `Dispatchers.Main`; that hop deferred a synchronous operation to a later frame, so a fast
 * fling saw the blur trail (sometimes far) behind the content. The async [requestCapture] is kept only
 * as a fallback for the (unreached) case where the impl actually suspends.
 *
 * ## Cost
 * - Idle (content static): the cached bitmap is reused; each frame just `drawImage`s the sampled region
 *   under the effect — no capture, cheaper than re-walking the sky subtree through `drawLayer`.
 * - Content changing (scroll / animation): one synchronous `Picture` rasterize per content version, the
 *   same cadence the CPU path already ships, resolved on the frame that changed rather than the next.
 */
internal class BackdropClearBlurrer {

  // The reusable layer the sampled bitmap region is recorded into and the blur RenderEffect is applied
  // to. Drawn each frame; its source is a bitmap (no RenderNode child), so it is never part of a cycle.
  private var blurLayer: GraphicsLayer? = null
  private var cachedBlurEffect: ComposeRenderEffect? = null
  private var cachedBlurRadius: Float = -1f

  // Progressive (RuntimeShader) effect cache. The shader + effect are rebuilt only when the draw-time
  // key (radius, progressive params, sampled size) changes; uniforms are re-set on the same cadence.
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private var progressiveShader: RuntimeShader? = null
  private var cachedProgressiveEffect: ComposeRenderEffect? = null
  private var cachedProgressiveKey: ProgressiveKey? = null

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
    node: EffectNode,
    layer: GraphicsLayer,
    radius: Int,
    offset: Offset,
    contentVersion: Long,
    progressive: CloudyProgressive,
  ) {
    val width = size.width.toInt()
    val height = size.height.toInt()
    if (width <= 0 || height <= 0) return

    // Refresh the snapshot when the backdrop content changed (or on the first draw). On the GPU tier
    // (always API 31+, so GraphicsLayer's snapshot impl is the synchronous Picture-based LayerSnapshotV28)
    // the capture completes inline this frame — no posted main-loop hop, no version lag, so the blur
    // tracks the content on the same frame the recorder re-captured it. A posted coroutine only ever
    // ran because toImageBitmap() is declared `suspend`; on this path it never actually suspends, and
    // the hop was pure latency that let a fast fling stay a frame (or more) behind the content.
    // captureInline falls back to the coalesced async path only if the snapshot impl unexpectedly
    // suspends (it does not on API 31+).
    if (contentVersion != cachedContentVersion) {
      captureInline(node, layer, contentVersion)
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

    // Progressive (gradient) blur on API 33+ (RuntimeShader): the effect is a RuntimeShader that mixes
    // the SHARP sampled region with its BLURRED self by a per-row fade factor, matching the CPU path's
    // premultiplied-alpha mask (BackgroundBlur.cpp applyProgressiveMask). API 31-32 has no RuntimeShader,
    // so it falls through to the uniform blur below (already warned once by BlurStrategy).
    val effect =
      resolveBlurEffect(bitmap, srcX, srcY, width, height, blurRadius, progressive)

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
    if (blurLayer.renderEffect != effect) {
      blurLayer.renderEffect = effect
    }
    drawLayer(blurLayer)
    lastState = CloudyState.Success.Applied
  }

  // Returns the RenderEffect to apply to the blur layer. Uniform blur for None / API < 33; a progressive
  // RuntimeShader composite (sharp mixed with blurred by a vertical fade) otherwise. Both are cached on a
  // draw-time key (radius, plus progressive params + size for the shader), so steady state allocates nothing.
  private fun resolveBlurEffect(
    bitmap: ImageBitmap,
    srcX: Int,
    srcY: Int,
    width: Int,
    height: Int,
    blurRadius: Float,
    progressive: CloudyProgressive,
  ): ComposeRenderEffect {
    val useProgressive = progressive != CloudyProgressive.None &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    if (!useProgressive) {
      if (cachedBlurEffect == null || cachedBlurRadius != blurRadius ||
        cachedProgressiveKey != null
      ) {
        cachedBlurEffect = RenderEffect
          .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
          .asComposeRenderEffect()
        cachedBlurRadius = blurRadius
        cachedProgressiveKey = null
      }
      return cachedBlurEffect!!
    }
    return progressiveEffect(bitmap, srcX, srcY, width, height, blurRadius, progressive)
  }

  // Builds (or reuses) the progressive RuntimeShader effect. The effect samples its RenderNode input
  // (the recorded sharp region) as `content`, blurs it in-shader via the platform blur chained as the
  // inner effect (so `content` is already blurred), and mixes it with the SHARP region — supplied as a
  // BitmapShader `sharp` — by a per-row fade `f`. `f` reproduces BackgroundBlur.cpp's applyProgressiveMask
  // exactly (f = "how much blur shows"): result = mix(sharp, blurred, f), so API 33+ matches API < 31.
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun progressiveEffect(
    bitmap: ImageBitmap,
    srcX: Int,
    srcY: Int,
    width: Int,
    height: Int,
    blurRadius: Float,
    progressive: CloudyProgressive,
  ): ComposeRenderEffect {
    val fade = fadeParamsOf(progressive)
    val key = ProgressiveKey(blurRadius, width, height, fade.direction, fade.start, fade.end)
    if (cachedProgressiveKey == key) return cachedProgressiveEffect!!

    val shader =
      progressiveShader ?: RuntimeShader(PROGRESSIVE_AGSL).also { progressiveShader = it }
    // The sharp region as a Shader: sample the full snapshot with a translation so (0,0) in the node
    // maps to (srcX, srcY) in the snapshot — the same sub-region drawImage records into the layer.
    val sharpShader = BitmapShader(
      bitmap.asAndroidBitmap(),
      Shader.TileMode.CLAMP,
      Shader.TileMode.CLAMP,
    )
      .apply { setLocalMatrix(Matrix().apply { setTranslate(-srcX.toFloat(), -srcY.toFloat()) }) }
    shader.setInputShader("sharp", sharpShader)
    shader.setFloatUniform("size", width.toFloat(), height.toFloat())
    shader.setIntUniform("direction", fade.direction)
    shader.setFloatUniform("fadeStart", fade.start)
    shader.setFloatUniform("fadeEnd", fade.end)

    // content = blur(recorded sharp region): chain the platform blur as the INNER effect so the runtime
    // shader receives the already-blurred input as its `content`. createChainEffect(outer, inner) =
    // outer(inner(source)) per the platform contract.
    val blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
    val runtime = RenderEffect.createRuntimeShaderEffect(shader, "content")
    val effect = RenderEffect.createChainEffect(runtime, blur).asComposeRenderEffect()

    cachedProgressiveEffect = effect
    cachedProgressiveKey = key
    cachedBlurEffect = null // uniform cache is now stale; force a rebuild if we switch back
    cachedBlurRadius = -1f
    return effect
  }

  // Maps a CloudyProgressive to the native fade direction + normalized fadeStart/fadeEnd, identical to
  // SkySnapshot.fromProgressive (the CPU path's mapping). Kept in sync so both tiers fade the same way.
  private fun fadeParamsOf(progressive: CloudyProgressive): FadeParams = when (progressive) {
    is CloudyProgressive.TopToBottom -> FadeParams(
      DIR_TOP_TO_BOTTOM,
      progressive.start,
      progressive.end,
    )

    is CloudyProgressive.BottomToTop -> FadeParams(
      DIR_BOTTOM_TO_TOP,
      progressive.start,
      progressive.end,
    )

    is CloudyProgressive.Edges ->
      FadeParams(DIR_EDGES, progressive.fadeDistance, 1f - progressive.fadeDistance)

    CloudyProgressive.None -> FadeParams(DIR_NONE, 0f, 1f)
  }

  // Captures the sky layer to a snapshot for [contentVersion]. On API 31+ (the only tier that reaches
  // here) toImageBitmap()'s impl (LayerSnapshotV28) rasterizes a Picture synchronously and never
  // suspends, so the capture is driven to completion inline within this draw. If it ever DID suspend
  // (it never does on this path), the driver leaves cachedContentVersion behind and the async
  // [requestCapture] takes over on the next frame, so correctness is preserved either way.
  private fun captureInline(node: EffectNode, layer: GraphicsLayer, contentVersion: Long) {
    val bitmap = try {
      layer.captureImageBitmapOrNull()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to capture backdrop layer", e)
      lastState = CloudyState.Error(e)
      return
    }
    if (bitmap != null) {
      // Synchronous capture landed: adopt it this frame.
      snapshot = bitmap
      cachedContentVersion = contentVersion
    } else {
      // The snapshot impl suspended (not expected on API 31+): fall back to the coalesced async path.
      requestCapture(node, layer, contentVersion)
    }
  }

  private fun requestCapture(node: EffectNode, layer: GraphicsLayer, contentVersion: Long) {
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

  // Runs the `suspend fun toImageBitmap()` to completion synchronously. On API 31+ its impl
  // (LayerSnapshotV28) is a synchronous Picture rasterize that never suspends, so `startCoroutine`
  // resumes the continuation before returning and the result is available immediately. Returns null
  // only if the impl actually suspended (it does not on this tier), letting the caller fall back to
  // the async path. This is the sanctioned public snapshot API — no re-entrant `drawLayer`, so it
  // stays acyclic / capture-safe like the async path it replaces.
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

  fun dispose(node: EffectNode) {
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
    progressiveShader = null
    cachedProgressiveEffect = null
    cachedProgressiveKey = null
  }

  // Draw-time key for the progressive effect: rebuild only when radius, sampled size, or fade changes.
  private data class ProgressiveKey(
    val radius: Float,
    val width: Int,
    val height: Int,
    val direction: Int,
    val fadeStart: Float,
    val fadeEnd: Float,
  )

  private class FadeParams(val direction: Int, val start: Float, val end: Float)

  private companion object {
    // Fade direction codes shared with the AGSL uniform (mirror RenderScriptToolkit.ProgressiveDirection).
    const val DIR_NONE = 0
    const val DIR_TOP_TO_BOTTOM = 1
    const val DIR_BOTTOM_TO_TOP = 2
    const val DIR_EDGES = 3

    // Mixes the sharp region with its blurred self (bound as `content`, already blurred by the chained
    // platform blur) by a per-row fade factor f. f mirrors BackgroundBlur.cpp applyProgressiveMask
    // exactly: f = fraction of blur shown at this row; f = 0 shows the sharp source. The mask is vertical
    // (all three CPU directions fade over Y). `content.eval` uses device coords; `sharp` is a BitmapShader
    // pre-translated so device (0,0) maps to the node's sampled region, so both align pixel-for-pixel.
    val PROGRESSIVE_AGSL = """
      uniform shader content;
      uniform shader sharp;
      uniform float2 size;
      uniform int direction;
      uniform float fadeStart;
      uniform float fadeEnd;

      half4 main(float2 coord) {
        float denom = max(size.y - 1.0, 1.0);
        float ny = coord.y / denom;
        float f = 1.0;
        if (direction == 1) {          // TOP_TO_BOTTOM: opaque at top, fades to sharp at bottom
          if (ny <= fadeStart) { f = 1.0; }
          else if (ny >= fadeEnd) { f = 0.0; }
          else { float r = fadeEnd - fadeStart; f = (r > 0.0) ? 1.0 - (ny - fadeStart) / r : 1.0; }
        } else if (direction == 2) {   // BOTTOM_TO_TOP: opaque at bottom, fades to sharp at top
          if (ny >= fadeStart) { f = 1.0; }
          else if (ny <= fadeEnd) { f = 0.0; }
          else { float r = fadeStart - fadeEnd; f = (r > 0.0) ? (ny - fadeEnd) / r : 1.0; }
        } else if (direction == 3) {   // EDGES: sharp at edges, blurred in center
          if (ny <= fadeStart) { f = (fadeStart > 0.0) ? ny / fadeStart : 1.0; }
          else if (ny >= fadeEnd) { f = (fadeEnd < 1.0) ? (1.0 - ny) / (1.0 - fadeEnd) : 1.0; }
          else { f = 1.0; }
        }
        f = clamp(f, 0.0, 1.0);
        return mix(sharp.eval(coord), content.eval(coord), f);
      }
    """.trimIndent()
  }
}
