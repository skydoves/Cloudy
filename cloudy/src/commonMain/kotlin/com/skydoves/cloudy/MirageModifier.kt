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

/**
 * Time-driving policy for the standard `mirageTime` uniform. Plan-level — the plan (not each stage)
 * owns a single frame loop, so all time-driven stages advance off one clock.
 */
@ExperimentalMirage
public sealed interface MirageClock {
  /**
   * Default. Spins a frame loop only if some stage's compiled kernel references `mirageTime`
   * (statically detected by the compiler via [CompiledProgram.usesTime]
   * [com.skydoves.cloudy.internal.CompiledProgram]). The value fed to the shader is seconds since the
   * node attached, wrapped at 3600s so a long-running session never grows the argument large enough
   * to decay float32 `sin()` precision.
   */
  // RequiresOptIn does not propagate to nested members, so each carries the marker explicitly to stay
  // out of the stable ABI dumps.
  @ExperimentalMirage
  public data object Auto : MirageClock

  /** Freezes time at whatever value the clock last held; no frame loop runs. */
  @ExperimentalMirage
  public data object Paused : MirageClock

  /**
   * Supplies a constant `mirageTime` and runs no frame loop, so the plan is fully deterministic —
   * the intended clock for screenshot tests of animated optics.
   */
  @ExperimentalMirage
  public data class Fixed(val seconds: Float) : MirageClock
}

/**
 * Stage declaration scope for [Modifier.mirage]. The block runs once (when the node attaches) to fix
 * the ordered stage list; each stage's `params` block re-runs every draw.
 *
 * This scope *declares* the optics of a plan; it does not write uniforms — a stage's uniforms are
 * bound each draw from its `params` block against the optic's [MirageParams].
 */
@ExperimentalMirage
public interface MirageScope {
  /**
   * Declares a content-transforming stage (a [ColorizeOptic] / [CompositeOptic] / raw filter). The
   * content pixels feed the shader and its output replaces them. Declared filters apply in the order
   * they are added: `content -> f1 -> f2 -> … -> screen`.
   *
   * @param optic the filter optic to run.
   * @param params optional per-draw uniform block, run against a params instance the node mints once
   *   and reuses (no per-draw allocation). `null` leaves every uniform at its declared default.
   */
  public fun <P : MirageParams> filter(optic: FilterOptic<P>, params: (P.() -> Unit)? = null)

  /**
   * Declares an overlay stage (a [GenerateOptic]) drawn over the content without sampling it. Overlay
   * output is composited over all filter results, in declared order, under [blendMode].
   *
   * @param optic the generator optic to run.
   * @param blendMode how the overlay composites over the content. Default: [BlendMode.SrcOver].
   * @param params optional per-draw uniform block; see [filter].
   */
  public fun <P : MirageParams> overlay(
    optic: GenerateOptic<P>,
    blendMode: BlendMode = BlendMode.SrcOver,
    params: (P.() -> Unit)? = null,
  )
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
 * so two plans that declare the same optic — or the same plan re-attached after [enabled] toggles —
 * reuse one program instead of recompiling.
 *
 * ## Stage order
 * Filters chain in declared order (`content -> f1 -> f2 -> …`); overlays are then composited over the
 * filtered result in declared order. There is no cross-stage fusion in this milestone — each stage is
 * a separate program applied in sequence (see the node for why).
 *
 * @param clock time-driving policy for the standard `mirageTime` uniform. Default: [MirageClock.Auto].
 * @param enabled when `false`, the whole plan is bypassed and the content passes through unmodified.
 *   Compiled programs remain cached process-wide, so re-enabling incurs no recompile.
 * @param plan the stage declaration block; see [MirageScope].
 * @return a [Modifier] that applies the plan.
 */
@ExperimentalMirage
public expect fun Modifier.mirage(
  clock: MirageClock = MirageClock.Auto,
  enabled: Boolean = true,
  plan: MirageScope.() -> Unit,
): Modifier
