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

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import com.skydoves.cloudy.ExperimentalMirage

/**
 * Owns and runs the layer-chaining draw for a mirage filter pipeline, decoupled from *what* stage 0
 * samples. A content-source [EffectNode] records its own content into stage 0; a backdrop one records the
 * Sky region instead — the chaining, per-stage render-effect binding, and layer pooling are identical,
 * so they are extracted here rather than duplicated (Pure Fabrication: the algorithm is neither source,
 * it is the shared stage-chain machinery).
 *
 * It holds no clock, no Sky, and no params ownership: the caller supplies the already-resolved
 * [Stage.ProgramFilter] → [CachedProgram] pairs, the per-stage uniform [bind], and the stage-0 source. This
 * keeps the chain lifecycle-free — its only state is the reusable layer pool, released on detach.
 *
 * ## Draw model (no fusion)
 * Stage 0 records the caller's `recordSource`; each later stage records the previous stage's layer.
 * After binding, every stage sets its layer's content-bound render effect so the program transforms
 * exactly the recorded pixels. The last stage's layer holds the fully chained result and is drawn.
 */
internal class MirageFilterChain {

  /**
   * Reusable filter layers, one per applicable stage, indexed by stage order. Rebuilt lazily on first
   * draw and released via [release]; never allocated inside the steady-state draw.
   */
  private var filterLayers: Array<GraphicsLayer?> = arrayOfNulls(0)

  /**
   * Draws [applicable] as a chained stack of content-bound render effects.
   *
   * When [applicable] is empty (no stage declared, or every stage unsupported — e.g. Android below
   * API 33 where [MirageProgramCache.obtain] returns `null`), [recordSource] is drawn straight to the
   * screen. This single contract is why both the self-content pass-through and the backdrop API < 33
   * "raw region" fallback need no extra branch: the caller's `recordSource` (own content, or the
   * offset backdrop region) is what shows.
   *
   * @param context the graphics context that owns the layer pool (`requireGraphicsContext()` from the
   *   calling node — passed in because the chain is a plain collaborator, not a `Modifier.Node`).
   * @param applicable already-resolved (`obtain != null`) filter stages in declared order.
   * @param bind writes a stage's per-draw uniforms into its program (called before the effect is set,
   *   so the effect captures the current uniforms). The caller keeps clock/params ownership.
   * @param recordSource records the stage-0 input — the self content for a node, or the offset Sky
   *   region for a backdrop node.
   */
  fun ContentDrawScope.draw(
    context: GraphicsContext,
    applicable: List<Pair<Stage.ProgramFilter, CachedProgram>>,
    bind: (Stage.ProgramFilter, CachedProgram) -> Unit,
    recordSource: DrawScope.() -> Unit,
  ) {
    if (applicable.isEmpty()) {
      // No filter ran (none declared, or all unsupported): draw the source as-is.
      recordSource()
      return
    }

    if (filterLayers.size != applicable.size) {
      // Layer count changed (a structural pipeline change). Release any stale layers before resizing so
      // the pool never leaks even if the node did not reset it first.
      release(context)
      filterLayers = arrayOfNulls(applicable.size)
    }

    for (index in applicable.indices) {
      val (stage, cached) = applicable[index]
      val layer = filterLayers[index]
        ?: context.createGraphicsLayer().also { filterLayers[index] = it }

      // Record the previous stage's output (or the caller's source for the first stage) into this
      // stage's layer, so the application below transforms exactly that.
      layer.record {
        if (index == 0) {
          recordSource()
        } else {
          drawLayer(filterLayers[index - 1]!!)
        }
      }

      bind(stage, cached)
      // Reset both per-draw layer applications first: the pool is reused across structural configs, so
      // a leftover effect/filter from a prior pipeline must not carry over onto this stage.
      layer.renderEffect = null
      layer.colorFilter = null
      when (val application = cached.backend.filterApplication()) {
        // API 33+ AGSL / every skiko target: a content-bound render effect.
        is FilterApplication.Effect -> layer.renderEffect = application.renderEffect

        // API 23-28 ColorGrade: an affine color filter applied in the layer paint (no RenderEffect).
        is FilterApplication.ColorFilter -> layer.colorFilter = application.colorFilter

        // Blit (API 29-32 GLES) never reaches the synchronous chain: the backdrop node routes it to the
        // async GLES runner and self-lit nodes filter it out (rendersInPlace). A Blit here is a wiring
        // bug — it would silently pass through, which is the self-lit no-op gap this guards against.
        is FilterApplication.Blit ->
          error("Blit filter reached the synchronous chain; it must run via MirageGlesBackdrop")
      }
    }

    // The last applicable filter's layer holds the fully chained result.
    drawLayer(filterLayers[applicable.lastIndex]!!)
  }

  /** Releases the pooled layers back to [context]; call from the owning node's `onDetach`. */
  fun release(context: GraphicsContext) {
    if (filterLayers.isEmpty()) return
    for (i in filterLayers.indices) {
      filterLayers[i]?.let { context.releaseGraphicsLayer(it) }
      filterLayers[i] = null
    }
    filterLayers = arrayOfNulls(0)
  }
}
