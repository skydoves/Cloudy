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

import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.skydoves.cloudy.ExperimentalMirage

/**
 * The mirage effect: resolve each applicable filter's program via [MirageProgramCache], chain the
 * filters through [MirageFilterChain] as content-bound render effects, then composite the overlays via
 * a [ShaderBrush][androidx.compose.ui.graphics.ShaderBrush] under each overlay's blend mode.
 *
 * This is the draw machinery moved verbatim from the former MirageNode / MirageBackdropNode: identical
 * for both stage-0 sources (self content vs. backdrop region), which is why the source is passed in as
 * `recordSource` and the effect itself is source-agnostic.
 *
 * ## API < 33 / unsupported platform
 * A stage whose program cannot be built ([MirageProgramCache.obtain] returns `null`, e.g. Android below
 * API 33) is skipped individually: the content still flows through the remaining stages. On API < 33
 * every stage returns `null`, so [MirageFilterChain] draws the raw `recordSource` (pass-through).
 */
@OptIn(ExperimentalMirage::class)
internal object MirageEffect : Effect {

  override fun ContentDrawScope.draw(node: EffectNode, recordSource: DrawScope.() -> Unit) {
    val width = size.width
    val height = size.height
    val dialect = currentDialect()
    val density = node.density()
    val time = node.resolveTime()

    val filters = node.stages.filterIsInstance<Stage.ProgramFilter>()
    val overlays = node.stages.filterIsInstance<Stage.Overlay>()

    // Filters: record the running content (stage 0 = recordSource) into a per-stage layer, bind that
    // stage's program, apply it as the layer's content-bound render effect, then draw the layer —
    // feeding the next stage. A stage whose program is unavailable (API < 33) is skipped. The layer
    // chaining lives in [MirageFilterChain]; here we resolve programs and supply the stage-0 source.
    val applicable = filters.mapNotNull { stage ->
      val cached = MirageProgramCache.obtain(stage.optic, dialect) ?: return@mapNotNull null
      // A self-lit content node draws its filters in place synchronously. A Blit stage (the async GLES
      // path) is backdrop-only — it has no self-lit capture path (no contentVersion key), so skip it here
      // rather than pass it to the chain's Blit branch, which errors. GLES self-lit is thus unsupported;
      // the plan's MirageFallback (if any) shows via planRenders. On the backdrop path a Blit filter is
      // routed to the async GLES runner before this draw is reached, so it never arrives here.
      if (!rendersInPlace(cached)) return@mapNotNull null
      stage to cached
    }

    with(node.chain) {
      draw(
        context = node.graphicsContext(),
        applicable = applicable,
        bind = { stage, cached ->
          bindUniforms(cached, stage.params, stage.paramsBlock, width, height, density, time)
        },
        recordSource = recordSource,
      )
    }

    // Overlays: the (already filtered) content was drawn above; here just composite each generator over
    // it via a ShaderBrush under the stage's blend mode.
    for (stage in overlays) {
      val cached = MirageProgramCache.obtain(stage.optic, dialect) ?: continue
      bindUniforms(cached, stage.params, stage.paramsBlock, width, height, density, time)
      drawRect(brush = cached.backend.asShaderBrush(), blendMode = stage.blendMode)
    }
  }
}
