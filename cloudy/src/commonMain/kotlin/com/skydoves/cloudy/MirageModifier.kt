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

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import com.skydoves.cloudy.internal.MiragePipelineBuilder
import com.skydoves.cloudy.internal.currentDialect
import com.skydoves.cloudy.internal.mirageElement
import com.skydoves.cloudy.internal.planRenders

/**
 * Time-driving policy for the standard `mirageTime` uniform. Pipeline-level — the pipeline (not each
 * stage) owns a single frame loop, so all time-driven stages advance off one clock.
 */
@ExperimentalMirage
public sealed interface MirageClock {
  /**
   * Default. Spins a frame loop only if some stage's compiled kernel references `mirageTime`
   * (statically detected by the compiler via [CompiledProgram.usesTime]
   * [com.skydoves.cloudy.internal.CompiledProgram]). The value fed to the shader is seconds since the
   * node attached, wrapped at 3600s so a long-running session never grows the argument large enough
   * to decay float32 `sin()` precision.
   *
   * RequiresOptIn does not propagate to nested members, so each carries the marker explicitly to stay
   * out of the stable ABI dumps.
   */
  @ExperimentalMirage
  public data object Auto : MirageClock

  /** Freezes time at whatever value the clock last held; no frame loop runs. */
  @ExperimentalMirage
  public data object Paused : MirageClock

  /**
   * Supplies a constant `mirageTime` and runs no frame loop, so the pipeline is fully deterministic —
   * the intended clock for screenshot tests of animated shaders.
   */
  @ExperimentalMirage
  public data class Fixed(val seconds: Float) : MirageClock
}

/**
 * Stage declaration scope for [Modifier.mirage]. The block runs once (when the node attaches) to fix
 * the ordered stage list; each stage's `params` block re-runs every draw.
 *
 * This scope *declares* the shaders of a pipeline; it does not write uniforms — a stage's uniforms are
 * bound each draw from its `params` block against the shader's [MirageParams].
 */
@ExperimentalMirage
public interface MirageScope {
  /**
   * Declares a content-transforming stage (a [ColorizeShader] / [CompositeShader] / raw filter). The
   * content pixels feed the shader and its output replaces them. Declared filters apply in the order
   * they are added: `content -> f1 -> f2 -> … -> screen`.
   *
   * @param shader the filter shader to run.
   * @param params optional per-draw uniform block, run against a params instance the node mints once
   *   and reuses (no per-draw allocation). `null` leaves every uniform at its declared default.
   */
  public fun <P : MirageParams> filter(shader: FilterShader<P>, params: (P.() -> Unit)? = null)

  /**
   * Declares an overlay stage (a [GeneratorShader]) drawn over the content without sampling it. Overlay
   * output is composited over all filter results, in declared order, under [blendMode].
   *
   * @param shader the generator shader to run.
   * @param blendMode how the overlay composites over the content. Default: [BlendMode.SrcOver].
   * @param params optional per-draw uniform block; see [filter].
   */
  public fun <P : MirageParams> overlay(
    shader: GeneratorShader<P>,
    blendMode: BlendMode = BlendMode.SrcOver,
    params: (P.() -> Unit)? = null,
  )
}

/**
 * What to draw when a mirage plan cannot render on the current device — e.g. a lens optic on Android
 * below API 33, where no runtime shader is available. The library never invents a degraded look for a
 * lens optic (a tint would be a different effect, not the one authored); instead the caller decides.
 *
 * A plan is considered unrenderable only when **no** stage produces any output on this platform. A
 * plan whose Colorize stage is reproduced by the color-grade path (Duotone below API 33) renders, so
 * its fallback is ignored.
 */
@ExperimentalMirage
public sealed interface MirageFallback {
  /** Default: draw nothing extra. The plan's content passes through unmodified (the historic behavior). */
  @ExperimentalMirage
  public data object None : MirageFallback

  /**
   * Draw [modifier] in place of the unrenderable plan. Applied to the same node — use it to supply a
   * still image, a solid fill, or any other stand-in for the effect the device cannot run.
   */
  @ExperimentalMirage
  public data class Content(val modifier: Modifier) : MirageFallback
}

/**
 * Applies a mirage effect [plan] to the content it modifies.
 *
 * Non-composable and `Modifier.Node`-based: [plan] is evaluated **once** to fix the ordered stage
 * list (which, together with [clock] and [enabled], forms the node's equality key), while each stage's
 * `params` block is re-evaluated **per draw**. Reading snapshot state inside a `params` block
 * therefore invalidates only the draw — recomposition is never required. Hoisting the whole modifier
 * into a top-level `val` / `remember` is unnecessary for correctness (the node reconciles on the key),
 * though still cheap.
 *
 * Compiled GPU programs are shared through a process-wide cache keyed on the generated shader source,
 * so two pipelines that declare the same shader — or the same pipeline re-attached after [enabled]
 * toggles — reuse one program instead of recompiling.
 *
 * ## Stage order
 * Filters chain in declared order (`content -> f1 -> f2 -> …`); overlays are then composited over the
 * filtered result in declared order. Stages are not fused: each is a separate program applied in
 * sequence (see the node for why).
 *
 * @param clock time-driving policy for the standard `mirageTime` uniform. Default: [MirageClock.Auto].
 * @param enabled when `false`, the whole pipeline is bypassed and the content passes through unmodified.
 *   Compiled programs remain cached process-wide, so re-enabling incurs no recompile.
 * @param fallback what to draw when the plan cannot render on this device (see [MirageFallback]).
 *   Default [MirageFallback.None] — content passes through, matching the historic behavior.
 * @param plan the stage declaration block; see [MirageScope].
 * @return a [Modifier] that applies the pipeline.
 */
@ExperimentalMirage
public expect fun Modifier.mirage(
  clock: MirageClock = MirageClock.Auto,
  enabled: Boolean = true,
  fallback: MirageFallback = MirageFallback.None,
  plan: MirageScope.() -> Unit,
): Modifier

/**
 * Shared body of the content [Modifier.mirage] actuals. Kept in commonMain so the platform actuals are
 * one-liners: they differ only because [Modifier.mirage] is `expect` (historic), not in behavior.
 *
 * When [fallback] is [MirageFallback.Content] **and** the plan renders nothing on this device (every
 * stage's program is unavailable — e.g. a lens optic below API 33), the mirage node is skipped and the
 * fallback modifier is applied in its place. Otherwise the normal mirage node attaches; a
 * [MirageFallback.None] never changes the chain.
 */
@ExperimentalMirage
internal fun Modifier.mirageOrFallback(
  clock: MirageClock,
  enabled: Boolean,
  fallback: MirageFallback,
  plan: MirageScope.() -> Unit,
): Modifier {
  if (fallback is MirageFallback.Content && enabled) {
    val stages = MiragePipelineBuilder().apply(plan).stages
    if (!planRenders(stages, currentDialect())) {
      return this.then(fallback.modifier)
    }
  }
  return this.then(mirageElement(sky = null, clock, enabled, plan))
}

/**
 * Applies a mirage effect [plan] to the [sky] **backdrop** behind the modified node, instead of to the
 * node's own content. Use it to grade, tint, or overlay the captured background — e.g.
 * `Modifier.mirage(sky = sky) { filter(MirageShaders.Duotone) }` renders a duotone "material" from the
 * backdrop, the mirage counterpart of `Modifier.cloudy(sky = sky)`'s blur.
 *
 * The node samples the region of [sky]'s captured backdrop directly behind it (tracked via its layout
 * position, like [Modifier.cloudy]), feeds those pixels through the pipeline's filter stages, and draws the
 * result. The modified node's own content is drawn on top, so this is typically applied to an otherwise
 * empty surface.
 *
 * Backdrop capture requires a `Modifier.sky(sky)` ancestor to record the background. While that
 * backdrop scrolls, the effect refreshes automatically. Single commonMain implementation: the same
 * node runs on Android (AGSL) and every skiko target (SKSL). Below API 33 (no `RuntimeShader`) the
 * filters are skipped and the raw backdrop region is drawn, mirroring the radius-0 backdrop blur.
 *
 * This is a distinct overload from the content [mirage] above: `mirage { … }` filters the content,
 * `mirage(sky = …) { … }` filters the backdrop. Otherwise it behaves identically — [plan] is evaluated
 * once to fix the stages, each stage's `params` block is re-evaluated per draw (no recomposition), and
 * programs are shared through the same process-wide cache.
 *
 * @param sky the backdrop state holder captured by a `Modifier.sky` ancestor; the source of the pixels
 *   the pipeline grades. Identity-stable via `rememberSky`, so it participates in the node's key cheaply.
 * @param clock time-driving policy for the standard `mirageTime` uniform. Default: [MirageClock.Auto].
 * @param enabled when `false`, the pipeline is bypassed and the node's content passes through unmodified.
 * @param plan the stage declaration block; see [MirageScope].
 * @return a [Modifier] that applies the pipeline to the [sky] backdrop.
 */
@ExperimentalMirage
public fun Modifier.mirage(
  sky: Sky,
  clock: MirageClock = MirageClock.Auto,
  enabled: Boolean = true,
  plan: MirageScope.() -> Unit,
): Modifier = this.then(mirageElement(sky, clock, enabled, plan))
