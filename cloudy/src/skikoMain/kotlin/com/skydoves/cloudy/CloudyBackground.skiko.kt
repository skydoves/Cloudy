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
package com.skydoves.cloudy

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
import com.skydoves.cloudy.internals.SkySnapshot

/**
 * Skiko implementation of [Modifier.sky].
 *
 * Captures content to a [GraphicsLayer] and stores it in [Sky.backgroundLayer].
 * This implementation is shared across iOS, macOS, JVM Desktop, and WASM platforms.
 */
@Composable
public actual fun Modifier.sky(sky: Sky): Modifier = this.then(SkyModifierElement(sky = sky))

/**
 * Skiko implementation of [Modifier.cloudy] for background blur.
 *
 * Applies blur to the background content captured by [Modifier.sky]
 * using Skia's BlurEffect for GPU-accelerated rendering.
 *
 * This implementation is shared across iOS, macOS, JVM Desktop, and WASM platforms.
 *
 * Note: The [cpuBlurEnabled] parameter is ignored on Skiko platforms as they always use
 * GPU-accelerated blur via Skia. This parameter exists for API consistency with Android.
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

  LaunchedEffect(radius) {
    if (radius == 0) {
      onStateChanged(CloudyState.Success.Applied)
    }
  }

  return this.then(
    CloudyBackgroundModifierElement(
      sky = sky,
      radius = radius,
      progressive = progressive,
      tint = tint,
      light = light,
      shape = shape,
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

private data class CloudyBackgroundModifierElement(
  val sky: Sky,
  val radius: Int,
  val progressive: CloudyProgressive,
  val tint: Color,
  val light: LiquidGlassLight?,
  val shape: Shape,
  val onStateChanged: (CloudyState) -> Unit,
) : ModifierNodeElement<CloudyBackgroundModifierNode>() {

  override fun InspectorInfo.inspectableProperties() {
    name = "cloudy"
    properties["sky"] = sky
    properties["radius"] = radius
    properties["progressive"] = progressive
    properties["tint"] = tint
    properties["light"] = light
    properties["shape"] = shape
  }

  override fun create(): CloudyBackgroundModifierNode = CloudyBackgroundModifierNode(
    sky = sky,
    radius = radius,
    progressive = progressive,
    tint = tint,
    light = light,
    shape = shape,
    onStateChanged = onStateChanged,
  )

  override fun update(node: CloudyBackgroundModifierNode) {
    node.update(sky, radius, progressive, tint, light, shape, onStateChanged)
  }
}

private class CloudyBackgroundModifierNode(
  private var sky: Sky,
  private var radius: Int,
  private var progressive: CloudyProgressive,
  private var tint: Color,
  private var light: LiquidGlassLight?,
  private var shape: Shape,
  private var onStateChanged: (CloudyState) -> Unit,
) : Modifier.Node(),
  DrawModifierNode,
  GlobalPositionAwareModifierNode,
  LayoutAwareModifierNode {

  private var positionInRoot: Offset = Offset.Zero
  private var size: IntSize = IntSize.Zero

  // Specular highlight brush cache: the light moves most frames, so rebuild the Brush only when the
  // pool center/radius change. Moving the light re-runs only this overlay draw, never a blur re-record.
  private var cachedBrush: Brush? = null
  private var cachedCenter: Offset = Offset.Unspecified
  private var cachedRadius: Float = -1f

  // Stable re-blur invalidator (a field, not a fresh lambda) so the frame driver can identity-match it.
  private val reblur: () -> Unit = { if (isAttached) invalidateDraw() }

  // Cached blur layer + BlurEffect, rebuilt only when the blur radius changes.
  private var blurLayer: GraphicsLayer? = null
  private var cachedBlurEffect: BlurEffect? = null
  private var cachedBlurRadius: Float = -1f

  // Reusable Path for the shape clip, rebuilt only when the outline changes.
  private var clipPathCache: Path? = null

  fun update(
    sky: Sky,
    radius: Int,
    progressive: CloudyProgressive,
    tint: Color,
    light: LiquidGlassLight?,
    shape: Shape,
    onStateChanged: (CloudyState) -> Unit,
  ) {
    val needsRedraw = this.sky != sky ||
      this.radius != radius ||
      this.progressive != progressive ||
      this.tint != tint ||
      this.light != light ||
      this.shape != shape

    if (this.sky != sky && isAttached) {
      this.sky.frameDriver.removeOverlay(reblur)
      sky.frameDriver.addOverlay(reblur)
    }

    this.sky = sky
    this.radius = radius
    this.progressive = progressive
    this.tint = tint
    this.light = light
    this.shape = shape
    this.onStateChanged = onStateChanged

    if (needsRedraw && isAttached) {
      invalidateDraw()
    }
  }

  override fun onAttach() {
    sky.frameDriver.addOverlay(reblur)
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    positionInRoot = coordinates.positionInRoot()
  }

  override fun onRemeasured(size: IntSize) {
    this.size = size
  }

  override fun ContentDrawScope.draw() {
    // Draw nothing while this sky is recording: keeping the overlay out of the blur source avoids the
    // cyclic picture graph that crashes the render thread (issues/112). See [Sky.isCapturing].
    if (sky.isCapturing) {
      return
    }

    val backgroundLayer = sky.backgroundLayer
    if (backgroundLayer == null) {
      onStateChanged(CloudyState.Error(RuntimeException("Background layer not available")))
      drawContent()
      return
    }

    if (radius <= 0) {
      drawBackgroundRegion(backgroundLayer)
      drawContent()
      return
    }

    val skyBounds = sky.sourceBounds
    val offsetX = positionInRoot.x - skyBounds.left
    val offsetY = positionInRoot.y - skyBounds.top

    val snapshot = SkySnapshot.fromProgressive(
      radius = radius,
      offsetX = offsetX,
      offsetY = offsetY,
      childWidth = size.width,
      childHeight = size.height,
      progressive = progressive,
      tintColor = tint,
    )

    drawWithBlurEffect(backgroundLayer, snapshot)
    drawContent()
  }

  private fun ContentDrawScope.drawBackgroundRegion(layer: GraphicsLayer) {
    val skyBounds = sky.sourceBounds
    val offsetX = positionInRoot.x - skyBounds.left
    val offsetY = positionInRoot.y - skyBounds.top

    drawContext.canvas.save()
    drawContext.canvas.translate(-offsetX, -offsetY)
    drawLayer(layer)
    drawContext.canvas.restore()

    if (tint != Color.Transparent) {
      drawRect(color = tint, blendMode = BlendMode.SrcOver)
    }

    onStateChanged(CloudyState.Success.Applied)
  }

  private fun ContentDrawScope.drawWithBlurEffect(layer: GraphicsLayer, snapshot: SkySnapshot) {
    // BlurEffect takes a radius in pixels; the Skiko backend converts it to sigma internally
    // (same 0.57735 * radius + 0.5 as HWUI).
    val blurRadius = snapshot.radius.toFloat()

    // Reuse the blur layer + BlurEffect across draws; rebuild the effect only when the radius
    // changes, so a steady-state frame allocates nothing.
    val context = requireGraphicsContext()
    val blurLayer = this@CloudyBackgroundModifierNode.blurLayer
      ?: context.createGraphicsLayer().also { this@CloudyBackgroundModifierNode.blurLayer = it }

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

    blurLayer.record {
      drawContext.canvas.save()
      drawContext.canvas.translate(-snapshot.offsetX, -snapshot.offsetY)
      drawLayer(layer)
      drawContext.canvas.restore()
    }

    if (blurLayer.renderEffect != blurEffect) {
      blurLayer.renderEffect = blurEffect
    }

    clipToShape {
      drawLayer(blurLayer)

      if (snapshot.tintColor != Color.Transparent) {
        drawRect(color = snapshot.tintColor, blendMode = BlendMode.SrcOver)
      }

      // Experimental specular highlight (no-op when light == null).
      if (light != null) drawHighlight()
    }

    onStateChanged(CloudyState.Success.Applied)
  }

  /**
   * Clips [block] to [shape]'s outline so the blurred fill follows rounded corners instead of a hard
   * rectangle. [RectangleShape] is a plain rectangular clip (the existing behavior).
   */
  private fun ContentDrawScope.clipToShape(block: DrawScope.() -> Unit) {
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
      is Outline.Rectangle -> clipRect { block() }
      is Outline.Rounded -> {
        val path = (clipPathCache ?: Path().also { clipPathCache = it })
        path.rewind()
        path.addRoundRect(outline.roundRect)
        clipPath(path) { block() }
      }
      is Outline.Generic -> clipPath(outline.path) { block() }
    }
  }

  /**
   * Draws the moving specular highlight over the already-blurred backdrop, inside [clipToShape] so
   * the glint follows the surface shape. Uses `SrcOver` (not `Screen`): the brush already encodes an
   * additive falloff, and `SrcOver` is the same path used for tint.
   */
  private fun DrawScope.drawHighlight() {
    val light = light ?: return
    // Node's measured IntSize field — qualified so it isn't shadowed by DrawScope.size (canvas Size).
    val lensSize = this@CloudyBackgroundModifierNode.size
    if (minOf(lensSize.width, lensSize.height) <= 0) return
    // Pure geometry lives in commonMain so the shipped path is the unit-tested path.
    val center = highlightPoolCenter(lensSize, light.direction.value, HIGHLIGHT_FOCAL_K)
    val radius = highlightPoolRadius(lensSize, HIGHLIGHT_POOL_FRAC)
    val brush = if (center != cachedCenter || radius != cachedRadius) {
      Brush.radialGradient(
        colorStops = HIGHLIGHT_STOPS,
        center = center,
        radius = radius,
        tileMode = TileMode.Clamp,
      ).also {
        cachedBrush = it
        cachedCenter = center
        cachedRadius = radius
      }
    } else {
      cachedBrush!!
    }
    drawRect(brush = brush)
  }

  override fun onDetach() {
    sky.frameDriver.removeOverlay(reblur)
    blurLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
    blurLayer = null
    cachedBlurEffect = null
    cachedBlurRadius = -1f
    clipPathCache = null
    cachedBrush = null
    cachedCenter = Offset.Unspecified
    cachedRadius = -1f
  }
}
