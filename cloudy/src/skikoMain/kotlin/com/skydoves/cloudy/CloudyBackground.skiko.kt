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
      light = light,
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
  DelegatingNode(),
  DrawModifierNode,
  GlobalPositionAwareModifierNode {

  private var graphicsLayer: GraphicsLayer? = null
  private var positionInRoot: Offset = Offset.Zero

  // Stable invalidator the frame driver pumps each scroll frame to re-capture the moved backdrop.
  private val recapture: () -> Unit = { if (isAttached) invalidateDraw() }

  // Forward descendant scroll/fling deltas to the frame driver: this is the precise "the backdrop is
  // moving now" signal the draw phase lacks (a scrollable's scroll does not re-invoke this recorder's
  // draw). The driver re-captures + re-blurs while scrolling, then parks so the app idles.
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

    // Record the background into `layer`, the source a `cloudy` overlay of this sky samples and
    // blurs. `sky.capturing` marks the sky as recording so its overlays draw nothing during this
    // pass: an overlay must be ABSENT from the blur source. Otherwise the overlay would draw its
    // blur layer into the layer being recorded, and that blur layer in turn samples a backdrop
    // layer — a cyclic picture graph that overflows the render thread stack
    // (https://github.com/skydoves/Cloudy/issues/112).
    sky.capturing {
      layer.record {
        this@draw.drawContent()
      }
    }

    // Publish the just-captured backdrop. A PLAIN (non-snapshot) field, so re-assigning the same
    // instance each draw is free and never re-invalidates the overlay (that snapshot-write loop was
    // the original idle redraw bug). The overlay re-reads it each draw; the frame driver re-runs the
    // overlay after a fresh capture.
    sky.backgroundLayer = layer

    // Draw the subtree to the window. `isCapturing` is now false, so the overlay paints its
    // blurred backdrop (sampling `layer`, which contains no reference back to the overlay) and
    // its own foreground here. The blur layer is composited straight to the window canvas, never
    // into `layer`, so no cycle is formed while the backdrop still renders.
    drawContent()
  }

  override fun onDetach() {
    sky.frameDriver.detachRecorder(coroutineScope)
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

  // Specular highlight brush cache. The light direction is read at gyro rate (~30Hz), so the pool
  // center moves most frames; rebuild the Brush only when center/radius actually change to avoid
  // per-frame Brush allocation on the draw hot path. Moving the light re-runs only this overlay
  // draw; it does NOT re-record or re-blur the cached blur layer.
  private var cachedBrush: Brush? = null
  private var cachedCenter: Offset = Offset.Unspecified
  private var cachedRadius: Float = -1f

  // Stable re-blur invalidator the frame driver runs after each capture. A field (not a fresh lambda
  // per call) so the driver can identity-match it on add/remove.
  private val reblur: () -> Unit = { if (isAttached) invalidateDraw() }

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
    // Draw nothing while this sky is recording: the overlay must be absent from the blur source,
    // else its blur layer (which samples the backdrop) is recorded into the backdrop — a cyclic
    // picture graph that crashes the render thread (issues/112). A `cloudy` surface is
    // background-only; its foreground lives outside the recorder, so nothing is lost here. See
    // [Sky.isCapturing].
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

      // Experimental specular highlight over the blurred backdrop (no-op when light == null).
      if (light != null) drawHighlight()
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

  /**
   * Draws the moving specular highlight over the already-blurred backdrop. Called only from the
   * blurred path, inside its existing [clipToShape] so the glint follows the surface shape.
   * Composited with the default `SrcOver` blend: the highlight brush already encodes a screen-like
   * additive falloff (soft warm-white radial that fades to transparent), so a plain `SrcOver`
   * composite reads as an additive glint. The shader (Screen) vs this overlay (SrcOver) delta is an
   * accepted trade-off, reusing the same `SrcOver` path already used for tint.
   *
   * Reads the Node's [light] (must be non-null here) and [size] fields and reuses [cachedBrush]
   * unless the pool center/radius moved this frame — a pure overlay composite on the cached blur
   * layer, never a blur re-record.
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
