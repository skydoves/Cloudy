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
import com.skydoves.cloudy.internals.render.RenderScriptToolkit
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

  // Legacy blur state (API < 31)
  private var blurredBitmap: PlatformBitmap? = null
  private var cachedContentVersion: Long = -1L
  private var isProcessing: Boolean = false
  private var blurJob: Job? = null
  private var pendingContentVersion: Long = -1L

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
      cachedContentVersion = -1L
      pendingContentVersion = -1L
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

    // Strategy pattern based on API level:
    // - API 31+ (S): GPU-accelerated RenderEffect (sync, fast)
    // - API < 31: CPU-based bitmap blur (async, slower)
    when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        // GPU blur - progressive blur not supported on API 31-32
        drawWithRenderEffect(backgroundLayer, snapshot)
      }
      else -> {
        // CPU blur - supports progressive blur
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

  @RequiresApi(Build.VERSION_CODES.S)
  private fun ContentDrawScope.drawWithRenderEffect(
    layer: GraphicsLayer,
    snapshot: SkySnapshot,
  ) {
    // Log warning for progressive blur on API 31-32 (not supported with RenderEffect)
    if (snapshot.direction != SkySnapshot.ProgressiveDirection.NONE &&
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    ) {
      // Only log once per session to avoid spam
      logProgressiveBlurWarningOnce()
    }

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

  private var hasLoggedProgressiveWarning = false

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

  private fun ContentDrawScope.drawWithBitmap(
    layer: GraphicsLayer,
    snapshot: SkySnapshot,
  ) {
    val currentVersion = sky.contentVersion
    val cached = blurredBitmap
    val cacheValid = cached != null &&
      !cached.bitmap.isRecycled &&
      cachedContentVersion == currentVersion

    // Draw cached blur if valid
    if (cacheValid && cached != null) {
      clipRect {
        drawImage(
          image = cached.bitmap.asImageBitmap(),
          dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
        )
        if (snapshot.tintColor != Color.Transparent) {
          drawRect(color = snapshot.tintColor, blendMode = BlendMode.SrcOver)
        }
      }
      onStateChanged(CloudyState.Success.Captured(cached))
      return
    }

    // Draw stale cache or original content while processing
    if (cached != null && !cached.bitmap.isRecycled) {
      // Show stale blur to avoid flickering during scroll
      clipRect {
        drawImage(
          image = cached.bitmap.asImageBitmap(),
          dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
        )
        if (snapshot.tintColor != Color.Transparent) {
          drawRect(color = snapshot.tintColor, blendMode = BlendMode.SrcOver)
        }
      }
    } else {
      // No cache at all - show original content
      clipRect {
        drawContext.canvas.save()
        drawContext.canvas.translate(-snapshot.offsetX, -snapshot.offsetY)
        drawLayer(layer)
        drawContext.canvas.restore()
        if (snapshot.tintColor != Color.Transparent) {
          drawRect(color = snapshot.tintColor, blendMode = BlendMode.SrcOver)
        }
      }
    }

    // Check if we need to start/restart blur processing
    val needsNewBlur = currentVersion != cachedContentVersion &&
      currentVersion != pendingContentVersion

    if (!needsNewBlur) {
      return
    }

    // Cancel existing job if content changed
    if (isProcessing) {
      blurJob?.cancel()
      blurJob = null
      isProcessing = false
    }

    // Start async blur processing
    isProcessing = true
    pendingContentVersion = currentVersion
    onStateChanged(CloudyState.Loading)

    val node = this@CloudyBackgroundModifierNode
    val capturedSnapshot = snapshot.copy()
    val processingVersion = currentVersion
    val outputWidth = capturedSnapshot.childWidth.toInt()
    val outputHeight = capturedSnapshot.childHeight.toInt()

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

        // Convert HARDWARE bitmap to software bitmap if needed
        val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
          capturedBitmap.config == Bitmap.Config.HARDWARE
        ) {
          try {
            capturedBitmap.copy(Bitmap.Config.ARGB_8888, true)
          } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to convert HARDWARE bitmap", e)
            onStateChanged(CloudyState.Error(e))
            return@launch
          }
        } else {
          capturedBitmap
        }

        if (softwareBitmap == null) {
          onStateChanged(CloudyState.Error(RuntimeException("Failed to create software bitmap")))
          return@launch
        }

        Log.d(TAG, "Captured bitmap: ${softwareBitmap.width}x${softwareBitmap.height}")

        // Create output bitmap for native pipeline
        val outputBitmap = Bitmap.createBitmap(
          outputWidth,
          outputHeight,
          Bitmap.Config.ARGB_8888,
        )

        // Map progressive direction
        val progressiveDir = when (capturedSnapshot.direction) {
          SkySnapshot.ProgressiveDirection.TOP_TO_BOTTOM ->
            RenderScriptToolkit.ProgressiveDirection.TOP_TO_BOTTOM
          SkySnapshot.ProgressiveDirection.BOTTOM_TO_TOP ->
            RenderScriptToolkit.ProgressiveDirection.BOTTOM_TO_TOP
          else -> RenderScriptToolkit.ProgressiveDirection.NONE
        }

        // Run native background blur pipeline (crop -> scale down -> blur -> mask -> scale up)
        val success = withContext(Dispatchers.Default) {
          RenderScriptToolkit.backgroundBlur(
            srcBitmap = softwareBitmap,
            dstBitmap = outputBitmap,
            cropX = capturedSnapshot.offsetX.toInt().coerceAtLeast(0),
            cropY = capturedSnapshot.offsetY.toInt().coerceAtLeast(0),
            radius = capturedSnapshot.radius.coerceIn(1, 25),
            scale = 0.25f,
            progressiveDirection = progressiveDir,
            fadeStart = capturedSnapshot.fadeStart,
            fadeEnd = capturedSnapshot.fadeEnd,
          )
        }

        if (!success) {
          Log.e(TAG, "Native background blur failed")
          onStateChanged(CloudyState.Error(RuntimeException("Native blur processing failed")))
          return@launch
        }

        Log.d(TAG, "Blur complete: ${outputBitmap.width}x${outputBitmap.height}")

        if (node.isAttached) {
          blurredBitmap = PlatformBitmap(outputBitmap)
          cachedContentVersion = processingVersion
          onStateChanged(CloudyState.Success.Captured(blurredBitmap!!))
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

  override fun onDetach() {
    blurJob?.cancel()
    blurJob = null
    blurredBitmap = null
    isProcessing = false
    cachedContentVersion = -1L
    pendingContentVersion = -1L
  }
}
