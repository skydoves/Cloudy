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

import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import com.skydoves.cloudy.CloudyState
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.MirageClock
import com.skydoves.cloudy.Sky

/**
 * Element that reconciles an [EffectNode]. Equatable on the stage-0 [sky] (the backdrop's [Sky], or
 * null for a content source), [clock], [enabled], the plan's ordered stage structure, the
 * per-program-stage params-block identities, the [postProcess] (by value), and the [effectKey].
 *
 * ## effectKey
 * The [effect] instance is not compared directly (a fresh `MirageEffect`/`BlurStrategy` each
 * recomposition would make every element unequal). Instead an [effectKey] value captures whatever
 * effect-construction state matters — `Unit` for the stateless [MirageEffect] object, or the blur's
 * `(cpuBlurEnabled, scrimTint)` — so a real config change makes the element unequal *and* recreates
 * the node's effect via [EffectNode.update]'s structural path.
 *
 * ## params-block identity
 * Program stages' params blocks are compared by reference (`===`), like Compose's own
 * `clickable`/`graphicsLayer` treat their lambda parameters, so a recomposition that re-creates them
 * (e.g. to feed a freshly measured lens center) is *not* equal and [update] runs to adopt them.
 * [update] takes a cheap path when only the blocks changed. Blur's [Stage.PlatformFilter] has no
 * params block, so it contributes nothing to this loop.
 */
@OptIn(ExperimentalMirage::class)
internal class EffectElement(
  private val effect: Effect,
  private val sky: Sky?,
  private val clock: MirageClock,
  private val enabled: Boolean,
  private val stages: List<Stage>,
  private val postProcess: PostProcess,
  private val effectKey: Any?,
  private val onStateChanged: ((CloudyState) -> Unit)?,
) : ModifierNodeElement<EffectNode>() {

  override fun create(): EffectNode =
    EffectNode(effect, sky, clock, enabled, stages, postProcess, effectKey, onStateChanged)

  override fun update(node: EffectNode) {
    node.update(effect, sky, clock, enabled, stages, postProcess, effectKey, onStateChanged)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "cloudy"
    if (sky != null) properties["sky"] = sky
    properties["clock"] = clock
    properties["enabled"] = enabled
    properties["stages"] = stages.map { it::class.simpleName }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EffectElement) return false
    if (sky != other.sky) return false
    if (clock != other.clock || enabled != other.enabled) return false
    if (effectKey != other.effectKey) return false
    if (postProcess != other.postProcess) return false
    if (!sameStructure(stages, other.stages)) return false
    for (i in stages.indices) {
      if (paramsBlockOf(stages[i]) !== paramsBlockOf(other.stages[i])) return false
      // Blur radius/progressive are draw-time keys (excluded from sameStructure so an update stays on
      // the cheap path), but a change must still make the element unequal so update() runs at all and
      // the node adopts the new stages — mirroring the old cloudy element's data-class radius equality.
      if (drawKeyOf(stages[i]) != drawKeyOf(other.stages[i])) return false
    }
    return true
  }

  override fun hashCode(): Int {
    var result = sky?.hashCode() ?: 0
    result = 31 * result + clock.hashCode()
    result = 31 * result + enabled.hashCode()
    result = 31 * result + (effectKey?.hashCode() ?: 0)
    result = 31 * result + postProcess.hashCode()
    for (stage in stages) {
      result = 31 * result + stage::class.hashCode()
      when (stage) {
        is Stage.ProgramFilter -> result = 31 * result + stage.shader.hashCode()

        is Stage.Overlay -> {
          result = 31 * result + stage.shader.hashCode()
          result = 31 * result + stage.blendMode.hashCode()
        }

        is Stage.PlatformFilter -> Unit
      }
      result = 31 * result + (paramsBlockOf(stage)?.hashCode() ?: 0)
      result = 31 * result + (drawKeyOf(stage)?.hashCode() ?: 0)
    }
    return result
  }
}

private fun paramsBlockOf(stage: Stage): Any? = when (stage) {
  is Stage.ProgramFilter -> stage.paramsBlock
  is Stage.Overlay -> stage.paramsBlock
  is Stage.PlatformFilter -> null
}

/** The draw-time key (radius/progressive) for a blur stage; null for program stages (they have none). */
private fun drawKeyOf(stage: Stage): Any? = when (stage) {
  is Stage.PlatformFilter -> stage.radius to stage.progressive
  else -> null
}
