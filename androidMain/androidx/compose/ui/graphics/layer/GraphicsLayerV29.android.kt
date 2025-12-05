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
     * Update the internal and RenderNode clipping flags according to the current `clip` and
     * `outlineIsProvided` state.
     *
     * Sets `clipToBounds` when `clip` is true and no outline is provided, and sets
     * `clipToOutline` when `clip` is true and an outline is provided. Each RenderNode property
     * is updated only when its value changes.
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
     * Applies the given compositing strategy to this RenderNode by configuring whether the node
     * should use an offscreen compositing layer and whether it has overlapping rendering.
     *
     * @param compositingStrategy Strategy that determines compositing behavior:
     *   - Offscreen: enables a compositing layer and marks the node as having overlapping rendering.
     *   - ModulateAlpha: disables a compositing layer and marks the node as not having overlapping rendering.
     *   - Other: disables a compositing layer and marks the node as having overlapping rendering.
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
     * Applies the appropriate compositing strategy to the internal RenderNode.
     *
     * If a separate compositing layer is required, sets the node to use the Offscreen strategy;
     * otherwise applies the configured compositingStrategy.
     */
    private fun updateLayerProperties() {
        if (requiresCompositingLayer()) {
            renderNode.applyCompositingStrategy(CompositingStrategy.Offscreen)
        } else {
            renderNode.applyCompositingStrategy(compositingStrategy)
        }
    }

    /**
     * Positions the layer at the given top-left coordinates and updates its size.
     *
     * Sets the RenderNode's bounds to [x, y, x + size.width, y + size.height] and stores the layer size.
     *
     * @param x The x coordinate of the layer's top-left corner.
     * @param y The y coordinate of the layer's top-left corner.
     * @param size The new width and height of the layer in pixels.
     */
    override fun setPosition(x: Int, y: Int, size: IntSize) {
        renderNode.setPosition(x, y, x + size.width, y + size.height)
        this.size = size.toSize()
    }

    /**
     * Applies the given outline to the layer and updates the layer's clipping state.
     *
     * The provided `outline` is forwarded to the underlying RenderNode, `outlineIsProvided`
     * is updated to reflect whether an outline is present, and clipping is reapplied.
     *
     * @param outline The outline to set on the layer, or `null` to remove any outline.
     * @param outlineSize The size of the outline; ignored by this implementation.
     */
    override fun setOutline(outline: Outline?, outlineSize: IntSize) {
        // outlineSize is not required for this GraphicsLayer implementation
        renderNode.setOutline(outline)
        outlineIsProvided = outline != null
        applyClip()
    }

    override var isInvalidated: Boolean = true

    /**
     * Records drawing commands into the layer's RenderNode by executing the provided draw block.
     *
     * The draw block is executed with a DrawScope whose drawContext is configured with the given
     * density, layout direction, the graphics layer, the current layer size, and the recording canvas.
     * Recording is always ended and the layer's invalidation flag is cleared after recording.
     *
     * @param density The density to apply to the draw context.
     * @param layoutDirection The layout direction to apply to the draw context.
     * @param layer The GraphicsLayer associated with this recording.
     * @param block The drawing instructions executed inside a DrawScope while recording.
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
     * Renders this graphics layer's underlying RenderNode into the provided Canvas.
     *
     * @param canvas The Canvas to draw the RenderNode into.
     */
    override fun draw(canvas: Canvas) {
        canvas.nativeCanvas.drawRenderNode(renderNode)
    }

    /**
     * Get the transformation matrix for this layer's RenderNode.
     *
     * @return The Matrix describing the RenderNode's current transformation. 
     */
    override fun calculateMatrix(): Matrix {
        val m = matrix ?: Matrix().also { matrix = it }
        renderNode.getMatrix(m)
        return m
    }

    override val hasDisplayList: Boolean
        get() = renderNode.hasDisplayList()

    /**
     * Drops the cached display list associated with this layer.
     *
     * Calling this forces the layer to regenerate its display list on the next draw.
     */
    override fun discardDisplayList() {
        renderNode.discardDisplayList()
    }

    override val layerId: Long
        get() = renderNode.uniqueId

    /**
         * Gets the Paint instance used for the layer, creating and caching it on first access.
         *
         * @return The cached `android.graphics.Paint` used to apply layer compositing properties. */
        private fun obtainLayerPaint(): android.graphics.Paint =
        layerPaint ?: android.graphics.Paint().also { layerPaint = it }

    /**
             * Determines whether this graphics layer requires a separate compositing layer.
             *
             * @return `true` if the compositing strategy is `Offscreen`, a layer paint is required, or a render effect is present; `false` otherwise.
             */
            private fun requiresCompositingLayer(): Boolean =
        compositingStrategy == CompositingStrategy.Offscreen ||
            requiresLayerPaint() ||
            renderEffect != null

    /**
         * Indicates whether the layer requires a dedicated Paint for compositing.
         *
         * @return `true` if the layer needs a Paint because the blend mode is not `SrcOver` or a color filter is set, `false` otherwise.
         */
        private fun requiresLayerPaint(): Boolean =
        blendMode != BlendMode.SrcOver || colorFilter != null
}

@RequiresApi(Build.VERSION_CODES.S)
internal object RenderNodeVerificationHelper {

    /**
     * Applies the given Compose `RenderEffect` to the provided Android `RenderNode`.
     *
     * If `target` is non-null, its Android equivalent produced by `asAndroidRenderEffect()` is set on
     * the `renderNode`; if `target` is null, any existing render effect on the `renderNode` is cleared.
     *
     * @param renderNode The Android `RenderNode` to modify.
     * @param target The Compose `RenderEffect` to apply, or `null` to remove the effect.
     */
    fun setRenderEffect(renderNode: RenderNode, target: RenderEffect?) {
        renderNode.setRenderEffect(target?.asAndroidRenderEffect())
    }
}