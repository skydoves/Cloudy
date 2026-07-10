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
 * Element that reconciles a [WeatherNode]. Equatable on the [Skylight] source (the [Sky] for a
 * backdrop, nothing for self-lit), [clock], [enabled], the plan's ordered stage optics + blend modes,
 * **and** the per-stage params-block identities. The blocks are included (by reference, like Compose's
 * own `clickable`/`graphicsLayer` treat their lambda parameters) so a recomposition that re-creates
 * them — e.g. to feed a freshly measured lens center or an animated uniform — is *not* equal to the
 * previous element, and [update] runs to adopt the new blocks. Excluding them would freeze the node on
 * whatever block it first captured. [update] takes a cheap path when only the blocks changed, so this
 * does not cause per-recomposition layer churn. The [Sky] (when present) is identity-stable across
 * recompositions (`rememberSky` holds it in a `remember`), so including it keeps that cheap path. The
 * stage list is captured by running the plan once here.
 */
@OptIn(ExperimentalMirage::class)
internal class WeatherElement(
  private val weather: Weather,
  private val skylight: Skylight,
  private val clock: MirageClock,
  private val enabled: Boolean,
  private val plan: MirageScope.() -> Unit,
) : ModifierNodeElement<WeatherNode>() {

  /** Build the stage list eagerly so create()/equals share one evaluation of the plan. */
  private val stages: List<Stage> = MiragePlanBuilder().apply(plan).stages

  /** The backdrop's Sky when this is a backdrop element, else null — the equality/inspector key. */
  private val sky: Sky? = (skylight as? Skylight.Backdrop)?.sky

  override fun create(): WeatherNode = WeatherNode(weather, skylight, clock, enabled, stages)

  override fun update(node: WeatherNode) {
    node.update(skylight, clock, enabled, stages)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "mirage"
    if (sky != null) properties["sky"] = sky
    properties["clock"] = clock
    properties["enabled"] = enabled
    properties["stages"] = stages.map { it.optic.name }
  }

  /**
   * Equal when the same plan would produce the same programs *and* run the same per-draw blocks: same
   * source [sky] (a self-lit element carries `null` and equals another self-lit one), clock, enabled,
   * the same ordered (optic, kind, blendMode) tuple, and the same params-block identities. Blocks are
   * compared by reference (`===`); a recomposition re-creates them, so this is unequal on recomposition
   * and [update] runs. See [sameStructure] for the structure-only half [update] reuses.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WeatherElement) return false
    if (sky != other.sky) return false
    if (clock != other.clock || enabled != other.enabled) return false
    if (!sameStructure(stages, other.stages)) return false
    for (i in stages.indices) {
      if (stages[i].paramsBlock !== other.stages[i].paramsBlock) return false
    }
    return true
  }

  override fun hashCode(): Int {
    var result = sky?.hashCode() ?: 0
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
