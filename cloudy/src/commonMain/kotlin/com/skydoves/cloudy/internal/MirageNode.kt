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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.FilterOptic
import com.skydoves.cloudy.GenerateOptic
import com.skydoves.cloudy.MirageClock
import com.skydoves.cloudy.MirageParams
import com.skydoves.cloudy.MirageScope
import com.skydoves.cloudy.Optic
import com.skydoves.cloudy.UColor
import com.skydoves.cloudy.UFloat
import com.skydoves.cloudy.UFloatArray
import com.skydoves.cloudy.UInt1
import com.skydoves.cloudy.UOffset
import com.skydoves.cloudy.USize
import com.skydoves.cloudy.UTexture
import com.skydoves.cloudy.UVec3
import com.skydoves.cloudy.UVec4
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The shading language the current platform runs (Android = AGSL, every skiko target = SKSL). */
internal expect fun currentDialect(): Dialect

/**
 * Standard uniform names the compiler emits on demand. The node binds each one only when the compiled
 * program declared it: Android's RuntimeShader throws IllegalArgumentException on a write to an
 * undeclared uniform (skiko tolerates it), so the CompiledProgram.uses* flags gate every bind.
 */
private const val STD_RESOLUTION = "mirageResolution"
private const val STD_TIME = "mirageTime"
private const val STD_DENSITY = "mirageDensity"

/** mirageTime wraps here so a long session never grows the argument enough to decay float32 sin(). */
private const val TIME_WRAP_SECONDS = 3600f

/**
 * One declared stage of a mirage plan. Sealed so the draw loop branches exhaustively over the two
 * application shapes. [params] is the single params instance the node mints once and reuses every
 * draw (no per-draw allocation); [paramsBlock] is the caller's per-draw uniform block, re-run each
 * draw against [params].
 */
internal sealed class Stage(
  val optic: Optic<*>,
  val params: MirageParams,
  val paramsBlock: (MirageParams.() -> Unit)?,
) {
  /** A content-transforming filter — applied as a content-bound render effect. */
  class Filter(
    optic: FilterOptic<*>,
    params: MirageParams,
    paramsBlock: (MirageParams.() -> Unit)?,
  ) : Stage(optic, params, paramsBlock)

  /** A content-free overlay generator — drawn over the content with [blendMode]. */
  class Overlay(
    optic: GenerateOptic<*>,
    params: MirageParams,
    paramsBlock: (MirageParams.() -> Unit)?,
    val blendMode: BlendMode,
  ) : Stage(optic, params, paramsBlock)
}

/**
 * Builds the immutable stage list for a plan by running the caller's `plan` block once. Each
 * `filter`/`overlay` call mints the optic's params instance (via its `paramsFactory`) and captures
 * the per-draw block; the built [stages] are what the node draws through.
 */
@OptIn(ExperimentalMirage::class)
internal class MiragePlanBuilder : MirageScope {

  val stages: MutableList<Stage> = mutableListOf()

  @Suppress("UNCHECKED_CAST")
  override fun <P : MirageParams> filter(optic: FilterOptic<P>, params: (P.() -> Unit)?) {
    // paramsFactory mints a P; the block is P.() -> Unit. Both are erased to MirageParams for storage
    // and re-cast at the (type-safe by construction) call site — the instance came from this optic.
    stages += Stage.Filter(optic, optic.paramsFactory(), params as (MirageParams.() -> Unit)?)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <P : MirageParams> overlay(
    optic: GenerateOptic<P>,
    blendMode: BlendMode,
    params: (P.() -> Unit)?,
  ) {
    stages += Stage.Overlay(
      optic,
      optic.paramsFactory(),
      params as (MirageParams.() -> Unit)?,
      blendMode,
    )
  }
}

/**
 * Orchestrates a mirage plan: it owns the ordered [stages], the [MirageClock] frame loop, and the
 * per-draw uniform binding, but delegates compilation + caching to [MirageProgramCache] and backend
 * application to the [MirageBackendProgram] seam. It never compiles a shader or owns cache state.
 *
 * ## Draw model (no fusion)
 * Filters chain through a stack of [GraphicsLayer]s — each stage records the previous stage's output,
 * sets its content-bound render effect, and hands its own layer forward. Overlays are then composited
 * over the filtered result. There is deliberately no cross-stage fusion in this milestone: each stage
 * is a separate program applied in sequence, which keeps the compiler and cache keyed on single
 * optics. Fusing adjacent kernels into one program is a later optimization.
 *
 * ## API < 33 / unsupported platform
 * A stage whose program cannot be built right now ([MirageProgramCache.obtain] returns `null`, e.g.
 * Android below API 33 where `RuntimeShader` is unavailable) is skipped individually: the content
 * still flows through the remaining stages rather than the whole plan going dark. On API < 33 *every*
 * stage returns `null`, so the net effect is a transparent pass-through of the original content.
 */
@OptIn(ExperimentalMirage::class)
internal class MirageNode(var clock: MirageClock, var enabled: Boolean, stages: List<Stage>) :
  Modifier.Node(),
  DrawModifierNode,
  CompositionLocalConsumerModifierNode {

  var stages: List<Stage> = stages
    private set

  /**
   * Reusable filter layers, one per filter stage, indexed by stage order. Rebuilt lazily on first
   * draw and released on detach; never allocated inside the steady-state draw.
   */
  private var filterLayers: Array<GraphicsLayer?> = arrayOfNulls(0)

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

  fun update(clock: MirageClock, enabled: Boolean, stages: List<Stage>) {
    // A recomposition re-creates the params blocks every time (they are lambdas), so the element is
    // unequal — and thus update() runs — on every recomposition even when only the blocks changed. If
    // the structural plan (clock, enabled, and the ordered stage optics/kinds/blend modes) is
    // unchanged, only the per-draw blocks moved: swap the stage list and redraw without tearing down
    // the layers or re-warming the cache. This keeps a params-driven animation cheap (no per-frame
    // layer churn) while still adopting the newly captured blocks so draw N runs recomposition N's
    // block. sameStructure is exactly the structural half of MirageElement.equals (which additionally
    // requires the blocks to match), so an unequal element with an unchanged structure lands here.
    val structuralChange = clock != this.clock || enabled != this.enabled ||
      !sameStructure(this.stages, stages)

    this.clock = clock
    this.enabled = enabled
    this.stages = stages

    if (!structuralChange) {
      // Blocks-only change: the layers still match the (unchanged) filter count, so keep them.
      if (isAttached) invalidateDraw()
      return
    }

    releaseLayers()
    filterLayers = arrayOfNulls(0)
    if (isAttached) {
      warmAndSchedule()
      invalidateDraw()
    }
  }

  override fun onAttach() {
    warmAndSchedule()
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

    if (clock is MirageClock.Auto && usesTime) {
      coroutineScope.launch {
        while (isActive) {
          withFrameNanos { now ->
            if (startNanos < 0L) startNanos = now
            timeSeconds = ((now - startNanos) / 1_000_000_000f) % TIME_WRAP_SECONDS
            invalidateDraw()
          }
        }
      }
    }
  }

  override fun ContentDrawScope.draw() {
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

    val dialect = currentDialect()
    val density = currentValueOf(LocalDensity).density
    val time = timeFor(clock)

    val filters = stages.filterIsInstance<Stage.Filter>()
    val overlays = stages.filterIsInstance<Stage.Overlay>()

    drawFilteredContent(filters, dialect, width, height, density, time)
    drawOverlays(overlays, dialect, width, height, density, time)
  }

  /**
   * Filters: record the running content into a per-stage layer, bind that stage's program, apply it
   * as the layer's content-bound render effect, then draw the layer - feeding the next stage. A stage
   * whose program is unavailable (API < 33) is skipped, leaving the running content untouched.
   */
  private fun ContentDrawScope.drawFilteredContent(
    filters: List<Stage.Filter>,
    dialect: Dialect,
    width: Float,
    height: Float,
    density: Float,
    time: Float,
  ) {
    val applicable = filters.mapNotNull { stage ->
      val cached = MirageProgramCache.obtain(stage.optic, dialect) ?: return@mapNotNull null
      stage to cached
    }

    if (applicable.isEmpty()) {
      // No filter ran (none declared, or all unsupported): draw the content as-is.
      drawContent()
      return
    }

    ensureFilterLayers(applicable.size)
    val context = requireGraphicsContext()

    for ((index, entry) in applicable.withIndex()) {
      val (stage, cached) = entry
      val layer = filterLayers[index]
        ?: context.createGraphicsLayer().also { filterLayers[index] = it }

      // Record the previous stage's output (or the original content for the first stage) into this
      // stage's layer, so the render effect below transforms exactly that.
      layer.record {
        if (index == 0) {
          this@drawFilteredContent.drawContent()
        } else {
          drawLayer(filterLayers[index - 1]!!)
        }
      }

      bindUniforms(cached, stage.params, stage.paramsBlock, width, height, density, time)
      layer.renderEffect = cached.backend.asContentRenderEffect()
    }

    // The last applicable filter's layer holds the fully chained result.
    drawLayer(filterLayers[applicable.lastIndex]!!)
  }

  /**
   * Overlays: the (already filtered) content was drawn above; here just composite each generator over
   * it via a ShaderBrush under the stage's blend mode.
   */
  private fun ContentDrawScope.drawOverlays(
    overlays: List<Stage.Overlay>,
    dialect: Dialect,
    width: Float,
    height: Float,
    density: Float,
    time: Float,
  ) {
    for (stage in overlays) {
      val cached = MirageProgramCache.obtain(stage.optic, dialect) ?: continue
      bindUniforms(cached, stage.params, stage.paramsBlock, width, height, density, time)
      drawRect(brush = cached.backend.asShaderBrush(), blendMode = stage.blendMode)
    }
  }

  /**
   * Resets each handle to its declared default, runs the caller's per-draw block, then pushes the
   * standard uniforms + every schema slot through the sink in declaration order.
   */
  private fun bindUniforms(
    cached: CachedProgram,
    params: MirageParams,
    paramsBlock: (MirageParams.() -> Unit)?,
    width: Float,
    height: Float,
    density: Float,
    time: Float,
  ) {
    // Reset to defaults so a value written on a previous draw does not leak when this draw's block
    // leaves it unset — the schema's declared default is the single source of truth per draw.
    resetToDefaults(params, cached.compiled.schema)
    paramsBlock?.invoke(params)

    val sink = cached.backend.uniformSink()

    // Standard uniforms first, each gated on whether the compiled kernel declared it: Android's
    // RuntimeShader throws on a write to an undeclared uniform name.
    val compiled = cached.compiled
    if (compiled.usesResolution) sink.float2(STD_RESOLUTION, width, height)
    if (compiled.usesTime) sink.float(STD_TIME, time)
    if (compiled.usesDensity) sink.float(STD_DENSITY, density)

    // Schema uniforms in declaration (= bind) order: the params' live handles and the compiled
    // schema entries share the same slot index, so entries[handle.slot] names each write.
    val entries = cached.compiled.schema.entries
    for (handle in params.handles) {
      val name = entries[handle.slot].name
      when (handle) {
        is UFloat -> sink.float(name, handle.value)
        is UOffset -> sink.float2(name, handle.value.x, handle.value.y)
        is USize -> sink.float2(name, handle.value.width, handle.value.height)
        is UInt1 -> sink.int(name, handle.value)
        is UVec3 -> sink.floatArray(name, handle.value)
        is UVec4 -> sink.floatArray(name, handle.value)
        is UFloatArray -> sink.floatArray(name, handle.value)
        is UColor -> sink.color(name, handle.value)
        is UTexture -> sink.texture(name, handle.value, handle.tileMode)
      }
    }
  }

  private fun ensureFilterLayers(count: Int) {
    if (filterLayers.size != count) {
      releaseLayers()
      filterLayers = arrayOfNulls(count)
    }
  }

  private fun timeFor(clock: MirageClock): Float = when (clock) {
    is MirageClock.Auto -> if (planUsesTime) timeSeconds else 0f
    is MirageClock.Paused -> timeSeconds
    is MirageClock.Fixed -> clock.seconds
  }

  private fun releaseLayers() {
    if (filterLayers.isEmpty()) return
    val context = requireGraphicsContext()
    for (i in filterLayers.indices) {
      filterLayers[i]?.let { context.releaseGraphicsLayer(it) }
      filterLayers[i] = null
    }
  }

  override fun onDetach() {
    releaseLayers()
  }
}

/**
 * True when two stage lists describe the same plan structure: same length and, in order, the same
 * stage kind, optic, and (for overlays) blend mode. The per-draw params blocks are deliberately not
 * compared — this is the "would the same programs and layer stack be built?" test that decides whether
 * [MirageNode.update] can take the cheap blocks-only path. Kept beside [MirageElement.equals], which
 * runs the identical comparison to decide element equality.
 */
@OptIn(ExperimentalMirage::class)
private fun sameStructure(a: List<Stage>, b: List<Stage>): Boolean {
  if (a.size != b.size) return false
  for (i in a.indices) {
    val x = a[i]
    val y = b[i]
    if (x::class != y::class) return false
    if (x.optic != y.optic) return false
    if (x is Stage.Overlay && y is Stage.Overlay && x.blendMode != y.blendMode) return false
  }
  return true
}

/**
 * Element that reconciles a [MirageNode]. Equatable on [clock], [enabled], the plan's ordered stage
 * optics + blend modes, **and** the per-stage params-block identities. The blocks are included (by
 * reference, like Compose's own `clickable`/`graphicsLayer` treat their lambda parameters) so that a
 * recomposition which re-creates the blocks — e.g. to feed a freshly measured lens center or an
 * animated uniform — is *not* equal to the previous element, and [update] runs to adopt the new
 * blocks. Excluding them would freeze the node on whatever block it first captured. [update] takes a
 * cheap path when only the blocks changed, so this does not cause per-recomposition layer churn. The
 * stage list is captured by running the plan once here.
 */
@OptIn(ExperimentalMirage::class)
internal class MirageElement(
  private val clock: MirageClock,
  private val enabled: Boolean,
  private val plan: MirageScope.() -> Unit,
) : ModifierNodeElement<MirageNode>() {

  /** Build the stage list eagerly so create()/equals share one evaluation of the plan. */
  private val stages: List<Stage> = MiragePlanBuilder().apply(plan).stages

  override fun create(): MirageNode = MirageNode(clock, enabled, stages)

  override fun update(node: MirageNode) {
    node.update(clock, enabled, stages)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "mirage"
    properties["clock"] = clock
    properties["enabled"] = enabled
    properties["stages"] = stages.map { it.optic.name }
  }

  /**
   * Equal when the same plan would produce the same programs *and* run the same per-draw blocks: same
   * clock, enabled, the same ordered (optic, kind, blendMode) tuple, and the same params-block
   * identities. Blocks are compared by reference (`===`); a recomposition re-creates them, so this is
   * unequal on recomposition and [update] runs. The mint-once params instances are per-node identity,
   * so they are left out. See [sameStructure] for the structure-only half [update] reuses.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MirageElement) return false
    if (clock != other.clock || enabled != other.enabled) return false
    if (!sameStructure(stages, other.stages)) return false
    for (i in stages.indices) {
      if (stages[i].paramsBlock !== other.stages[i].paramsBlock) return false
    }
    return true
  }

  override fun hashCode(): Int {
    var result = clock.hashCode()
    result = 31 * result + enabled.hashCode()
    for (stage in stages) {
      result = 31 * result + stage.optic.hashCode()
      if (stage is Stage.Overlay) result = 31 * result + stage.blendMode.hashCode()
      result = 31 * result + (stage.paramsBlock?.hashCode() ?: 0)
    }
    return result
  }
}

/**
 * Resets each handle on [params] back to the declared default from its schema entry, so an unset
 * uniform this draw re-uses its default rather than a stale prior-draw value.
 */
@OptIn(ExperimentalMirage::class)
private fun resetToDefaults(params: MirageParams, schema: UniformSchema) {
  for (handle in params.handles) {
    val default = schema.entries[handle.slot].default
    when (handle) {
      is UFloat -> handle.value = default as Float
      is UOffset -> handle.value = default as Offset
      is USize -> handle.value = default as Size
      is UInt1 -> handle.value = default as Int
      is UVec3 -> handle.value = (default as FloatArray).copyOf()
      is UVec4 -> handle.value = (default as FloatArray).copyOf()
      is UFloatArray -> handle.value = (default as FloatArray).copyOf()
      is UColor -> handle.value = default as Color
      is UTexture -> {
        @Suppress("UNCHECKED_CAST")
        handle.value = default as ImageBitmap?
      }
    }
  }
}
