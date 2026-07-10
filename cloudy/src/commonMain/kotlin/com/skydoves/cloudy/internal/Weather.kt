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
package com.skydoves.cloudy.internal

import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.skydoves.cloudy.CloudyState

/**
 * The effect that a [WeatherNode] applies to its stage-0 pixels. [WeatherNode] owns the plan, clock,
 * layer pool, positioning, and post-processing; a [Weather] owns *the draw* — how the resolved source
 * pixels become the final look. [MirageWeather] runs the shader filter/overlay pipeline; BlurWeather
 * runs the platform blur (GPU render effect, or the CPU legacy / scrim fallback below API 31).
 *
 * It is handed the [WeatherNode] directly rather than through a narrower "atmosphere" seam because the
 * draw needs the node's `stages` / `chain` / resolved time, and Compose's `invalidateDraw()` /
 * `requireGraphicsContext()` are extension functions on the node — not reachable through an interface.
 */
internal interface Weather {

  /**
   * Draws the effect. [recordSource] records the stage-0 input (the node's own content for
   * [Skylight.SelfLit], or the offset backdrop region for [Skylight.Backdrop]); the [Weather] runs the
   * plan against it.
   */
  fun ContentDrawScope.draw(node: WeatherNode, recordSource: DrawScope.() -> Unit)

  /** Releases weather-owned caches (blur layer/effect, legacy bitmap machines). Called on detach and structural update. */
  fun detach(node: WeatherNode) {}

  /**
   * Overcast pre-check: when true, the node draws [drawScrim] then its own content behind, instead of
   * running [draw]. Only the API < 31 scrim fallback returns true.
   */
  fun shouldDrawContentBehind(node: WeatherNode): Boolean = false

  /** Draws the Overcast scrim over the [recordSource] region. Only called when [shouldDrawContentBehind] is true. */
  fun ContentDrawScope.drawScrim(node: WeatherNode, recordSource: DrawScope.() -> Unit) {}

  /** The [CloudyState] this weather is in after the last draw, relayed by the node (deduped). Null = no state to report. */
  fun currentState(node: WeatherNode): CloudyState? = null
}
