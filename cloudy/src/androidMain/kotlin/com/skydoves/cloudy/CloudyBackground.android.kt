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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.skydoves.cloudy.internals.SkySnapshot
import com.skydoves.cloudy.internals.render.iterativeBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "CloudyBackground"

/**
 * Android implementation of [Modifier.sky].
 *
 * Captures content to a [GraphicsLayer] and stores it in [Sky.backgroundLayer].
 */
@Composable
public actual fun Modifier.sky(sky: Sky): Modifier {
  return this.then(SkyModifierElement(sky = sky))
}

/**
 * Android implementation of [Modifier.cloudy] for background blur.
 *
 * Applies blur to the background content captured by [Modifier.sky].
 */
@Composable
public actual fun Modifier.cloudy(
  sky: Sky,
  @IntRange(from = 0) radius: Int,
  progressive: CloudyProgressive,
  tint: Color,
  enabled: Boolean,
  onStateChanged: (CloudyState) -> Unit,
): Modifier {
  require(radius >= 0) { "Blur radius must be non-negative, but was $radius" }

  if (!enabled) {
    return this
  }

  // Log performance warning for legacy API
  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      Log.w(
        TAG,
        "cloudy(sky) on API ${Build.VERSION.SDK_INT} uses CPU-based blur. " +
          "Performance may be degraded for large blur radii or frequent updates.",
      )
    }
  }

  return this.then(
    CloudyBackgroundModifierElement(
      sky = sky,
      radius = radius,
      progressive = progressive,
      tint = tint,
      onStateChanged = onStateChanged,
    ),
  )
}

// ============================================================================
// Sky Modifier Implementation
// ============================================================================

private data class SkyModifierElement(
  val sky: Sky,
) : ModifierNodeElement<SkyModifierNode>() {

  override fun InspectorInfo.inspectableProperties() {
    name = "sky"
  }

  override fun create(): SkyModifierNode = SkyModifierNode(sky = sky)

  override fun update(node: SkyModifierNode) {
    node.sky = sky
  }
}

private class SkyModifierNode(
  var sky: Sky,
) : Modifier.Node(),
  DrawModifierNode,
  GlobalPositionAwareModifierNode {

  private var graphicsLayer: GraphicsLayer? = null
  private var positionInRoot: Offset = Offset.Zero

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

    // Record content to layer
    layer.record {
      this@draw.drawContent()
    }

    // Share layer with children
    sky.backgroundLayer = layer
    sky.isDirty = false
    sky.incrementContentVersion()

    // Draw original content to screen
    drawLayer(layer)
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
  val onStateChanged: (CloudyState) -> Unit,
) : ModifierNodeElement<CloudyBackgroundModifierNode>() {

  override fun InspectorInfo.inspectableProperties() {
    name = "cloudy"
    properties["sky"] = sky
    properties["radius"] = radius
    properties["progressive"] = progressive
    properties["tint"] = tint
  }

  override fun create(): CloudyBackgroundModifierNode = CloudyBackgroundModifierNode(
    sky = sky,
    radius = radius,
    progressive = progressive,
    tint = tint,
    onStateChanged = onStateChanged,
  )

  override fun update(node: CloudyBackgroundModifierNode) {
    node.update(sky, radius, progressive, tint, onStateChanged)
  }
}

private class CloudyBackgroundModifierNode(
  private var sky: Sky,
  private var radius: Int,
  private var progressive: CloudyProgressive,
  private var tint: Color,
  private var onStateChanged: (CloudyState) -> Unit,
) : Modifier.Node(),
  DrawModifierNode,
  GlobalPositionAwareModifierNode,
  LayoutAwareModifierNode,
  CompositionLocalConsumerModifierNode {

  private var positionInRoot: Offset = Offset.Zero
  private var size: IntSize = IntSize.Zero

  // Legacy blur state (API 30-)
  private var blurredBitmap: PlatformBitmap? = null
  private var isProcessing: Boolean = false
  private var blurJob: Job? = null

  // Track sky content changes for cache invalidation (API < 31 only)
  private var lastContentVersion: Long = -1L

  fun update(
    sky: Sky,
    radius: Int,
    progressive: CloudyProgressive,
    tint: Color,
    onStateChanged: (CloudyState) -> Unit,
  ) {
    val needsRedraw = this.sky != sky ||
      this.radius != radius ||
      this.progressive != progressive ||
      this.tint != tint

    this.sky = sky
    this.radius = radius
    this.progressive = progressive
    this.tint = tint
    this.onStateChanged = onStateChanged

    if (needsRedraw) {
      blurJob?.cancel()
      blurJob = null
      isProcessing = false
      blurredBitmap = null
      if (isAttached) {
        invalidateDraw()
      }
    }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    positionInRoot = coordinates.positionInRoot()
  }

  override fun onRemeasured(size: IntSize) {
    this.size = size
  }

  override fun ContentDrawScope.draw() {
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
      childWidth = size.width.toFloat(),
      childHeight = size.height.toFloat(),
      progressive = progressive,
      tintColor = tint,
    )

    when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
        drawWithRuntimeShader(backgroundLayer, snapshot)
      }
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && progressive == CloudyProgressive.None -> {
        drawWithRenderEffect(backgroundLayer, snapshot)
      }
      else -> {
        drawWithBitmap(backgroundLayer, snapshot)
      }
    }
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

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun ContentDrawScope.drawWithRuntimeShader(
    layer: GraphicsLayer,
    snapshot: SkySnapshot,
  ) {
    // For API 33+, use RenderEffect with uniform blur
    // Progressive blur shader would be implemented here with AGSL RuntimeShader
    // For now, fall back to RenderEffect
    drawWithRenderEffect(layer, snapshot)
  }

  @RequiresApi(Build.VERSION_CODES.S)
  private fun ContentDrawScope.drawWithRenderEffect(
    layer: GraphicsLayer,
    snapshot: SkySnapshot,
  ) {
    val sigma = snapshot.radius / 2.0f

    // Create blur effect
    val blurEffect = RenderEffect
      .createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP)
      .asComposeRenderEffect()

    val context = requireGraphicsContext()
    val blurLayer = context.createGraphicsLayer()

    try {
      // Record the clipped background region with blur
      blurLayer.record {
        // Translate to sample correct region from background
        drawContext.canvas.save()
        drawContext.canvas.translate(-snapshot.offsetX, -snapshot.offsetY)
        drawLayer(layer)
        drawContext.canvas.restore()
      }

      blurLayer.renderEffect = blurEffect

      // Clip to current bounds and draw the blurred layer
      clipRect {
        drawLayer(blurLayer)

        // Apply tint
        if (snapshot.tintColor != Color.Transparent) {
          drawRect(color = snapshot.tintColor, blendMode = BlendMode.SrcOver)
        }
      }

      onStateChanged(CloudyState.Success.Applied)
    } finally {
      context.releaseGraphicsLayer(blurLayer)
    }
  }

  private fun ContentDrawScope.drawWithBitmap(
    layer: GraphicsLayer,
    snapshot: SkySnapshot,
  ) {
    val currentVersion = sky.contentVersion

    // Check if sky content has changed (scroll detected)
    if (currentVersion != lastContentVersion && blurredBitmap != null) {
      Log.d(TAG, "Sky content changed (v$lastContentVersion -> v$currentVersion), invalidating blur cache")
      blurJob?.cancel()
      blurJob = null
      blurredBitmap = null
      isProcessing = false
    }

    // Update tracking state
    lastContentVersion = currentVersion

    // Check for cached result
    val cached = blurredBitmap

    if (cached != null && !cached.bitmap.isRecycled) {
      clipRect {
        drawImage(
          image = cached.bitmap.asImageBitmap(),
          dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
        )

        // Apply tint
        if (snapshot.tintColor != Color.Transparent) {
          drawRect(color = snapshot.tintColor, blendMode = BlendMode.SrcOver)
        }
      }

      onStateChanged(CloudyState.Success.Captured(cached))
      return
    }

    // Draw original content while processing (clipped)
    clipRect {
      drawContext.canvas.save()
      drawContext.canvas.translate(-snapshot.offsetX, -snapshot.offsetY)
      drawLayer(layer)
      drawContext.canvas.restore()

      // Apply tint even while processing
      if (snapshot.tintColor != Color.Transparent) {
        drawRect(color = snapshot.tintColor, blendMode = BlendMode.SrcOver)
      }
    }

    if (isProcessing) {
      return
    }

    // Start async blur processing
    isProcessing = true
    onStateChanged(CloudyState.Loading)

    val node = this@CloudyBackgroundModifierNode
    val capturedSnapshot = snapshot.copy()

    blurJob = coroutineScope.launch(Dispatchers.Main) {
      try {
        // Capture the background region
        val capturedBitmap: Bitmap = try {
          layer.toImageBitmap().asAndroidBitmap()
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          Log.e(TAG, "Failed to capture layer as bitmap", e)
          onStateChanged(CloudyState.Error(e))
          return@launch
        }

        Log.d(TAG, "Captured bitmap: ${capturedBitmap.width}x${capturedBitmap.height}, " +
          "offset: (${capturedSnapshot.offsetX}, ${capturedSnapshot.offsetY}), " +
          "child: ${capturedSnapshot.childWidth}x${capturedSnapshot.childHeight}")

        // Crop to child bounds with downsampling
        val scale = 0.25f
        val croppedBitmap = cropAndScale(
          capturedBitmap,
          capturedSnapshot.offsetX.toInt(),
          capturedSnapshot.offsetY.toInt(),
          capturedSnapshot.childWidth.toInt(),
          capturedSnapshot.childHeight.toInt(),
          scale,
        )

        if (croppedBitmap == null) {
          Log.e(TAG, "Failed to crop bitmap - bounds may be invalid")
          onStateChanged(CloudyState.Error(RuntimeException("Failed to crop bitmap")))
          return@launch
        }

        Log.d(TAG, "Cropped bitmap: ${croppedBitmap.width}x${croppedBitmap.height}")

        // Apply blur
        val blurResult = withContext(Dispatchers.Default) {
          val outputBitmap = Bitmap.createBitmap(
            croppedBitmap.width,
            croppedBitmap.height,
            Bitmap.Config.ARGB_8888,
          )
          val scaledRadius = (capturedSnapshot.radius * scale).toInt().coerceAtLeast(1)
          iterativeBlur(
            androidBitmap = croppedBitmap,
            outputBitmap = outputBitmap,
            radius = scaledRadius,
          ).await()
        }

        if (blurResult == null) {
          Log.e(TAG, "Blur processing returned null")
          onStateChanged(CloudyState.Error(RuntimeException("Blur processing failed")))
          return@launch
        }

        // Apply progressive mask
        val maskedResult = applyProgressiveMask(blurResult, capturedSnapshot)

        // Scale back up
        val finalBitmap = Bitmap.createScaledBitmap(
          maskedResult,
          capturedSnapshot.childWidth.toInt(),
          capturedSnapshot.childHeight.toInt(),
          true,
        )

        Log.d(TAG, "Blur complete: ${finalBitmap.width}x${finalBitmap.height}")

        if (node.isAttached) {
          blurredBitmap = PlatformBitmap(finalBitmap)
          node.invalidateDraw()
        }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Background blur failed", e)
        onStateChanged(CloudyState.Error(e))
      } finally {
        isProcessing = false
        blurJob = null
      }
    }
  }

  private fun ContentDrawScope.drawBackgroundRegionUnblurred(
    layer: GraphicsLayer,
    snapshot: SkySnapshot,
  ) {
    drawContext.canvas.save()
    drawContext.canvas.translate(-snapshot.offsetX, -snapshot.offsetY)
    drawLayer(layer)
    drawContext.canvas.restore()
  }

  private fun applyProgressiveMask(
    bitmap: Bitmap,
    snapshot: SkySnapshot,
  ): Bitmap {
    if (snapshot.direction == SkySnapshot.ProgressiveDirection.NONE) return bitmap

    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    val paint = Paint().apply {
      xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    val gradient = when (snapshot.direction) {
      SkySnapshot.ProgressiveDirection.TOP_TO_BOTTOM -> {
        LinearGradient(
          0f,
          0f,
          0f,
          bitmap.height.toFloat(),
          intArrayOf(
            android.graphics.Color.BLACK,
            android.graphics.Color.BLACK,
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
          ),
          floatArrayOf(0f, snapshot.fadeStart, snapshot.fadeEnd, 1f),
          Shader.TileMode.CLAMP,
        )
      }
      SkySnapshot.ProgressiveDirection.BOTTOM_TO_TOP -> {
        LinearGradient(
          0f,
          0f,
          0f,
          bitmap.height.toFloat(),
          intArrayOf(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.BLACK,
            android.graphics.Color.BLACK,
          ),
          floatArrayOf(0f, snapshot.fadeEnd, snapshot.fadeStart, 1f),
          Shader.TileMode.CLAMP,
        )
      }
      else -> return bitmap
    }

    paint.shader = gradient
    canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)

    return result
  }

  private fun cropAndScale(
    source: Bitmap,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    scale: Float,
  ): Bitmap? {
    return try {
      val safeX = x.coerceIn(0, source.width - 1)
      val safeY = y.coerceIn(0, source.height - 1)
      val safeWidth = width.coerceAtMost(source.width - safeX)
      val safeHeight = height.coerceAtMost(source.height - safeY)

      if (safeWidth <= 0 || safeHeight <= 0) return null

      val cropped = Bitmap.createBitmap(source, safeX, safeY, safeWidth, safeHeight)
      val scaledWidth = (safeWidth * scale).toInt().coerceAtLeast(1)
      val scaledHeight = (safeHeight * scale).toInt().coerceAtLeast(1)

      Bitmap.createScaledBitmap(cropped, scaledWidth, scaledHeight, true)
    } catch (e: Exception) {
      null
    }
  }

  override fun onDetach() {
    blurJob?.cancel()
    blurJob = null
    blurredBitmap = null
    isProcessing = false
  }
}
