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

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.MirageClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orchestrates a mirage plan against a [Skylight] source: [Skylight.SelfLit] runs it over the node's
 * own content, [Skylight.Backdrop] over the offset [Sky][com.skydoves.cloudy.Sky] region behind it.
 * The node owns the ordered [stages], the [MirageClock] frame loop, the layer-pool [chain], and (for a
 * backdrop) the positioning + frame-driver lifecycle; the [Weather] owns the actual draw. It never
 * compiles a shader or owns cache state — compilation/caching live in [MirageProgramCache].
 *
 * This merges the former self-content and backdrop nodes, which were ~90% identical; the only
 * differences were the stage-0 source and the backdrop's positioning/frame-driver/#112 concerns, all
 * of which are now guarded on [skylight].
 *
 * ## #112 cyclic RenderNode guard
 * A backdrop node is a descendant of the Sky recorder, so the capture pass re-enters its draw. Drawing
 * a backdrop-sampling effect into the layer being recorded would form a cyclic `RenderNode` graph that
 * overflows the render thread (https://github.com/skydoves/Cloudy/issues/112), so [draw] returns on its
 * first line while the sky is capturing.
 */
@OptIn(ExperimentalMirage::class)
internal class WeatherNode(
  private val weather: Weather,
  var skylight: Skylight,
  var clock: MirageClock,
  var enabled: Boolean,
  stages: List<Stage>,
) : Modifier.Node(),
  DrawModifierNode,
  GlobalPositionAwareModifierNode,
  CompositionLocalConsumerModifierNode {

  var stages: List<Stage> = stages
    private set

  /** Owns the filter-layer pool and the stage-chaining draw; the node keeps only clock + binding. */
  val chain = MirageFilterChain()

  // Samples the sky backdrop through a rasterized snapshot instead of a live drawLayer(backgroundLayer).
  // The backdrop node is a descendant of the sky recorder, so backgroundLayer's displaylist embeds this
  // node; recording drawLayer(backgroundLayer) into a filter layer closes a skyLayer->thisNode->skyLayer
  // RenderNode cycle that overflows the render thread under captureToImage/PixelCopy (issue #112). Drawing
  // bitmap pixels has no back-edge. Backdrop-only; unused for a self-lit source.
  private val backdropSnapshot = MirageBackdropSnapshot()

  // Async GLES backdrop runner (API 29-32 band, where a filter is a Blit, not a RenderEffect). Lazily
  // used only when an applicable filter reports FilterApplication.Blit; null-cost otherwise. Backdrop-only.
  private val glesBackdrop = MirageGlesBackdrop()

  /** Backdrop-only: this node's root position, from which the backdrop sample offset is derived. */
  private var positionInRoot: Offset = Offset.Zero

  /**
   * mirageTime, in seconds. Auto advances it from the frame loop; Fixed writes a constant; Paused
   * freezes the last value. Plain field (not snapshot state): the clock loop invalidateDraw()s
   * explicitly, and Fixed/Paused change only when the node is re-created on a new key.
   */
  private var timeSeconds: Float = 0f

  /** Wall-clock nanos of the first Auto frame, so timeSeconds is measured from attach. */
  private var startNanos: Long = -1L

  /**
   * True if any stage's compiled kernel references mirageTime - computed once at attach from the
   * (already-warmed) cache, so the draw loop never re-queries it.
   */
  private var planUsesTime: Boolean = false

  // The running Auto-clock frame loop, if any. Cancelled before a re-warm launches a new one so a
  // structural update never leaves two loops advancing timeSeconds and invalidating in parallel.
  private var frameLoopJob: Job? = null

  // Stable re-blur invalidator (a field, not a fresh lambda) so the frame driver can identity-match it
  // in addOverlay/removeOverlay — a new lambda each attach would never unregister. Backdrop-only.
  private val reblur: () -> Unit = { if (isAttached) invalidateDraw() }

  fun update(skylight: Skylight, clock: MirageClock, enabled: Boolean, stages: List<Stage>) {
    // Re-register the scroll-refresh overlay on the new sky before adopting it: a sky swap must move
    // the frame-driver registration or the old sky keeps pumping this node while the new one never
    // does. Only meaningful when both sides are backdrops (the facade never swaps SelfLit <-> Backdrop
    // on one node — that is a distinct element, so update() is not called across the split).
    val current = skylight
    val previous = this.skylight
    if (current is Skylight.Backdrop && previous is Skylight.Backdrop &&
      previous.sky != current.sky && isAttached
    ) {
      previous.sky.frameDriver.removeOverlay(reblur)
      current.sky.frameDriver.addOverlay(reblur)
      // The cached snapshot / GLES blit came from the old sky's layer and key on contentVersion + size;
      // a new sky reusing the old version/size would wrongly hit the stale bitmap, so drop both.
      backdropSnapshot.dispose()
      glesBackdrop.release()
    }

    // A recomposition re-creates the params blocks every time (they are lambdas), so the element is
    // unequal — and thus update() runs — on every recomposition even when only the blocks changed. If
    // the structural plan (skylight, clock, enabled, and the ordered stage optics/kinds/blend modes) is
    // unchanged, only the per-draw blocks moved: swap the stage list and redraw without tearing down
    // the layers or re-warming the cache. sameStructure is exactly the structural half of
    // WeatherElement.equals (which additionally requires the blocks to match), so an unequal element
    // with an unchanged structure lands here.
    val structuralChange = skylight != this.skylight || clock != this.clock ||
      enabled != this.enabled || !sameStructure(this.stages, stages)

    this.skylight = skylight
    this.clock = clock
    this.enabled = enabled
    this.stages = stages

    if (!structuralChange) {
      // Blocks-only change: the layers still match the (unchanged) filter count, so keep them.
      if (isAttached) invalidateDraw()
      return
    }

    // Structural change: the filter count may differ, so drop the pooled layers. The chain re-sizes
    // itself lazily on the next draw. (When detached there are no pooled layers to release.)
    if (isAttached) {
      chain.release(requireGraphicsContext())
      warmAndSchedule()
      invalidateDraw()
    }
  }

  override fun onAttach() {
    warmAndSchedule()
    val skylight = skylight
    if (skylight is Skylight.Backdrop) skylight.sky.frameDriver.addOverlay(reblur)
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    // Only the backdrop sample offset is derived from this position, so a self-lit node has nothing to
    // track here.
    if (skylight !is Skylight.Backdrop) return
    val newPosition = coordinates.positionInRoot()
    if (newPosition != positionInRoot) {
      // A move must redraw or the node keeps sampling the region it used to sit over.
      positionInRoot = newPosition
      if (isAttached) invalidateDraw()
    }
  }

  /**
   * Warm the cache for every stage (so usesTime is known and the draw loop enters with programs
   * ready), then start the frame loop when the clock is Auto and some stage is time-driven.
   */
  private fun warmAndSchedule() {
    val dialect = currentDialect()
    var usesTime = false
    for (stage in stages) {
      val cached = MirageProgramCache.obtain(stage.optic, dialect) ?: continue
      if (cached.compiled.usesTime) usesTime = true
    }
    planUsesTime = usesTime
    startNanos = -1L

    // Cancel a prior loop before starting a new one: a re-warm on structural update would otherwise
    // stack loops that all advance timeSeconds and invalidate.
    frameLoopJob?.cancel()
    frameLoopJob = if (clock is MirageClock.Auto && usesTime) {
      coroutineScope.launch {
        while (isActive) {
          withFrameNanos { now ->
            if (startNanos < 0L) startNanos = now
            timeSeconds = ((now - startNanos) / 1_000_000_000f) % TIME_WRAP_SECONDS
            invalidateDraw()
          }
        }
      }
    } else {
      null
    }
  }

  override fun ContentDrawScope.draw() {
    // Draw nothing while this sky is recording: keeping this node out of the blur/grade source avoids
    // the cyclic RenderNode graph that crashes the render thread (issues/112). Must be the first
    // statement. See [Sky.isCapturing].
    val skylight = skylight
    if (skylight is Skylight.Backdrop && skylight.sky.isCapturing) {
      return
    }

    if (!enabled || stages.isEmpty()) {
      drawContent()
      return
    }

    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) {
      drawContent()
      return
    }

    val contentScope = this
    when (skylight) {
      is Skylight.SelfLit -> {
        // Stage 0 records the node's own content (no sky layer, no #112 cycle).
        with(weather) { draw(this@WeatherNode, recordSource = { contentScope.drawContent() }) }
        // A self-lit plan's filters replace the content, so the chain already drew it — nothing more.
      }

      is Skylight.Backdrop -> {
        val backgroundLayer = skylight.sky.backgroundLayer
        if (backgroundLayer == null) {
          // No captured backdrop yet: nothing to sample, so just draw this node's own content.
          drawContent()
          return
        }
        drawBackdrop(skylight.sky, backgroundLayer, width, height)
        // A backdrop grades the region *behind* the node, so the node's own content is drawn on top.
        drawContent()
      }
    }
  }

  /**
   * Draws the mirage backdrop for this frame. Two draw paths that must both stay acyclic under
   * captureToImage/PixelCopy (#112):
   *
   *  - **AGSL / skiko** (and API < 33 pass-through): the chain records the backdrop region and applies
   *    each stage's content-bound `RenderEffect`. Stage 0 samples a rasterized [backdropSnapshot], never
   *    a live `drawLayer(backgroundLayer)`, so no `skyLayer -> thisNode -> skyLayer` cycle forms.
   *  - **GLES** (API 29-32): the first applicable filter is a [FilterApplication.Blit] (async FBO
   *    capture) the synchronous chain cannot run. The snapshot region is the on-screen placeholder while
   *    an async capture + blit runs; the blit INPUT is captured off-screen from a live `drawLayer` and
   *    released within the runner, never reachable from the on-screen tree, so it does not cycle either.
   */
  private fun ContentDrawScope.drawBackdrop(
    sky: com.skydoves.cloudy.Sky,
    backgroundLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    width: Float,
    height: Float,
  ) {
    // Backdrop-relative offset — this node's root position minus the sky origin.
    val skyBounds = sky.sourceBounds
    val offsetX = positionInRoot.x - skyBounds.left
    val offsetY = positionInRoot.y - skyBounds.top

    // Keep the acyclic snapshot fresh (async, coalesced on contentVersion); the previously cached bitmap
    // keeps drawing until the new capture lands.
    backdropSnapshot.requestIfStale(
      coroutineScope = coroutineScope,
      layer = backgroundLayer,
      contentVersion = sky.contentVersion,
      invalidate = { if (isAttached) invalidateDraw() },
    )

    // On-screen backdrop region, sampled from the rasterized snapshot rather than a live
    // drawLayer(backgroundLayer): the live layer closes the issue-112 RenderNode cycle under
    // captureToImage/PixelCopy. Everything drawn into the on-screen tree uses this.
    val recordSnapshot: DrawScope.() -> Unit = {
      with(backdropSnapshot) {
        drawSampledRegion(Offset(offsetX, offsetY), IntSize(width.toInt(), height.toInt()))
      }
    }

    // Live backdrop region for the GLES blit INPUT only: that layer is captured to a bitmap and released
    // off-screen within the runner, never reachable from the on-screen tree, so it does not cycle under
    // PixelCopy — and staying live keeps the version-keyed blit from caching a not-yet-ready snapshot.
    val recordLive: DrawScope.() -> Unit = {
      drawContext.canvas.save()
      drawContext.canvas.translate(-offsetX, -offsetY)
      drawLayer(backgroundLayer)
      drawContext.canvas.restore()
    }

    // API 29-32 GLES band: the first applicable filter is a Blit (async FBO capture) the sync chain
    // cannot run. prepareGlesBlit binds this draw's uniforms into a fresh per-draw list and returns the
    // transform; the async runner captures the region and applies it. Other bands' filters (Effect/
    // ColorFilter) go through the chain as before. Single-stage on this band by scope.
    val dialect = currentDialect()
    val density = density()
    val time = resolveTime()
    val glesFilter = stages.filterIsInstance<Stage.Filter>().firstNotNullOfOrNull { stage ->
      val cached = MirageProgramCache.obtain(stage.optic, dialect) ?: return@firstNotNullOfOrNull null
      if (cached.backend.filterApplication() is FilterApplication.Blit) stage to cached else null
    }
    val glesBlit = glesFilter?.let { (stage, cached) ->
      cached.backend.prepareGlesBlit(
        cached,
        stage.params,
        stage.paramsBlock,
        width,
        height,
        density,
        time,
      )
    }

    if (glesBlit != null) {
      with(glesBackdrop) {
        draw(
          context = graphicsContext(),
          scope = coroutineScope,
          blit = glesBlit,
          contentVersion = sky.contentVersion,
          recordRaw = recordSnapshot,
          recordInput = recordLive,
          invalidate = { if (isAttached) invalidateDraw() },
        )
      }
    } else {
      // AGSL / skiko / API < 33 pass-through: the chain grades the acyclic snapshot region.
      with(weather) { draw(this@WeatherNode, recordSnapshot) }
    }
  }

  /** Resolves the current mirageTime for this draw; called by the [Weather] when binding uniforms. */
  fun resolveTime(): Float = when (val clock = clock) {
    is MirageClock.Auto -> if (planUsesTime) timeSeconds else 0f
    is MirageClock.Paused -> timeSeconds
    is MirageClock.Fixed -> clock.seconds
  }

  // Node-scoped accessors for the [Weather]: requireGraphicsContext / currentValueOf are extension
  // functions on the node's receiver, unreachable from a Weather instance, so the node exposes them.
  fun graphicsContext(): GraphicsContext = requireGraphicsContext()

  fun density(): Float = currentValueOf(LocalDensity).density

  override fun onDetach() {
    val skylight = skylight
    if (skylight is Skylight.Backdrop) skylight.sky.frameDriver.removeOverlay(reblur)
    // coroutineScope is cancelled on detach anyway; null the handle so a re-attach starts clean.
    frameLoopJob?.cancel()
    frameLoopJob = null
    chain.release(requireGraphicsContext())
    backdropSnapshot.dispose()
    glesBackdrop.release()
  }
}
