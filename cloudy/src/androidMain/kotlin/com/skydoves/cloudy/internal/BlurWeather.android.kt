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

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import com.skydoves.cloudy.CloudyDefaults
import com.skydoves.cloudy.CloudyState
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.internals.SkySnapshot
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

private const val TAG = "CloudyBackground"

/**
 * Android blur weather for both the self-lit foreground and the backdrop. Resolves a [Visibility]
 * ladder each draw:
 *
 *  - [Visibility.Clear] (API 31+): a single reusable [GraphicsLayer] + a [RenderEffect] cached on the
 *    radius — the former `drawWithRenderEffect` (bg) and `CloudyRenderEffectStrategy` (fg), unified.
 *  - [Visibility.Hazy] (API < 31 + [cpuBlurEnabled]): delegates to the extracted legacy CPU machines,
 *    which self-capture and run asynchronously; the spine's layer pool is bypassed.
 *  - [Visibility.Overcast] (API < 31, no cpuBlurEnabled, backdrop only): a scrim, via
 *    [shouldDrawContentBehind] + [drawScrim].
 *
 * Radius/progressive are read from the node's [Stage.PlatformFilter] at draw time so a radius change
 * re-keys the cached effect on the cheap update path. [tint] is the scrim color for the Overcast path
 * only (the Clear/Hazy tint is the node's [PostProcess]); [cpuBlurEnabled] is fixed `true` for the
 * foreground (fg has no Overcast — it always has a CPU path below API 31).
 */
internal class BlurWeather(private val cpuBlurEnabled: Boolean, private val tint: Color) :
  Weather {

  // Clear cache (draw-time radius key).
  private var blurLayer: GraphicsLayer? = null
  private var cachedBlurEffect: ComposeRenderEffect? = null
  private var cachedBlurRadius: Float = -1f
  private var hasLoggedProgressiveWarning = false

  private val legacyForeground = LegacyForegroundBlurMachine()
  private val legacyBackdrop = LegacyBackdropBlurMachine()
  private val backdropClear = BackdropClearBlurMachine()

  private var lastState: CloudyState = CloudyState.Nothing

  private fun stageOf(node: WeatherNode): Stage.PlatformFilter? =
    node.stages.firstOrNull { it is Stage.PlatformFilter } as? Stage.PlatformFilter

  override fun ContentDrawScope.draw(node: WeatherNode, recordSource: DrawScope.() -> Unit) {
    val stage = stageOf(node) ?: return
    when (Visibility.resolve(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S, cpuBlurEnabled)) {
      Visibility.Clear -> drawClear(node, stage, recordSource)
      Visibility.Hazy -> drawHazy(node, stage, recordSource)
      Visibility.Overcast -> Unit // handled by the node via shouldDrawContentBehind + drawScrim
    }
  }

  private fun ContentDrawScope.drawClear(
    node: WeatherNode,
    stage: Stage.PlatformFilter,
    recordSource: DrawScope.() -> Unit,
  ) {
    // A BACKDROP samples the sky's captured layer. Drawing `drawLayer(sky.backgroundLayer)` (directly,
    // or under a blur RenderEffect) forms a cyclic RenderNode graph — this node's draw sits inside the
    // sky layer it references back — that crashes captureToImage/PixelCopy with an unbounded
    // prepareTreeImpl recursion (issues/112). So the backdrop always samples a rasterized SNAPSHOT of
    // the sky region (drawImage, no drawLayer back-edge), the acyclic structure the API < 31 CPU path
    // uses. This covers radius 0 too: the inline `drawLayer(sky.backgroundLayer)` (passthrough / tint /
    // scrim over-draw) is the same back-edge and also crashes. The foreground self-lit path samples its
    // OWN content (no sky layer, no cycle), so it keeps the direct inline / render-effect path below.
    val backdrop = node.skylight as? Skylight.Backdrop
    if (backdrop != null) {
      val layer = backdrop.sky.backgroundLayer
      if (layer == null) {
        lastState = CloudyState.Error(RuntimeException("Background layer not available"))
        return
      }
      // Progressive blur is unsupported on API 31-32 (RenderEffect); warn once per session.
      if (stage.radius > 0 && stage.progressive != com.skydoves.cloudy.CloudyProgressive.None &&
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
      ) {
        logProgressiveBlurWarningOnce()
      }
      with(backdropClear) {
        draw(node, layer, stage.radius, node.sampleOffset(), backdrop.sky.contentVersion)
      }
      lastState = backdropClear.lastState
      return
    }

    if (stage.radius <= 0) {
      recordSource()
      lastState = CloudyState.Success.Applied
      return
    }

    // createBlurEffect takes a radius in pixels (HWUI converts to sigma internally).
    val blurRadius = stage.radius.toFloat()
    val context = node.graphicsContext()
    val layer = blurLayer ?: context.createGraphicsLayer().also { blurLayer = it }

    val blurEffect = if (cachedBlurEffect == null || cachedBlurRadius != blurRadius) {
      RenderEffect
        .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
        .asComposeRenderEffect()
        .also {
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

  private fun ContentDrawScope.drawHazy(
    node: WeatherNode,
    stage: Stage.PlatformFilter,
    recordSource: DrawScope.() -> Unit,
  ) {
    val backdrop = node.skylight as? Skylight.Backdrop
    lastState = if (backdrop == null) {
      // Foreground self-lit CPU blur: the machine self-captures the node's own content.
      with(legacyForeground) { draw(node, stage.radius, recordSource) }
      legacyForeground.lastState
    } else {
      val layer = backdrop.sky.backgroundLayer
      if (layer == null) {
        // Should not happen: the node returns before draw() when the backdrop layer is null. Kept as
        // a defensive echo of the old node's Error state.
        CloudyState.Error(RuntimeException("Background layer not available"))
      } else {
        val offset = node.sampleOffset()
        val snapshot = SkySnapshot.fromProgressive(
          radius = stage.radius,
          offsetX = offset.x,
          offsetY = offset.y,
          childWidth = size.width,
          childHeight = size.height,
          progressive = stage.progressive,
          tintColor = tint,
        )
        with(legacyBackdrop) { draw(node, layer, snapshot, backdrop.sky.contentVersion) }
        legacyBackdrop.lastState
      }
    }
  }

  override fun shouldDrawContentBehind(node: WeatherNode): Boolean =
    Visibility.resolve(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S, cpuBlurEnabled) ==
      Visibility.Overcast

  override fun ContentDrawScope.drawScrim(node: WeatherNode, recordSource: DrawScope.() -> Unit) {
    // Overcast is backdrop-only. The scrim carries the tint (or the default scrim when transparent).
    val scrim = if (tint == Color.Transparent) CloudyDefaults.DefaultScrimColor else tint
    // Draw the sampled backdrop region as a rasterized SNAPSHOT (drawImage), not `drawLayer(sky
    // .backgroundLayer)`: the direct layer reference would form the cyclic RenderNode graph that
    // crashes captureToImage (issues/112). The bitmap draw is acyclic (same as the Clear path). Fall
    // back to the direct recordSource only when the backdrop layer is unexpectedly missing.
    val backdrop = node.skylight as? Skylight.Backdrop
    val layer = backdrop?.sky?.backgroundLayer
    if (backdrop != null && layer != null) {
      with(backdropClear) {
        // radius 0 = passthrough draw of the snapshot region; the scrim is layered on top below.
        draw(
          node,
          layer,
          radius = 0,
          offset = node.sampleOffset(),
          contentVersion = backdrop.sky.contentVersion,
        )
      }
    } else {
      recordSource()
    }
    drawRect(color = scrim, blendMode = BlendMode.SrcOver)
    lastState = CloudyState.Success.Scrim
  }

  override fun currentState(node: WeatherNode): CloudyState {
    // The node returns before draw() when a backdrop's captured layer is not ready; echo the old
    // node's cold-start Error state there, matching the former CloudyBackgroundModifierNode.
    val backdrop = node.skylight as? Skylight.Backdrop
    if (backdrop != null && backdrop.sky.backgroundLayer == null) {
      return CloudyState.Error(RuntimeException("Background layer not available"))
    }
    return lastState
  }

  override fun detach(node: WeatherNode) {
    blurLayer?.let { node.graphicsContext().releaseGraphicsLayer(it) }
    blurLayer = null
    cachedBlurEffect = null
    cachedBlurRadius = -1f
    legacyForeground.dispose()
    legacyBackdrop.dispose()
    backdropClear.dispose(node)
  }

  private fun logProgressiveBlurWarningOnce() {
    if (!hasLoggedProgressiveWarning) {
      hasLoggedProgressiveWarning = true
      Log.w(
        TAG,
        "Progressive blur requires API 33+ for background blur. " +
          "Falling back to uniform blur on API ${Build.VERSION.SDK_INT}.",
      )
    }
  }
}
