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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Default values for the Liquid Glass effect.
 */
public object LiquidGlassDefaults {
  /** Default lens size in pixels. */
  public val LENS_SIZE: Size = Size(350f, 350f)

  /** Default corner radius for the lens shape. */
  public const val CORNER_RADIUS: Float = 50f

  /** Default refraction strength. Controls how much the background distorts. */
  public const val REFRACTION: Float = 0.25f

  /** Default curve strength. Controls how strongly the lens curves at center vs edges. */
  public const val CURVE: Float = 0.25f

  /** Default dispersion (chromatic aberration) strength. */
  public const val DISPERSION: Float = 0.0f

  /** Default saturation. 1.0 = normal, <1.0 = desaturated, >1.0 = oversaturated. */
  public const val SATURATION: Float = 1.0f

  /** Default contrast. 1.0 = normal, <1.0 = less contrast, >1.0 = more contrast. */
  public const val CONTRAST: Float = 1.0f

  /** Default tint color (transparent = no tint). */
  public val TINT: Color = Color.Transparent

  /** Default edge lighting width. 0.0 = no edge, higher = wider edge lighting. */
  public const val EDGE: Float = 0.2f

  /** Minimum Android API level required for the full liquid glass effect. */
  public const val MIN_ANDROID_API_FULL: Int = 33

  /** Minimum Android API level for fallback support. */
  public const val MIN_ANDROID_API_FALLBACK: Int = 23
}

/**
 * Applies a cross-platform Liquid Glass effect to the content.
 *
 * This modifier creates an interactive glass lens effect that distorts the dynamic content
 * beneath it in real-time. The effect uses SDF (Signed Distance Field) for crisp edges,
 * normal-based refraction, and chromatic dispersion.
 *
 * **Note:** For blur effects, use [Modifier.cloudy] separately. This modifier focuses on
 * the lens distortion effect and can be combined with Cloudy's blur for a complete
 * frosted glass look.
 *
 * ## Platform Behavior
 *
 * | Platform | Implementation | Features |
 * |----------|----------------|----------|
 * | Android API 33+ | RuntimeShader (AGSL) | Full effect |
 * | Android API 23-32 | Fallback | Saturation + edge (no refraction/dispersion) |
 * | iOS/macOS/Desktop | Skia RuntimeEffect (SKSL) | Full effect |
 *
 * ## Example Usage
 *
 * ```kotlin
 * var lensCenter by remember { mutableStateOf(Offset(100f, 100f)) }
 *
 * Box(
 *   modifier = Modifier
 *     .fillMaxSize()
 *     .pointerInput(Unit) {
 *       detectDragGestures { change, _ ->
 *         lensCenter = change.position
 *       }
 *     }
 *     .cloudy(radius = 15) // Use Cloudy for blur
 *     .liquidGlass(
 *       lensCenter = lensCenter,
 *       lensSize = Size(350f, 350f),
 *       cornerRadius = 50f,
 *       refraction = 0.25f,
 *       curve = 0.25f,
 *     )
 * ) {
 *   Image(painter = painterResource(R.drawable.photo), ...)
 * }
 * ```
 *
 * @param lensCenter The current position of the glass lens center in pixels.
 *   This should be updated based on touch/pointer input for interactive effects.
 *
 * @param lensSize The size of the lens in pixels (width, height).
 *   Default: [LiquidGlassDefaults.LENS_SIZE] (350x350).
 *
 * @param cornerRadius The corner radius of the rounded rectangle lens shape.
 *   Use higher values for more rounded corners, or set to half of lensSize for circular.
 *   Default: [LiquidGlassDefaults.CORNER_RADIUS] (50).
 *
 * @param refraction Controls how much the background distorts through the liquid lens.
 *   Setting to 0 removes the liquid effect. No-op on Android API < 33.
 *   Default: [LiquidGlassDefaults.REFRACTION] (0.25).
 *
 * @param curve Controls how strongly the liquid lens curves at its center vs edges.
 *   Setting to 0 removes the liquid effect. No-op on Android API < 33.
 *   Default: [LiquidGlassDefaults.CURVE] (0.25).
 *
 * @param dispersion The chromatic dispersion (aberration) intensity.
 *   Controls the RGB channel separation that creates the prism-like effect.
 *   No-op on Android API < 33.
 *   Default: [LiquidGlassDefaults.DISPERSION] (0.0).
 *
 * @param saturation Color saturation adjustment. 1.0 = normal.
 *   Works on all platforms including fallback.
 *   Default: [LiquidGlassDefaults.SATURATION] (1.0).
 *
 * @param contrast Adjusts the difference between light and dark areas.
 *   1.0 = normal, >1.0 = more contrast, <1.0 = less contrast.
 *   Default: [LiquidGlassDefaults.CONTRAST] (1.0).
 *
 * @param tint Optional color tint to apply over the glass effect.
 *   Use Color.Transparent for no tint.
 *   Default: [LiquidGlassDefaults.TINT] (Transparent).
 *
 * @param edge The edge lighting/rim width. Higher values create wider, softer edges.
 *   Set to 0 to disable edge lighting. On Android API < 33, this becomes
 *   a boolean where value > 0 draws a fixed width edge effect.
 *   Default: [LiquidGlassDefaults.EDGE] (0.2).
 *
 * @param enabled If false, disables the effect and returns the original modifier.
 *
 * @return A [Modifier] with the Liquid Glass effect applied.
 *
 * @see LiquidGlassDefaults
 * @see LiquidGlassShaderSource
 * @see cloudy
 */
@Composable
public expect fun Modifier.liquidGlass(
  lensCenter: Offset,
  lensSize: Size = LiquidGlassDefaults.LENS_SIZE,
  cornerRadius: Float = LiquidGlassDefaults.CORNER_RADIUS,
  refraction: Float = LiquidGlassDefaults.REFRACTION,
  curve: Float = LiquidGlassDefaults.CURVE,
  dispersion: Float = LiquidGlassDefaults.DISPERSION,
  saturation: Float = LiquidGlassDefaults.SATURATION,
  contrast: Float = LiquidGlassDefaults.CONTRAST,
  tint: Color = LiquidGlassDefaults.TINT,
  edge: Float = LiquidGlassDefaults.EDGE,
  enabled: Boolean = true,
): Modifier
