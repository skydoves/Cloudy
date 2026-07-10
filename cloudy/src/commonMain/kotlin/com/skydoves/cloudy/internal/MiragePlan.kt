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

import androidx.compose.ui.graphics.BlendMode
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.FilterOptic
import com.skydoves.cloudy.GenerateOptic
import com.skydoves.cloudy.MirageParams
import com.skydoves.cloudy.MirageScope
import com.skydoves.cloudy.Optic

/** The shading language the current platform runs (Android = AGSL, every skiko target = SKSL). */
internal expect fun currentDialect(): Dialect

/** mirageTime wraps here so a long session never grows the argument enough to decay float32 sin(). */
internal const val TIME_WRAP_SECONDS = 3600f

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
 * True when two stage lists describe the same plan structure: same length and, in order, the same
 * stage kind, optic, and (for overlays) blend mode. The per-draw params blocks are deliberately not
 * compared — this is the "would the same programs and layer stack be built?" test that decides whether
 * [WeatherNode.update] can take the cheap blocks-only path. Kept beside [WeatherElement.equals], which
 * runs the identical comparison to decide element equality.
 */
@OptIn(ExperimentalMirage::class)
internal fun sameStructure(a: List<Stage>, b: List<Stage>): Boolean {
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
