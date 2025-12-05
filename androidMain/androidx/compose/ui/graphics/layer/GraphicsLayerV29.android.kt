/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.graphics.layer

import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.CanvasHolder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toAndroidBlendMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize

/** GraphicsLayer implementation for Android Q+ that uses the public RenderNode API */
@RequiresApi(Build.VERSION_CODES.Q)
internal class GraphicsLayerV29(
    override val ownerId: Long,
    private val canvasHolder: CanvasHolder = CanvasHolder(),
    private val canvasDrawScope: CanvasDrawScope = CanvasDrawScope(),
) : GraphicsLayerImpl {
    private val renderNode: RenderNode = RenderNode("graphicsLayer")

    private var size: Size = Size.Zero
    private var layerPaint: android.graphics.Paint? = null
    private var matrix: Matrix? = null
    private var outlineIsProvided = false

    init {
        renderNode.clipToBounds = false
        renderNode.applyCompositingStrategy(CompositingStrategy.Auto)
    }

    override var alpha: Float = 1.0f
        set(value) {
            field = value
            renderNode.alpha = value
        }

    override var blendMode: BlendMode = BlendMode.SrcOver
        set(value) {
            field = value
            obtainLayerPaint().apply { blendMode = value.toAndroidBlendMode() }
            updateLayerProperties()
        }

    override var colorFilter: ColorFilter? = null
        set(value) {
            field = value
            obtainLayerPaint().apply { colorFilter = value?.asAndroidColorFilter() }
            updateLayerProperties()
        }

    override var pivotOffset: Offset = Offset.Unspecified
        set(value) {
            field = value
            if (value.isUnspecified) {
                renderNode.resetPivot()
            } else {
                renderNode.pivotX = value.x
                renderNode.pivotY = value.y
            }
        }

    override var scaleX: Float = 1f
        set(value) {
            field = value
            renderNode.scaleX = value
        }

    override var scaleY: Float = 1f
        set(value) {
            field = value
            renderNode.scaleY = value
        }

    override var translationX: Float = 0f
        set(value) {
            field = value
            renderNode.translationX = value
        }

    override var translationY: Float = 0f
        set(value) {
            field = value
            renderNode.translationY = value
        }

    override var shadowElevation: Float = 0f
        set(value) {
            field = value
            renderNode.elevation = value
        }

    override var ambientShadowColor: Color = Color.Black
        set(value) {
            field = value
            renderNode.ambientShadowColor = value.toArgb()
        }

    override var spotShadowColor: Color = Color.Black
        set(value) {
            field = value
            renderNode.spotShadowColor = value.toArgb()
        }

    override var rotationX: Float = 0f
        set(value) {
            field = value
            renderNode.rotationX = value
        }

    override var rotationY: Float = 0f
        set(value) {
            field = value
            renderNode.rotationY = value
        }

    override var rotationZ: Float = 0f
        set(value) {
            field = value
            renderNode.rotationZ = value
        }

    override var cameraDistance: Float = DefaultCameraDistance
        set(value) {
            field = value
            renderNode.cameraDistance = value
        }

    override var clip: Boolean = false
        set(value) {
            field = value
            applyClip()
        }

    private var clipToBounds = false
    private var clipToOutline = false

    /**
     * Synchronizes clipping configuration with the current `clip` and outline state.
     *
     * Updates the internal `clipToBounds` and `clipToOutline` flags and applies them to
     * the underlying `RenderNode` so clipping reflects whether an outline is provided.
     */
    private fun applyClip() {
        val newClipToBounds = clip && !outlineIsProvided
        val newClipToOutline = clip && outlineIsProvided
        if (newClipToBounds != clipToBounds) {
            clipToBounds = newClipToBounds
            renderNode.clipToBounds = clipToBounds
        }
        if (newClipToOutline != clipToOutline) {
            clipToOutline = newClipToOutline
            renderNode.clipToOutline = newClipToOutline
        }
    }

    override var renderEffect: RenderEffect? = null
        set(value) {
            field = value
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RenderNodeVerificationHelper.setRenderEffect(renderNode, value)
            }
        }

    override var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto
        set(value) {
            field = value
            updateLayerProperties()
        }

    /**
     * Configure this RenderNode's compositing and overlapping-rendering settings based on the given compositing strategy.
     *
     * @param compositingStrategy Chooses how the node composes its contents:
     *   - `Offscreen`: enable a compositing (offscreen) layer and allow overlapping rendering.
     *   - `ModulateAlpha`: disable a compositing layer and disallow overlapping rendering.
     *   - otherwise: disable a compositing layer and allow overlapping rendering.
     */
    private fun RenderNode.applyCompositingStrategy(compositingStrategy: CompositingStrategy) {
        when (compositingStrategy) {
            CompositingStrategy.Offscreen -> {
                setUseCompositingLayer(true, layerPaint)
                setHasOverlappingRendering(true)
            }
            CompositingStrategy.ModulateAlpha -> {
                setUseCompositingLayer(false, layerPaint)
                setHasOverlappingRendering(false)
            }
            else -> {
                setUseCompositingLayer(false, layerPaint)
                setHasOverlappingRendering(true)
            }
        }
    }

    /**
     * Selects and applies the appropriate compositing strategy to the render node.
     *
     * If the current layer requires an offscreen compositing layer (for example due to blend
     * mode, color filter, or render effects), this forces the Offscreen strategy; otherwise
     * it applies the configured `compositingStrategy`.
     */
    private fun updateLayerProperties() {
        if (requiresCompositingLayer()) {
            renderNode.applyCompositingStrategy(CompositingStrategy.Offscreen)
        } else {
            renderNode.applyCompositingStrategy(compositingStrategy)
        }
    }

    /**
     * Set the layer's position and size on the underlying RenderNode.
     *
     * @param x The left position in pixels.
     * @param y The top position in pixels.
     * @param size The width and height of the layer in pixels.
     */
    override fun setPosition(x: Int, y: Int, size: IntSize) {
        renderNode.setPosition(x, y, x + size.width, y + size.height)
        this.size = size.toSize()
    }

    /**
     * Sets the RenderNode outline used for clipping and updates the layer's clipping state.
     *
     * The provided outline is applied to the underlying RenderNode; the internal flag tracking
     * whether an outline is present is updated and clipping is reapplied to reflect the change.
     *
     * @param outline The outline to apply to the layer, or `null` to clear the outline.
     * @param outlineSize Ignored for this implementation.
     */
    override fun setOutline(outline: Outline?, outlineSize: IntSize) {
        // outlineSize is not required for this GraphicsLayer implementation
        renderNode.setOutline(outline)
        outlineIsProvided = outline != null
        applyClip()
    }

    override var isInvalidated: Boolean = true

    /**
     * Records drawing operations into the layer's RenderNode using the provided drawing block.
     *
     * The drawing environment is configured with the given density, layout direction, graphics layer,
     * and the current layer size before executing `block`. Recording is ended when the block
     * completes and the layer is marked as not invalidated.
     *
     * @param block The drawing operations to record; executed with a `DrawScope` receiver configured
     *              for this layer's recording canvas.
     */
    override fun record(
        density: Density,
        layoutDirection: LayoutDirection,
        layer: GraphicsLayer,
        block: DrawScope.() -> Unit,
    ) {
        val recordingCanvas = renderNode.beginRecording()
        try {
            canvasHolder.drawInto(recordingCanvas) {
                canvasDrawScope.drawContext.also {
                    it.density = density
                    it.layoutDirection = layoutDirection
                    it.graphicsLayer = layer
                    it.size = size
                    it.canvas = this
                }
                canvasDrawScope.block()
            }
        } finally {
            renderNode.endRecording()
        }
        isInvalidated = false
    }

    /**
     * Draws the internal RenderNode contents into the provided canvas.
     *
     * @param canvas The canvas to draw the RenderNode into.
     */
    override fun draw(canvas: Canvas) {
        canvas.nativeCanvas.drawRenderNode(renderNode)
    }

    /**
     * Obtain the transformation matrix representing the render node's current local transform.
     *
     * The returned matrix is populated with the render node's matrix values; the same Matrix
     * instance may be reused by the implementation across calls.
     *
     * @return A Matrix populated with the render node's current local transform.
     */
    override fun calculateMatrix(): Matrix {
        val m = matrix ?: Matrix().also { matrix = it }
        renderNode.getMatrix(m)
        return m
    }

    override val hasDisplayList: Boolean
        get() = renderNode.hasDisplayList()

    /**
     * Discards the RenderNode's cached display list.
     *
     * Forces the layer to rebuild its display list the next time content is recorded or drawn.
     */
    override fun discardDisplayList() {
        renderNode.discardDisplayList()
    }

    override val layerId: Long
        get() = renderNode.uniqueId

    /**
         * Get the cached Paint used for layer rendering, creating and caching a new instance if necessary.
         *
         * @return The cached android.graphics.Paint instance used for layer properties.
         */
        private fun obtainLayerPaint(): android.graphics.Paint =
        layerPaint ?: android.graphics.Paint().also { layerPaint = it }

    /**
             * Determines whether this graphics layer needs a compositing (offscreen) layer.
             *
             * @return `true` if the compositing strategy is `Offscreen`, the layer requires a paint (blend mode or color filter), or a render effect is set; `false` otherwise.
             */
            private fun requiresCompositingLayer(): Boolean =
        compositingStrategy == CompositingStrategy.Offscreen ||
            requiresLayerPaint() ||
            renderEffect != null

    /**
         * Indicates whether the layer requires an Android Paint to apply compositing effects.
         *
         * @return `true` if the blend mode is not `BlendMode.SrcOver` or a non-null color filter is set, `false` otherwise.
         */
        private fun requiresLayerPaint(): Boolean =
        blendMode != BlendMode.SrcOver || colorFilter != null
}

@RequiresApi(Build.VERSION_CODES.S)
internal object RenderNodeVerificationHelper {

    /**
     * Applies the given RenderEffect to the RenderNode, or clears any existing effect when `target` is `null`.
     *
     * @param renderNode The Android RenderNode to update.
     * @param target The Compose RenderEffect to apply, or `null` to remove the current render effect.
     */
    fun setRenderEffect(renderNode: RenderNode, target: RenderEffect?) {
        renderNode.setRenderEffect(target?.asAndroidRenderEffect())
    }
}