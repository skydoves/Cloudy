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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.skydoves.cloudy.internals.SkySnapshot
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds

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
  enabled: Boolean,
  @Suppress("UNUSED_PARAMETER") cpuBlurEnabled: Boolean,
  shape: Shape,
  onStateChanged: (CloudyState) -> Unit,
): Modifier {
  require(radius >= 0) { "Blur radius must be non-negative, but was $radius" }

  if (!enabled) {
    return this
  }

  // Notify state for zero radius
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
      shape = shape,
      onStateChanged = onStateChanged,
    ),
  )
}

// ============================================================================
// Sky Modifier Implementation
// ============================================================================

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
  Modifier.Node(),
  DrawModifierNode,
  GlobalPositionAwareModifierNode {

  private var graphicsLayer: GraphicsLayer? = null
  private var positionInRoot: Offset = Offset.Zero

  // Throttle version increments to reduce blur processing frequency. Uses a monotonic time mark
  // (multiplatform) rather than a wall clock so it works across iOS/macOS/Desktop/WASM.
  private var lastVersionIncrement: TimeMark? = null

  companion object {
    // Only increment version every 100ms to prevent excessive blur updates
    private val VERSION_INCREMENT_INTERVAL = 100.milliseconds
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
    val newlyCreated = graphicsLayer == null
    val layer = graphicsLayer ?: context.createGraphicsLayer().also {
      graphicsLayer = it
    }

    // Record the background into `layer`, the source a descendant `cloudy` overlay samples
    // and blurs. `isCapturing` is set so the overlay draws nothing during this pass: it must be
    // ABSENT from the blur source. Otherwise the overlay would draw its blur layer into `layer`,
    // and that blur layer in turn samples `layer` — a cyclic RenderNode/picture graph that
    // overflows the render thread stack (https://github.com/skydoves/Cloudy/issues/112).
    sky.isCapturing = true
    try {
      layer.record {
        this@draw.drawContent()
      }
    } finally {
      sky.isCapturing = false
    }

    // `layer` is reused across draws, so publish it to the snapshot state ONCE (when first
    // created). Re-assigning the same instance every draw would write snapshot state that the
    // descendant overlay reads in its own draw, invalidating it and triggering an endless redraw
    // loop that keeps the window producing frames even while idle.
    if (newlyCreated) {
      sky.backgroundLayer = layer
    }

    // Re-recording `layer` above re-captures the current backdrop, so bump the content version.
    // Throttle it: an unconditional per-draw bump writes snapshot state read during the overlay's
    // draw, which re-invalidates the overlay and self-perpetuates the redraw loop, so the app
    // never goes idle. Rate-limiting lets the tree settle to zero frames once the backdrop stops
    // changing.
    val lastMark = lastVersionIncrement
    if (lastMark == null || lastMark.elapsedNow() >= VERSION_INCREMENT_INTERVAL) {
      lastVersionIncrement = TimeSource.Monotonic.markNow()
      sky.incrementContentVersion()
    }

    // Draw the subtree to the window. `isCapturing` is now false, so the overlay paints its
    // blurred backdrop (sampling `layer`, which contains no reference back to the overlay) and
    // its own foreground here. The blur layer is composited straight to the window canvas, never
    // into `layer`, so no cycle is formed while the backdrop still renders.
    drawContent()
  }

  override fun onDetach() {
    graphicsLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
    graphicsLayer = null
    sky.backgroundLayer = null
  }
}

// ============================================================================
// CloudyBackground Modifier Implementation
// ============================================================================

private data class CloudyBackgroundModifierElement(
  val sky: Sky,
  val radius: Int,
  val progressive: CloudyProgressive,
  val tint: Color,
  val shape: Shape,
  val onStateChanged: (CloudyState) -> Unit,
) : ModifierNodeElement<CloudyBackgroundModifierNode>() {

  override fun InspectorInfo.inspectableProperties() {
    name = "cloudy"
    properties["sky"] = sky
    properties["radius"] = radius
    properties["progressive"] = progressive
    properties["tint"] = tint
    properties["shape"] = shape
  }

  override fun create(): CloudyBackgroundModifierNode = CloudyBackgroundModifierNode(
    sky = sky,
    radius = radius,
    progressive = progressive,
    tint = tint,
    shape = shape,
    onStateChanged = onStateChanged,
  )

  override fun update(node: CloudyBackgroundModifierNode) {
    node.update(sky, radius, progressive, tint, shape, onStateChanged)
  }
}

private class CloudyBackgroundModifierNode(
  private var sky: Sky,
  private var radius: Int,
  private var progressive: CloudyProgressive,
  private var tint: Color,
  private var shape: Shape,
  private var onStateChanged: (CloudyState) -> Unit,
) : Modifier.Node(),
  DrawModifierNode,
  GlobalPositionAwareModifierNode,
  LayoutAwareModifierNode {

  private var positionInRoot: Offset = Offset.Zero
  private var size: IntSize = IntSize.Zero

  // Cached blur layer + BlurEffect. Reused across draws; the effect is rebuilt only when the blur
  // radius changes (it is size-independent), so a steady-state frame allocates nothing.
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
    shape: Shape,
    onStateChanged: (CloudyState) -> Unit,
  ) {
    val needsRedraw = this.sky != sky ||
      this.radius != radius ||
      this.progressive != progressive ||
      this.tint != tint ||
      this.shape != shape

    this.sky = sky
    this.radius = radius
    this.progressive = progressive
    this.tint = tint
    this.shape = shape
    this.onStateChanged = onStateChanged

    if (needsRedraw && isAttached) {
      invalidateDraw()
    }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    positionInRoot = coordinates.positionInRoot()
  }

  override fun onRemeasured(size: IntSize) {
    this.size = size
  }

  override fun ContentDrawScope.draw() {
    // When `sky` is an ancestor of this overlay, the sky's capture pass re-enters this draw
    // while recording the blur source into `backgroundLayer`. This overlay must be ABSENT from
    // that source: if it drew here, its blur layer (which samples `backgroundLayer`) would be
    // recorded into `backgroundLayer`, forming a cyclic RenderNode/picture graph that crashes
    // the render thread (https://github.com/skydoves/Cloudy/issues/112). Draw nothing during
    // capture; the overlay paints itself in the sky's on-screen pass, when `isCapturing` is false.
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
      // No blur, draw the background region directly
      drawBackgroundRegion(backgroundLayer)
      drawContent()
      return
    }

    // Calculate offset relative to sky
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

    // Apply tint if specified
    if (tint != Color.Transparent) {
      drawRect(color = tint, blendMode = BlendMode.SrcOver)
    }

    onStateChanged(CloudyState.Success.Applied)
  }

  private fun ContentDrawScope.drawWithBlurEffect(layer: GraphicsLayer, snapshot: SkySnapshot) {
    // BlurEffect's radiusX/radiusY are blur radii in pixels; Compose's Skiko backend
    // converts the radius to a sigma internally (same 0.57735 * radius + 0.5 as HWUI)
    // before handing it to Skia, so pass the requested radius through directly.
    val blurRadius = snapshot.radius.toFloat()

    // Reuse the blur layer and BlurEffect across draws; rebuild the effect only when the radius
    // changes. Allocating a GraphicsLayer + BlurEffect every frame churns memory and GPU state.
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

    // Record the clipped background region
    blurLayer.record {
      // Translate to sample correct region from background
      drawContext.canvas.save()
      drawContext.canvas.translate(-snapshot.offsetX, -snapshot.offsetY)
      drawLayer(layer)
      drawContext.canvas.restore()
    }

    if (blurLayer.renderEffect != blurEffect) {
      blurLayer.renderEffect = blurEffect
    }

    // Clip to the surface shape (rounded corners included) and draw the blurred layer, so the
    // blurred fill follows the rounded edge instead of leaving a hard rectangular inner box.
    clipToShape {
      drawLayer(blurLayer)

      // Apply tint
      if (snapshot.tintColor != Color.Transparent) {
        drawRect(color = snapshot.tintColor, blendMode = BlendMode.SrcOver)
      }
    }

    onStateChanged(CloudyState.Success.Applied)
  }

  /**
   * Clips [block] to [shape]'s outline so the blurred fill follows rounded corners instead of a
   * hard rectangle. For [RectangleShape] this is a plain rectangular clip (the existing behavior),
   * so default callers are unaffected.
   */
  private fun ContentDrawScope.clipToShape(block: DrawScope.() -> Unit) {
    // `size` here is the draw scope's size (the composite area), already a Size in pixels.
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

  override fun onDetach() {
    blurLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
    blurLayer = null
    cachedBlurEffect = null
    cachedBlurRadius = -1f
    clipPathCache = null
  }
}
