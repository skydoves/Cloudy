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

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Android implementation of [Modifier.liquidGlass].
 *
 * Uses RuntimeShader with RenderEffect on API 33+ for GPU-accelerated glass effects.
 * On older API levels, provides a fallback with saturation, contrast, tint, and edge effects.
 *
 * **Note:** For blur effects, use [Modifier.cloudy] separately.
 */
@ExperimentalLiquidGlassMaterial
@Composable
public actual fun Modifier.liquidGlass(
  lensCenter: Offset,
  lensSize: Size,
  cornerRadius: Float,
  refraction: Float,
  curve: Float,
  dispersion: Float,
  saturation: Float,
  contrast: Float,
  tint: Color,
  edge: Float,
  light: LiquidGlassLight,
  glow: LiquidGlassGlow,
  chromatic: ChromaticOverlay,
  enabled: Boolean,
): Modifier = liquidGlassImpl(
  lensCenter = lensCenter,
  lensSize = lensSize,
  cornerRadius = cornerRadius,
  refraction = refraction,
  curve = curve,
  dispersion = dispersion,
  saturation = saturation,
  contrast = contrast,
  tint = tint,
  edge = edge,
  light = light,
  // The stable public surface only exposes the two perceptual knobs; widen to the full 4-knob
  // tuning (extra knobs at their tuned defaults) for the single uniform-writing path below.
  tuning = glow.toTuning(),
  chromatic = chromatic,
  enabled = enabled,
)

@OptIn(ExperimentalLiquidGlassMaterial::class)
@ExperimentalLiquidGlassMotion
@Composable
public actual fun Modifier.liquidGlassTuned(
  lensCenter: Offset,
  lensSize: Size,
  cornerRadius: Float,
  refraction: Float,
  curve: Float,
  dispersion: Float,
  saturation: Float,
  contrast: Float,
  tint: Color,
  edge: Float,
  light: LiquidGlassLight,
  glowIntensity: Float,
  glowSharpness: Float,
  glowRimMix: Float,
  glowWidthPx: Float,
  enabled: Boolean,
): Modifier = liquidGlassImpl(
  lensCenter = lensCenter,
  lensSize = lensSize,
  cornerRadius = cornerRadius,
  refraction = refraction,
  curve = curve,
  dispersion = dispersion,
  saturation = saturation,
  contrast = contrast,
  tint = tint,
  edge = edge,
  light = light,
  tuning = GlowTuning(
    intensity = glowIntensity,
    sharpness = glowSharpness,
    rimMix = glowRimMix,
    widthPx = glowWidthPx,
  ),
  // liquidGlassTuned tunes the specular glint only; it does not expose the chromatic overlay, so
  // forward the no-op preset (the binding still writes all 6 chromatic uniforms gate-free below).
  chromatic = LiquidGlassDefaults.NoChromatic,
  enabled = enabled,
)

/**
 * Single entry point shared by [liquidGlass] and [liquidGlassTuned]. Takes the full internal
 * [GlowTuning] so there is exactly one uniform-writing code path (in [liquidGlassApi33]).
 */
@OptIn(ExperimentalLiquidGlassMaterial::class)
@Composable
private fun Modifier.liquidGlassImpl(
  lensCenter: Offset,
  lensSize: Size,
  cornerRadius: Float,
  refraction: Float,
  curve: Float,
  dispersion: Float,
  saturation: Float,
  contrast: Float,
  tint: Color,
  edge: Float,
  light: LiquidGlassLight,
  tuning: GlowTuning,
  chromatic: ChromaticOverlay,
  enabled: Boolean,
): Modifier {
  // Validation
  require(lensSize.width > 0f) { "lensSize.width must be > 0, but was ${lensSize.width}" }
  require(lensSize.height > 0f) { "lensSize.height must be > 0, but was ${lensSize.height}" }
  require(cornerRadius >= 0f) { "cornerRadius must be >= 0, but was $cornerRadius" }
  require(refraction >= 0f) { "refraction must be >= 0, but was $refraction" }
  require(curve >= 0f) { "curve must be >= 0, but was $curve" }
  require(dispersion >= 0f) { "dispersion must be >= 0, but was $dispersion" }
  require(saturation >= 0f) { "saturation must be >= 0, but was $saturation" }
  require(contrast >= 0f) { "contrast must be >= 0, but was $contrast" }
  require(edge >= 0f) { "edge must be >= 0, but was $edge" }

  if (!enabled) {
    return this
  }

  // Preview mode fallback
  if (LocalInspectionMode.current) {
    return this
  }

  // API level check - RuntimeShader requires API 33+
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    liquidGlassApi33(
      lensCenter = lensCenter,
      lensSize = lensSize,
      cornerRadius = cornerRadius,
      refraction = refraction,
      curve = curve,
      dispersion = dispersion,
      saturation = saturation,
      contrast = contrast,
      tint = tint,
      edge = edge,
      light = light,
      tuning = tuning,
      chromatic = chromatic,
    )
  } else {
    // Fallback for older Android versions
    // The fallback path has no shader, so the light/glow/chromatic tuning is intentionally not
    // forwarded (no specular/chromatic uniforms to drive).
    // Provides saturation, contrast, tint, and edge effects without lens refraction
    liquidGlassFallback(
      lensCenter = lensCenter,
      lensSize = lensSize,
      cornerRadius = cornerRadius,
      saturation = saturation,
      contrast = contrast,
      tint = tint,
      edge = edge,
    )
  }
}

@OptIn(ExperimentalLiquidGlassMaterial::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.liquidGlassApi33(
  lensCenter: Offset,
  lensSize: Size,
  cornerRadius: Float,
  refraction: Float,
  curve: Float,
  dispersion: Float,
  saturation: Float,
  contrast: Float,
  tint: Color,
  edge: Float,
  light: LiquidGlassLight,
  tuning: GlowTuning,
  chromatic: ChromaticOverlay,
): Modifier {
  // Create and cache the RuntimeShader
  val shader = remember {
    try {
      RuntimeShader(LiquidGlassShaderSource.AGSL)
    } catch (e: Exception) {
      Log.w("LiquidGlass", "RuntimeShader compilation failed", e)
      null
    }
  }

  // If shader failed to compile, return unchanged
  if (shader == null) {
    return this
  }

  return this.graphicsLayer {
    val width = size.width
    val height = size.height

    if (width > 0 && height > 0) {
      // Update shader uniforms
      shader.setFloatUniform("resolution", width, height)
      shader.setFloatUniform("lensCenter", lensCenter.x, lensCenter.y)
      shader.setFloatUniform("lensSize", lensSize.width, lensSize.height)
      shader.setFloatUniform("cornerRadius", cornerRadius)
      shader.setFloatUniform("refraction", refraction)
      shader.setFloatUniform("curve", curve)
      shader.setFloatUniform("dispersion", dispersion)
      shader.setFloatUniform("saturation", saturation)
      shader.setFloatUniform("contrast", contrast)
      shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
      shader.setFloatUniform("edge", edge)
      // Draw-phase read: only the value changes per tick (holder identity is stable), so a
      // high-frequency light source invalidates the draw without recomposing. Always set
      // (never gate) — the shader requires every declared uniform.
      val lightDir = light.direction.value
      shader.setFloatUniform("lightDir", lightDir.x, lightDir.y)
      // Specular glint tuning — every declared uniform must be set on every draw (gate-free),
      // right alongside lightDir.
      shader.setFloatUniform("specStrength", tuning.intensity)
      shader.setFloatUniform("specPower", tuning.sharpness)
      shader.setFloatUniform("specRimMix", tuning.rimMix)
      shader.setFloatUniform("specWidthPx", tuning.widthPx)
      // Every declared uniform must be set each draw, or AGSL reads garbage (0).
      shader.setFloatUniform("specLightZ", tuning.lightZ)
      shader.setFloatUniform("specDomeFrac", tuning.domeFrac)
      shader.setFloatUniform("specBodyPower", tuning.bodyPower)
      shader.setFloatUniform("specBodyGain", tuning.bodyGain)
      // Moving focal hotspot (dual-axis) — same gate-free contract.
      shader.setFloatUniform("specFocalK", tuning.focalK)
      shader.setFloatUniform("specPoolFrac", tuning.poolFrac)
      shader.setFloatUniform("specPoolGain", tuning.poolGain)
      // Chromatic overlay — all 6 declared chromatic uniforms must be set every draw (gate-free);
      // an unset AGSL uniform reads garbage. intensity == 0 keeps the term bit-exact off in-shader.
      // Only intensity + mode come from the public ChromaticOverlay; the remaining 4 are held at the
      // shader's tuned defaults (bands/cycles/phase/modulate) as constants here.
      shader.setFloatUniform("chromaticIntensity", chromatic.intensity)
      // Exhaustive when (no else) so adding a ChromaticMode without a float breaks the build here.
      val chromaticModeF = when (chromatic.mode) {
        ChromaticMode.Iridescent -> 0f
        ChromaticMode.Foil -> 1f
        ChromaticMode.OilSlick -> 2f
        ChromaticMode.SoapBubble -> 3f
        ChromaticMode.MetallicFoil -> 4f
        ChromaticMode.Pearl -> 5f
      }
      shader.setFloatUniform("chromaticMode", chromaticModeF)
      shader.setFloatUniform("chromaticBands", 3f)
      shader.setFloatUniform("chromaticCycles", 1.5f)
      shader.setFloatUniform("chromaticPhase", 0f)
      shader.setFloatUniform("chromaticModulate", 0.3f)

      // Apply shader as RenderEffect - "content" binds to underlying layer
      renderEffect = RenderEffect
        .createRuntimeShaderEffect(shader, "content")
        .asComposeRenderEffect()
    }
  }
}

/**
 * Fallback implementation for Android < 33.
 *
 * Provides a graceful degradation with saturation, contrast, tint, and edge effects.
 * The lens refraction/distortion effect is not available without RuntimeShader (API 33+).
 *
 * For blur effects, use [Modifier.cloudy] separately.
 */
@Composable
private fun Modifier.liquidGlassFallback(
  lensCenter: Offset,
  lensSize: Size,
  cornerRadius: Float,
  saturation: Float,
  contrast: Float,
  tint: Color,
  edge: Float,
): Modifier = this.drawWithContent {
  // Draw original content first
  drawContent()

  // Calculate lens bounds centered on lens center position
  val halfWidth = lensSize.width / 2f
  val halfHeight = lensSize.height / 2f
  val lensLeft = lensCenter.x - halfWidth
  val lensTop = lensCenter.y - halfHeight
  val clampedCornerRadius = cornerRadius.coerceAtMost(minOf(halfWidth, halfHeight))

  // Create lens path
  val lensPath = Path().apply {
    addRoundRect(
      RoundRect(
        left = lensLeft,
        top = lensTop,
        right = lensLeft + lensSize.width,
        bottom = lensTop + lensSize.height,
        cornerRadius = CornerRadius(clampedCornerRadius, clampedCornerRadius),
      ),
    )
  }

  // Draw color-adjusted overlay inside the lens shape
  if (saturation != 1f || contrast != 1f) {
    clipPath(lensPath) {
      // Draw a semi-transparent overlay that simulates the color adjustment
      // This is a simplified approximation since we can't re-render content with a filter
      // Use absolute deviation from 1.0 to handle both directions (over/under saturation/contrast)
      val saturationDelta = kotlin.math.abs(1f - saturation).coerceIn(0f, 1f)
      val contrastDelta = kotlin.math.abs(1f - contrast).coerceIn(0f, 1f)
      val overlayAlpha = 0.3f * (saturationDelta + contrastDelta)
      if (overlayAlpha > 0f) {
        drawRect(
          color = Color.Gray.copy(alpha = overlayAlpha.coerceIn(0f, 0.5f)),
          topLeft = Offset(lensLeft, lensTop),
          size = lensSize,
        )
      }
    }
  }

  // Draw tint overlay inside the lens
  if (tint != Color.Transparent && tint.alpha > 0f) {
    clipPath(lensPath) {
      drawRect(
        color = tint,
        topLeft = Offset(lensLeft, lensTop),
        size = lensSize,
      )
    }
  }

  // Draw edge lighting effect
  if (edge > 0f) {
    val strokeWidth = edge * 20f // Scale edge value to reasonable stroke width
    val edgeColor = Color.White.copy(alpha = (edge * 0.5f).coerceIn(0f, 0.8f))

    drawPath(
      path = lensPath,
      color = edgeColor,
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
    )
  }
}
