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

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import com.skydoves.cloudy.CloudyState
import com.skydoves.cloudy.ExperimentalMirage

/**
 * Skiko blur strategy (iOS, macOS, Desktop, Wasm): always the Gpu path — Skia's [BlurEffect] is
 * always available, so there is no Cpu/Scrim fallback. Ports the former skiko backdrop node's
 * `drawWithBlurEffect`: a single reusable [GraphicsLayer] plus a [BlurEffect] cached on the radius.
 * Radius/progressive are read from the node's [Stage.PlatformFilter] at draw time so a radius change
 * re-keys the effect on the cheap update path. PostProcess (clip/tint/highlight) is applied by the
 * node around this draw; a radius of 0 is a passthrough of the source region.
 */
internal class BlurStrategy : Effect {

  private var blurLayer: GraphicsLayer? = null
  private var cachedBlurEffect: BlurEffect? = null
  private var cachedBlurRadius: Float = -1f
  private var lastState: CloudyState = CloudyState.Nothing

  override fun ContentDrawScope.draw(node: EffectNode, recordSource: DrawScope.() -> Unit) {
    val stage = node.stages.firstOrNull { it is Stage.PlatformFilter } as? Stage.PlatformFilter
    val radius = stage?.radius ?: 0
    if (radius <= 0) {
      recordSource()
      lastState = CloudyState.Success.Applied
      return
    }

    // BlurEffect takes a radius in pixels; the Skiko backend converts it to sigma internally (same
    // 0.57735 * radius + 0.5 as HWUI). Reuse the layer + effect; rebuild the effect only on a radius
    // change so a steady-state frame allocates nothing.
    val blurRadius = radius.toFloat()
    val context = node.graphicsContext()
    val layer = blurLayer ?: context.createGraphicsLayer().also { blurLayer = it }

    val blurEffect = if (cachedBlurEffect == null || cachedBlurRadius != blurRadius) {
      BlurEffect(
        radiusX = blurRadius,
        radiusY = blurRadius,
        edgeTreatment = TileMode.Clamp,
      ).also {
        cachedBlurEffect = it
        cachedBlurRadius = blurRadius
      }
    } else {
      cachedBlurEffect
    }

    layer.record { recordSource() }
    if (layer.renderEffect != blurEffect) {
      layer.renderEffect = blurEffect
    }
    drawLayer(layer)

    lastState = CloudyState.Success.Applied
  }

  override fun currentState(node: EffectNode): CloudyState = lastState

  override fun detach(node: EffectNode) {
    blurLayer?.let { node.graphicsContext().releaseGraphicsLayer(it) }
    blurLayer = null
    cachedBlurEffect = null
    cachedBlurRadius = -1f
  }
}
