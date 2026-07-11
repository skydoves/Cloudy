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
import com.skydoves.cloudy.CloudyProgressive
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.FilterShader
import com.skydoves.cloudy.GeneratorShader
import com.skydoves.cloudy.MirageParams
import com.skydoves.cloudy.MirageScope
import com.skydoves.cloudy.MirageShader

/** The shading language the current platform runs (Android = AGSL, every skiko target = SKSL). */
internal expect fun currentDialect(): Dialect

/** mirageTime wraps here so a long session never grows the argument enough to decay float32 sin(). */
internal const val TIME_WRAP_SECONDS = 3600f

/**
 * One declared stage of a plan. Sealed so the draw loop branches exhaustively over the application
 * shapes: a program stage ([ProgramFilter]/[Overlay]) carried by [MirageEffect], or a
 * platform-blur stage ([PlatformFilter]) carried by BlurStrategy.
 */
internal sealed class Stage {

  /**
   * A content-transforming shader filter — applied as a content-bound render effect. [params] is the
   * single params instance the node mints once and reuses every draw (no per-draw allocation);
   * [paramsBlock] is the caller's per-draw uniform block, re-run each draw against [params].
   */
  class ProgramFilter(
    val shader: FilterShader<*>,
    val params: MirageParams,
    val paramsBlock: (MirageParams.() -> Unit)?,
  ) : Stage()

  /** A content-free overlay generator — drawn over the content with [blendMode]. */
  class Overlay(
    val shader: GeneratorShader<*>,
    val params: MirageParams,
    val paramsBlock: (MirageParams.() -> Unit)?,
    val blendMode: BlendMode,
  ) : Stage()

  /**
   * The platform blur stage. [radius]/[progressive] are draw-time cache keys, **not** structural
   * (see [sameStructure]): every blur code path treats a radius change as a draw-time re-key of the
   * cached effect, so a slider animation re-keys the effect without re-warming layers.
   */
  class PlatformFilter(val radius: Int, val progressive: CloudyProgressive) : Stage()
}

/**
 * Builds the immutable stage list for a plan by running the caller's `plan` block once. Each
 * `filter`/`overlay` call mints the shader's params instance (via its `paramsFactory`) and captures
 * the per-draw block; the built [stages] are what the node draws through.
 */
@OptIn(ExperimentalMirage::class)
internal class MiragePlanBuilder : MirageScope {

  val stages: MutableList<Stage> = mutableListOf()

  @Suppress("UNCHECKED_CAST")
  override fun <P : MirageParams> filter(shader: FilterShader<P>, params: (P.() -> Unit)?) {
    // paramsFactory mints a P; the block is P.() -> Unit. Both are erased to MirageParams for storage
    // and re-cast at the (type-safe by construction) call site — the instance came from this shader.
    stages +=
      Stage.ProgramFilter(shader, shader.paramsFactory(), params as (MirageParams.() -> Unit)?)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <P : MirageParams> overlay(
    shader: GeneratorShader<P>,
    blendMode: BlendMode,
    params: (P.() -> Unit)?,
  ) {
    stages += Stage.Overlay(
      shader,
      shader.paramsFactory(),
      params as (MirageParams.() -> Unit)?,
      blendMode,
    )
  }
}

/**
 * Builds the [EffectElement] for a `Modifier.mirage` plan: runs [plan] once to fix the stages and
 * wires the stateless [MirageEffect] with an empty [PostProcess] (mirage clips/tints nothing — it
 * grades in-shader). The effect key is [Unit] because [MirageEffect] is a stateless object.
 */
@OptIn(ExperimentalMirage::class)
internal fun mirageElement(
  sky: com.skydoves.cloudy.Sky?,
  clock: com.skydoves.cloudy.MirageClock,
  enabled: Boolean,
  plan: MirageScope.() -> Unit,
): EffectElement {
  val stages = MiragePlanBuilder().apply(plan).stages
  return EffectElement(
    effect = MirageEffect,
    sky = sky,
    clock = clock,
    enabled = enabled,
    stages = stages,
    postProcess = PostProcess(
      androidx.compose.ui.graphics.RectangleShape,
      androidx.compose.ui.graphics.Color.Transparent,
      null,
    ),
    effectKey = Unit,
    onStateChanged = null,
  )
}

/**
 * True when two stage lists describe the same plan structure: same length and, in order, the same
 * stage kind, shader, and (for overlays) blend mode. The per-draw params blocks are deliberately not
 * compared — this is the "would the same programs and layer stack be built?" test that decides whether
 * [EffectNode.update] can take the cheap blocks-only path. Kept beside [EffectElement.equals], which
 * runs the identical comparison to decide element equality.
 *
 * [Stage.PlatformFilter] matches by kind only: its radius/progressive are draw-time cache keys (a
 * blur re-keys the cached effect at draw time without a re-warm), so they must not force a structural
 * update or a slider animation would tear down and rebuild the blur layer on every frame.
 */
@OptIn(ExperimentalMirage::class)
internal fun sameStructure(a: List<Stage>, b: List<Stage>): Boolean {
  if (a.size != b.size) return false
  for (i in a.indices) {
    val x = a[i]
    val y = b[i]
    if (x::class != y::class) return false
    when {
      x is Stage.ProgramFilter && y is Stage.ProgramFilter ->
        if (x.shader != y.shader) return false

      x is Stage.Overlay && y is Stage.Overlay ->
        if (x.shader != y.shader || x.blendMode != y.blendMode) return false

      // PlatformFilter: kind match suffices — radius/progressive are draw-time keys.
    }
  }
  return true
}
