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
import com.skydoves.cloudy.CloudyState
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.MirageClock
import com.skydoves.cloudy.Sky
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orchestrates a pipeline against a stage-0 source: a null [sky] runs it over the node's own content, a
 * non-null [sky] over the offset [Sky][com.skydoves.cloudy.Sky] region behind it. The
 * node owns the ordered [stages], the [MirageClock] frame loop, the layer-pool [chain], the
 * [PostProcess] (clip/tint/highlight) around the effect, and (for a backdrop) the positioning +
 * frame-driver lifecycle; the [Effect] owns the actual draw. It never compiles a shader or owns
 * effect-cache state — that lives in the [Effect] (blur layer/effect, legacy bitmaps) or
 * [MirageProgramCache] (programs).
 *
 * ## Effect adoption on config change
 * [update] swaps [stages]/[postProcess]/[onStateChanged] on the cheap path, but a change to an
 * *effect-config* value (blur's `cpuBlurEnabled` / scrim tint) can't be swapped into the existing
 * effect — those are constructor state of the [Effect]. The element carries an [effectKey] value:
 * when it differs, [update] treats it as a structural change and adopts the fresh [effect] instance
 * (releasing the old one's caches first). [effect] is therefore a `var`.
 *
 * ## #112 cyclic RenderNode guard
 * A backdrop node is a descendant of the Sky recorder, so the capture pass re-enters its draw. Drawing
 * a backdrop-sampling effect into the layer being recorded would form a cyclic `RenderNode` graph that
 * overflows the render thread (https://github.com/skydoves/Cloudy/issues/112), so [draw] returns on its
 * first line while the sky is capturing.
 */
@OptIn(ExperimentalMirage::class)
internal class EffectNode(
  effect: Effect,
  var sky: Sky?,
  var clock: MirageClock,
  var enabled: Boolean,
  stages: List<Stage>,
  var postProcess: PostProcess,
  private var effectKey: Any?,
  var onStateChanged: ((CloudyState) -> Unit)?,
) : Modifier.Node(),
  DrawModifierNode,
  GlobalPositionAwareModifierNode,
  CompositionLocalConsumerModifierNode {

  var effect: Effect = effect
    private set

  var stages: List<Stage> = stages
    private set

  /** Owns the filter-layer pool and the stage-chaining draw for program stages; blur bypasses it. */
  val chain = MirageFilterChain()

  /** Reusable clip-path / highlight-brush caches for [applyPostProcess]; released on detach. */
  val postProcessCache = PostProcessCache()

  // Samples the sky backdrop through a rasterized snapshot instead of a live drawLayer(backgroundLayer).
  // The backdrop node is a descendant of the sky recorder, so backgroundLayer's displaylist embeds this
  // node; recording drawLayer(backgroundLayer) into a filter layer closes a skyLayer->thisNode->skyLayer
  // RenderNode cycle that overflows the render thread under captureToImage/PixelCopy (issue #112). Drawing
  // bitmap pixels has no back-edge. Backdrop-only; the blur Weather self-samples and ignores this source.
  private val backdropSnapshot = MirageBackdropSnapshot()

  // Async GLES backdrop runner (API 29-32 band, where a mirage filter is a Blit, not a RenderEffect).
  // Lazily used only when an applicable filter reports FilterApplication.Blit; null-cost otherwise.
  private val glesBackdrop = MirageGlesBackdrop()

  /** Backdrop-only: this node's root position, from which the backdrop sample offset is derived. */
  private var positionInRoot: Offset = Offset.Zero

  /** mirageTime, in seconds. Plain field (not snapshot state); the clock loop invalidateDraw()s. */
  private var timeSeconds: Float = 0f

  /** Wall-clock nanos of the first Auto frame, so timeSeconds is measured from attach. */
  private var startNanos: Long = -1L

  /** True if any program stage's compiled kernel references mirageTime (computed at warm time). */
  private var pipelineUsesTime: Boolean = false

  // The running Auto-clock frame loop, if any. Cancelled before a re-warm launches a new one.
  private var frameLoopJob: Job? = null

  // Stable re-blur invalidator (a field, not a fresh lambda) so the frame driver can identity-match it.
  private val reblur: () -> Unit = { if (isAttached) invalidateDraw() }

  // Last state relayed to onStateChanged, so a steady-state frame does not re-notify the same state.
  private var lastNotifiedState: CloudyState? = null

  fun update(
    effect: Effect,
    sky: Sky?,
    clock: MirageClock,
    enabled: Boolean,
    stages: List<Stage>,
    postProcess: PostProcess,
    effectKey: Any?,
    onStateChanged: ((CloudyState) -> Unit)?,
  ) {
    // Re-register the scroll-refresh overlay on the new sky before adopting it.
    val previous = this.sky
    if (sky != null && previous != null && previous != sky && isAttached) {
      previous.frameDriver.removeOverlay(reblur)
      sky.frameDriver.addOverlay(reblur)
      // The cached snapshot / GLES blit came from the old sky's layer and key on contentVersion + size;
      // a new sky reusing the old version/size would wrongly hit the stale bitmap, so drop both.
      backdropSnapshot.dispose()
      glesBackdrop.release()
    }

    // An effect-config change (blur cpuBlurEnabled / scrim tint) can't be swapped into the live
    // effect instance, so a differing effectKey is a structural change that adopts the new one.
    val effectChanged = effectKey != this.effectKey
    val structuralChange = effectChanged || sky != this.sky || clock != this.clock ||
      enabled != this.enabled || !sameStructure(this.stages, stages)

    if (structuralChange && isAttached) {
      // Release the old effect's caches before adopting the new one / a new pipeline structure.
      this.effect.detach(this)
    }

    this.effect = effect
    this.sky = sky
    this.clock = clock
    this.enabled = enabled
    this.stages = stages
    this.postProcess = postProcess
    this.effectKey = effectKey
    this.onStateChanged = onStateChanged

    if (!structuralChange) {
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
    sky?.frameDriver?.addOverlay(reblur)
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    if (sky == null) return
    val newPosition = coordinates.positionInRoot()
    if (newPosition != positionInRoot) {
      positionInRoot = newPosition
      if (isAttached) invalidateDraw()
    }
  }

  /**
   * Warm the cache for every program stage (so usesTime is known and the draw loop enters with
   * programs ready), then start the frame loop when the clock is Auto and some stage is time-driven.
   * Only [Stage.ProgramFilter]/[Stage.Overlay] touch [MirageProgramCache]; [Stage.PlatformFilter]
   * (blur) has no program to warm.
   */
  private fun warmAndSchedule() {
    val dialect = currentDialect()
    var usesTime = false
    for (stage in stages) {
      val shader = when (stage) {
        is Stage.ProgramFilter -> stage.shader
        is Stage.Overlay -> stage.shader
        is Stage.PlatformFilter -> continue
      }
      val cached = MirageProgramCache.obtain(shader, dialect) ?: continue
      if (cached.compiled.usesTime) usesTime = true
    }
    pipelineUsesTime = usesTime
    startNanos = -1L

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
    // #112: draw nothing while this sky is recording. Must be the first statement.
    val sky = sky
    if (sky != null && sky.isCapturing) {
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

    val recordSource: DrawScope.() -> Unit
    if (sky == null) {
      recordSource = { this@draw.drawContent() }
    } else {
      val backgroundLayer = sky.backgroundLayer
      if (backgroundLayer == null) {
        // No captured backdrop yet: draw this node's own content and relay any state.
        drawContent()
        relayState()
        return
      }
      val skyBounds = sky.sourceBounds
      val backdropOffset = Offset(
        positionInRoot.x - skyBounds.left,
        positionInRoot.y - skyBounds.top,
      )

      if (backdropNeedsAcyclicSnapshot()) {
        // Android: sample the offset-shifted Sky region through a rasterized snapshot rather than a live
        // drawLayer(backgroundLayer). Recording the live layer into an Effect's filter layer closes the
        // issue-112 RenderNode cycle that captureToImage/PixelCopy overflows (the mirage chain records
        // recordSource into a layer; the blur Effect self-samples and ignores this source). The snapshot
        // capture is async, coalesced on contentVersion; the previous bitmap keeps drawing until it lands.
        backdropSnapshot.requestIfStale(
          coroutineScope = coroutineScope,
          layer = backgroundLayer,
          contentVersion = sky.contentVersion,
          invalidate = { if (isAttached) invalidateDraw() },
        )
        recordSource = {
          with(backdropSnapshot) {
            drawSampledRegion(backdropOffset, IntSize(width.toInt(), height.toInt()))
          }
        }
      } else {
        // Skiko: Skia has no #112 cycle overflow, so sample the live sky layer directly — synchronous in
        // this draw pass (no async snapshot latency).
        recordSource = {
          drawContext.canvas.save()
          drawContext.canvas.translate(-backdropOffset.x, -backdropOffset.y)
          drawLayer(backgroundLayer)
          drawContext.canvas.restore()
        }
      }

      // API 29-32 GLES band: a mirage filter is a Blit (async FBO capture) the synchronous chain cannot
      // run, so it is routed to the async GLES runner here — before the generic Effect path, which
      // cannot express a suspend blit. Returns true if it handled this frame. Android-only (GLES is
      // never resolved on skiko: prepareGlesBlit returns null there), backdrop-only, and mirage-only
      // (blur is never a Blit); every other band falls through to the generic path below.
      if (drawBackdropGles(sky, backgroundLayer, backdropOffset, width, height)) {
        drawContent()
        relayState()
        return
      }
    }

    val intSize = IntSize(width.toInt(), height.toInt())

    // Scrim tier (API < 31 scrim): draw the scrim over the backdrop region, then this node's content
    // behind, without running the effect. The scrim carries the tint itself (no PostProcess tint or
    // highlight), so only the shape clip applies.
    if (effect.shouldDrawContentBehind(this@EffectNode)) {
      val contentScope = this
      clipToShape(postProcess.shape, intSize, postProcessCache, clipRectangle = true) {
        with(effect) { contentScope.drawScrim(this@EffectNode, recordSource) }
      }
      if (sky != null) drawContent()
      relayState()
      return
    }

    // A backdrop is always clipped to the node bounds (its blur layer's CLAMP bloom must not smear
    // over siblings); the content-source blur is deliberately unclipped so its bloom bleeds like the
    // pre-pipeline graphicsLayer render effect did. See [clipToShape].
    applyPostProcess(postProcess, intSize, postProcessCache, clipRectangle = sky != null) {
      with(effect) { draw(this@EffectNode, recordSource) }
    }

    // A content-source pipeline's filters replace the content, so the chain already drew it. A backdrop
    // grades the region behind the node, so the node's own content is drawn on top.
    if (sky != null) drawContent()
    relayState()
  }

  /**
   * API 29-32 GLES mirage backdrop: if the first applicable filter is a [FilterApplication.Blit] (async
   * FBO capture the sync chain cannot run), route it to the [glesBackdrop] runner and return `true`.
   * Returns `false` for every other case (self-lit is filtered upstream; blur is never a Blit; AGSL /
   * skiko / API < 33 apply through the generic chain path), leaving the caller to run the normal path.
   *
   * The blit INPUT is captured off-screen from a live `drawLayer` and released within the runner, never
   * reachable from the on-screen tree, so it does not cycle under PixelCopy — and staying live keeps the
   * version-keyed blit from caching a not-yet-ready snapshot. The on-screen placeholder is the acyclic
   * snapshot ([recordRaw]).
   */
  private fun ContentDrawScope.drawBackdropGles(
    sky: com.skydoves.cloudy.Sky,
    backgroundLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    offset: Offset,
    width: Float,
    height: Float,
  ): Boolean {
    val dialect = currentDialect()
    val density = density()
    val time = resolveTime()

    // First applicable filter that reports a Blit (the async GLES FBO path); single-stage on this band.
    var stage: Stage.ProgramFilter? = null
    var cached: CachedProgram? = null
    for (candidate in stages) {
      if (candidate !is Stage.ProgramFilter) continue
      val program = MirageProgramCache.obtain(candidate.shader, dialect) ?: continue
      if (program.backend.filterApplication() is FilterApplication.Blit) {
        stage = candidate
        cached = program
        break
      }
    }
    val blitStage = stage ?: return false
    val blitProgram = cached ?: return false

    val glesBlit = blitProgram.backend.prepareGlesBlit(
      blitProgram,
      blitStage.params,
      blitStage.paramsBlock,
      width,
      height,
      density,
      time,
    ) ?: return false

    val recordRaw: DrawScope.() -> Unit = {
      with(backdropSnapshot) {
        drawSampledRegion(offset, IntSize(width.toInt(), height.toInt()))
      }
    }
    val recordInput: DrawScope.() -> Unit = {
      drawContext.canvas.save()
      drawContext.canvas.translate(-offset.x, -offset.y)
      drawLayer(backgroundLayer)
      drawContext.canvas.restore()
    }
    with(glesBackdrop) {
      draw(
        context = graphicsContext(),
        scope = coroutineScope,
        blit = glesBlit,
        contentVersion = sky.contentVersion,
        recordRaw = recordRaw,
        recordInput = recordInput,
        invalidate = { if (isAttached) invalidateDraw() },
      )
    }
    return true
  }

  private fun relayState() {
    val s = effect.currentState(this) ?: return
    if (s != lastNotifiedState) {
      lastNotifiedState = s
      onStateChanged?.invoke(s)
    }
  }

  /** Resolves the current mirageTime for this draw; called by the [Effect] when binding uniforms. */
  fun resolveTime(): Float = when (val clock = clock) {
    is MirageClock.Auto -> if (pipelineUsesTime) timeSeconds else 0f
    is MirageClock.Paused -> timeSeconds
    is MirageClock.Fixed -> clock.seconds
  }

  // Node-scoped accessors for the [Effect]: these are extension functions on the node's receiver,
  // unreachable from an Effect instance, so the node exposes them.
  fun graphicsContext(): GraphicsContext = requireGraphicsContext()

  /** Node-scoped `invalidateDraw()` for the [Effect] (an extension unreachable from a plain instance). */
  fun invalidate() {
    if (isAttached) invalidateDraw()
  }

  fun density(): Float = currentValueOf(LocalDensity).density

  /** Backdrop-relative sample offset for this draw; Zero for a content source. Read by the legacy blur machines. */
  fun sampleOffset(): Offset {
    val sky = sky ?: return Offset.Zero
    val skyBounds = sky.sourceBounds
    return Offset(positionInRoot.x - skyBounds.left, positionInRoot.y - skyBounds.top)
  }

  override fun onDetach() {
    sky?.frameDriver?.removeOverlay(reblur)
    frameLoopJob?.cancel()
    frameLoopJob = null
    effect.detach(this)
    chain.release(requireGraphicsContext())
    postProcessCache.clear()
    backdropSnapshot.dispose()
    glesBackdrop.release()
  }
}
