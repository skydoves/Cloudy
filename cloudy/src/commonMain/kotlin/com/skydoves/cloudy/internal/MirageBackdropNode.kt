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
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.MirageClock
import com.skydoves.cloudy.Sky
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs a mirage plan against a [Sky] **backdrop** instead of the node's own content: the sibling of
 * [MirageNode] that records the offset Sky region into stage 0 (via [MirageFilterChain]) so a colorize
 * optic grades the backdrop rather than the content. It is a separate node because a backdrop is
 * positioned relative to the Sky and refreshed as the Sky scrolls, so it carries positioning and the
 * frame-driver lifecycle that a pure self-content node has no reason to.
 *
 * A backdrop node is a descendant of the Sky recorder, so the capture pass re-enters its draw. Drawing
 * a backdrop-sampling effect into the layer being recorded would form a cyclic `RenderNode` graph that
 * overflows the render thread (https://github.com/skydoves/Cloudy/issues/112), so [draw] returns on its
 * first line while [Sky.isCapturing].
 */
@OptIn(ExperimentalMirage::class)
internal class MirageBackdropNode(
  var sky: Sky,
  var clock: MirageClock,
  var enabled: Boolean,
  stages: List<Stage>,
) : Modifier.Node(),
  DrawModifierNode,
  GlobalPositionAwareModifierNode,
  CompositionLocalConsumerModifierNode {

  var stages: List<Stage> = stages
    private set

  private var positionInRoot: Offset = Offset.Zero

  private val chain = MirageFilterChain()

  // Async GLES backdrop runner (API 29-32 band, where a filter is a Blit, not a RenderEffect). Lazily
  // used only when an applicable filter reports FilterApplication.Blit; null-cost otherwise.
  private val glesBackdrop = MirageGlesBackdrop()

  // Same clock machinery as MirageNode: this small duplication is deliberate (the clock is a node
  // concern, not a chain concern, and forcing it into the shared chain would drag lifecycle in).
  private var timeSeconds: Float = 0f
  private var startNanos: Long = -1L
  private var planUsesTime: Boolean = false

  // The running Auto-clock frame loop, if any. Cancelled before a re-warm launches a new one so a
  // structural update never leaves two loops advancing timeSeconds and invalidating in parallel.
  private var frameLoopJob: Job? = null

  // Stable re-blur invalidator (a field, not a fresh lambda) so the frame driver can identity-match it
  // in addOverlay/removeOverlay — a new lambda each attach would never unregister.
  private val reblur: () -> Unit = { if (isAttached) invalidateDraw() }

  fun update(sky: Sky, clock: MirageClock, enabled: Boolean, stages: List<Stage>) {
    // Re-register the scroll-refresh overlay on the new sky before adopting it, mirroring the cloudy
    // backdrop node: a sky swap must move the frame-driver registration or the old sky keeps pumping
    // this node while the new one never does.
    if (this.sky != sky && isAttached) {
      this.sky.frameDriver.removeOverlay(reblur)
      sky.frameDriver.addOverlay(reblur)
    }

    val structuralChange = sky != this.sky || clock != this.clock || enabled != this.enabled ||
      !sameStructure(this.stages, stages)

    this.sky = sky
    this.clock = clock
    this.enabled = enabled
    this.stages = stages

    if (!structuralChange) {
      // Blocks-only change: the layers still match the (unchanged) filter count, so keep them.
      if (isAttached) invalidateDraw()
      return
    }

    if (isAttached) {
      chain.release(requireGraphicsContext())
      warmAndSchedule()
      invalidateDraw()
    }
  }

  override fun onAttach() {
    warmAndSchedule()
    sky.frameDriver.addOverlay(reblur)
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    val newPosition = coordinates.positionInRoot()
    if (newPosition != positionInRoot) {
      // The backdrop sample offset is derived from this position, so a move must redraw or the node
      // keeps sampling the region it used to sit over.
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
    // the cyclic RenderNode graph that crashes the render thread (issues/112). See [Sky.isCapturing].
    if (sky.isCapturing) {
      return
    }

    if (!enabled) {
      drawContent()
      return
    }

    val backgroundLayer = sky.backgroundLayer
    if (backgroundLayer == null) {
      // No captured backdrop yet: nothing to sample, so just draw this node's own content.
      drawContent()
      return
    }

    // The shader resolution is the ContentDrawScope's canvas size, exactly as MirageNode binds it.
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) {
      drawContent()
      return
    }

    val dialect = currentDialect()
    val density = currentValueOf(LocalDensity).density
    val time = timeFor(clock)

    // Backdrop-relative offset — this node's root position minus the sky origin, exactly as the cloudy
    // backdrop path computes it (CloudyBackground.android.kt:397-399).
    val skyBounds = sky.sourceBounds
    val offsetX = positionInRoot.x - skyBounds.left
    val offsetY = positionInRoot.y - skyBounds.top

    val filters = stages.filterIsInstance<Stage.Filter>()
    val overlays = stages.filterIsInstance<Stage.Overlay>()

    val applicable = filters.mapNotNull { stage ->
      val cached = MirageProgramCache.obtain(stage.optic, dialect) ?: return@mapNotNull null
      stage to cached
    }

    val recordSource: DrawScope.() -> Unit = {
      // The offset-shifted Sky region (a direct port of the cloudy backdrop record,
      // CloudyBackground.android.kt:550-559). When no stage is applicable (e.g. API 23-28 lens), the
      // chain / GLES runner draws this same region raw.
      drawContext.canvas.save()
      drawContext.canvas.translate(-offsetX, -offsetY)
      drawLayer(backgroundLayer)
      drawContext.canvas.restore()
    }

    // API 29-32 GLES band: the first applicable filter is a Blit (async FBO capture), which the sync
    // chain cannot run. prepareGlesBlit binds this draw's uniforms into a fresh per-draw list and
    // returns the transform; the async runner captures the region and applies it. Other filters
    // (Effect/ColorFilter) go through the chain as before. Single-stage on this band by scope.
    val glesFilter = applicable.firstOrNull { (_, cached) ->
      cached.backend.filterApplication() is FilterApplication.Blit
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
          context = requireGraphicsContext(),
          scope = coroutineScope,
          blit = glesBlit,
          contentVersion = sky.contentVersion,
          recordSource = recordSource,
          invalidate = { if (isAttached) invalidateDraw() },
        )
      }
    } else {
      with(chain) {
        draw(
          context = requireGraphicsContext(),
          applicable = applicable,
          bind = { stage, cached ->
            bindUniforms(cached, stage.params, stage.paramsBlock, width, height, density, time)
          },
          recordSource = recordSource,
        )
      }
    }

    drawOverlays(overlays, width, height, density, time)

    drawContent()
  }

  /**
   * Overlays: composite each generator over the graded backdrop via a ShaderBrush under the stage's
   * blend mode — the same overlay path [MirageNode] uses, applied over the backdrop result.
   */
  private fun ContentDrawScope.drawOverlays(
    overlays: List<Stage.Overlay>,
    width: Float,
    height: Float,
    density: Float,
    time: Float,
  ) {
    val dialect = currentDialect()
    for (stage in overlays) {
      val cached = MirageProgramCache.obtain(stage.optic, dialect) ?: continue
      bindUniforms(cached, stage.params, stage.paramsBlock, width, height, density, time)
      drawRect(brush = cached.backend.asShaderBrush(), blendMode = stage.blendMode)
    }
  }

  private fun timeFor(clock: MirageClock): Float = when (clock) {
    is MirageClock.Auto -> if (planUsesTime) timeSeconds else 0f
    is MirageClock.Paused -> timeSeconds
    is MirageClock.Fixed -> clock.seconds
  }

  override fun onDetach() {
    sky.frameDriver.removeOverlay(reblur)
    // coroutineScope is cancelled on detach anyway; null the handle so a re-attach starts clean.
    frameLoopJob?.cancel()
    frameLoopJob = null
    chain.release(requireGraphicsContext())
    glesBackdrop.release()
  }
}
