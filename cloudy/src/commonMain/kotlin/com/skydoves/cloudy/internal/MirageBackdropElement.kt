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
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.MirageClock
import com.skydoves.cloudy.MirageScope
import com.skydoves.cloudy.Sky

/**
 * Element that reconciles a [MirageBackdropNode]. Equatable on [sky], [clock], [enabled], the plan's
 * ordered stage optics + blend modes, **and** the per-stage params-block identities — the same key as
 * [MirageElement] plus [sky]. [sky] is identity-stable across recompositions (`rememberSky` holds it in
 * a `remember`), so including it keeps the cheap blocks-only [MirageBackdropNode.update] path. The
 * blocks are compared by reference so a recomposition that re-creates them (e.g. an animated uniform)
 * is unequal and [update] runs to adopt them.
 */
@OptIn(ExperimentalMirage::class)
internal class MirageBackdropElement(
  private val sky: Sky,
  private val clock: MirageClock,
  private val enabled: Boolean,
  private val plan: MirageScope.() -> Unit,
) : ModifierNodeElement<MirageBackdropNode>() {

  /** Build the stage list eagerly so create()/equals share one evaluation of the plan. */
  private val stages: List<Stage> = MiragePlanBuilder().apply(plan).stages

  override fun create(): MirageBackdropNode = MirageBackdropNode(sky, clock, enabled, stages)

  override fun update(node: MirageBackdropNode) {
    node.update(sky, clock, enabled, stages)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "mirage"
    properties["sky"] = sky
    properties["clock"] = clock
    properties["enabled"] = enabled
    properties["stages"] = stages.map { it.optic.name }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MirageBackdropElement) return false
    if (sky != other.sky) return false
    if (clock != other.clock || enabled != other.enabled) return false
    if (!sameStructure(stages, other.stages)) return false
    for (i in stages.indices) {
      if (stages[i].paramsBlock !== other.stages[i].paramsBlock) return false
    }
    return true
  }

  override fun hashCode(): Int {
    var result = sky.hashCode()
    result = 31 * result + clock.hashCode()
    result = 31 * result + enabled.hashCode()
    for (stage in stages) {
      result = 31 * result + stage.optic.hashCode()
      if (stage is Stage.Overlay) result = 31 * result + stage.blendMode.hashCode()
      result = 31 * result + (stage.paramsBlock?.hashCode() ?: 0)
    }
    return result
  }
}
