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

package com.skydoves.cloudy

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
import com.skydoves.cloudy.internal.BlurWeather
import com.skydoves.cloudy.internal.PostProcess
import com.skydoves.cloudy.internal.Skylight
import com.skydoves.cloudy.internal.Stage
import com.skydoves.cloudy.internal.WeatherElement

/**
 * Skiko implementation of [Modifier.sky].
 *
 * Captures content to a [GraphicsLayer] and stores it in [Sky.backgroundLayer].
 * This implementation is shared across iOS, macOS, JVM Desktop, and WASM platforms.
 */
@Composable
public actual fun Modifier.sky(sky: Sky): Modifier = this.then(SkyModifierElement(sky = sky))

/**
 * Skiko implementation of [Modifier.cloudy] for background blur, on the unified [WeatherElement]
 * spine. Skia's `BlurEffect` is always available, so this is always the Clear path; [cpuBlurEnabled]
 * is ignored (kept for API parity with Android). This implementation is shared across iOS, macOS, JVM
 * Desktop, and WASM platforms.
 */
@Composable
public actual fun Modifier.cloudy(
  sky: Sky,
  @IntRange(from = 0) radius: Int,
  progressive: CloudyProgressive,
  tint: Color,
  light: LiquidGlassLight?,
  enabled: Boolean,
  @Suppress("UNUSED_PARAMETER") cpuBlurEnabled: Boolean,
  shape: Shape,
  onStateChanged: (CloudyState) -> Unit,
): Modifier {
  require(radius >= 0) { "Blur radius must be non-negative, but was $radius" }

  if (!enabled) {
    return this
  }

  val skylight = remember(sky) { Skylight.Backdrop(sky) }
  val weather = remember { BlurWeather() }
  return this.then(
    WeatherElement(
      weather = weather,
      skylight = skylight,
      clock = MirageClock.Paused,
      enabled = true,
      stages = listOf(Stage.PlatformFilter(radius, progressive)),
      postProcess = PostProcess(shape, tint, light),
      weatherKey = Unit,
      onStateChanged = onStateChanged,
    ),
  )
}

private data class SkyModifierElement(val sky: Sky) : ModifierNodeElement<SkyModifierNode>() {

  override fun InspectorInfo.inspectableProperties() {
    name = "sky"
  }

  override fun create(): SkyModifierNode = SkyModifierNode(sky = sky)

  override fun update(node: SkyModifierNode) {
    node.sky = sky
  }
}

private class SkyModifierNode(var sky: Sky) :
  DelegatingNode(),
  DrawModifierNode,
  GlobalPositionAwareModifierNode {

  private var graphicsLayer: GraphicsLayer? = null
  private var positionInRoot: Offset = Offset.Zero

  // Stable invalidator the frame driver pumps each scroll frame to re-capture the moved backdrop.
  private val recapture: () -> Unit = { if (isAttached) invalidateDraw() }

  // Forward descendant scroll/fling to the frame driver: a scrollable's scroll does not re-invoke this
  // recorder's draw, so this is the "backdrop moving now" signal that triggers re-capture + re-blur.
  private val scrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      sky.frameDriver.onScrollActivity()
      return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
      sky.frameDriver.onScrollActivity()
      return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
      sky.frameDriver.onScrollActivity()
      return Velocity.Zero
    }
  }

  init {
    delegate(nestedScrollModifierNode(scrollConnection, dispatcher = null))
  }

  override fun onAttach() {
    sky.frameDriver.attachRecorder(coroutineScope, recapture)
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    positionInRoot = coordinates.positionInRoot()
    sky.sourceBounds = Rect(
      positionInRoot,
      coordinates.size.toSize(),
    )
  }

  override fun ContentDrawScope.draw() {
    val context = requireGraphicsContext()
    val layer = graphicsLayer ?: context.createGraphicsLayer().also {
      graphicsLayer = it
    }

    // `sky.capturing` marks overlays absent from the blur source while recording: otherwise an
    // overlay's blur layer (which samples this backdrop) is recorded into the backdrop, forming a
    // cyclic picture graph that overflows the render thread stack (issues/112).
    sky.capturing {
      layer.record {
        this@draw.drawContent()
      }
    }

    // Plain (non-snapshot) field: the overlay re-reads it each draw and the frame driver re-runs the
    // overlay, so no snapshot observation is needed. A snapshot write here caused an idle redraw loop.
    sky.backgroundLayer = layer

    drawContent()
  }

  override fun onDetach() {
    sky.frameDriver.detachRecorder(coroutineScope)
    graphicsLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
    graphicsLayer = null
    sky.backgroundLayer = null
  }
}
