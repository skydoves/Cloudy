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

/**
 * Default values for the Liquid Glass effect.
 */
public object LiquidGlassDefaults {
  /** Default lens size in pixels. */
  public val LENS_SIZE: Size = Size(350f, 350f)

  /** Default corner radius for the lens shape. */
  public const val CORNER_RADIUS: Float = 32f

  /** Default refraction strength. 0.0 = no refraction, higher = more distortion. */
  public const val REFRACTION: Float = 0.5f

  /** Default blur radius in pixels. */
  public const val BLUR: Float = 8f

  /** Default chromatic aberration strength. 0.0 = none, higher = more color separation. */
  public const val ABERRATION: Float = 0.5f

  /** Default saturation. 1.0 = normal, <1.0 = desaturated, >1.0 = oversaturated. */
  public const val SATURATION: Float = 1.0f

  /** Default edge brightness/lighting intensity. */
  public const val EDGE_BRIGHTNESS: Float = 0.3f

  /** Minimum Android API level required for the liquid glass effect. */
  public const val MIN_ANDROID_API: Int = 33
}

/**
 * Applies a cross-platform Liquid Glass effect to the content.
 *
 * This modifier creates an interactive glass lens effect that distorts the dynamic content
 * beneath it in real-time. The effect uses SDF (Signed Distance Field) for crisp edges,
 * normal-based refraction, frosted blur, and chromatic aberration.
 *
 * ## Platform Behavior
 *
 * | Platform | Implementation |
 * |----------|----------------|
 * | Android 33+ | RenderEffect with RuntimeShader (AGSL) |
 * | Android 32- | No-op fallback (returns content unchanged) |
 * | iOS/macOS/Desktop | ImageFilter with RuntimeEffect (SKSL via Skia) |
 *
 * ## Example Usage
 *
 * ```kotlin
 * var mousePosition by remember { mutableStateOf(Offset(100f, 100f)) }
 *
 * Box(
 *   modifier = Modifier
 *     .fillMaxSize()
 *     .pointerInput(Unit) {
 *       detectDragGestures { change, _ ->
 *         mousePosition = change.position
 *       }
 *     }
 *     .liquidGlass(
 *       mousePosition = mousePosition,
 *       lensSize = Size(200f, 200f),
 *       cornerRadius = 32f,
 *       refraction = 0.5f,
 *       blur = 8f,
 *     )
 * ) {
 *   // Your dynamic content here (images, videos, animations)
 *   Image(painter = painterResource(R.drawable.photo), ...)
 * }
 * ```
 *
 * @param mousePosition The current position of the glass lens center in pixels.
 *   This should be updated based on touch/mouse input for interactive effects.
 *
 * @param lensSize The size of the lens in pixels (width, height).
 *   Default: [LiquidGlassDefaults.LENS_SIZE] (200x200).
 *
 * @param cornerRadius The corner radius of the rounded rectangle lens shape.
 *   Default: [LiquidGlassDefaults.CORNER_RADIUS] (32).
 *
 * @param refraction The refraction/distortion strength.
 *   Controls how much the content is displaced through the lens.
 *   Default: [LiquidGlassDefaults.REFRACTION] (0.5).
 *
 * @param blur The blur radius for the frosted glass effect.
 *   Default: [LiquidGlassDefaults.BLUR] (8).
 *
 * @param aberration The chromatic aberration intensity.
 *   Controls the RGB channel separation that creates the "rainbow fringe" effect.
 *   Default: [LiquidGlassDefaults.ABERRATION] (0.5).
 *
 * @param saturation Color saturation adjustment. 1.0 = normal.
 *   Default: [LiquidGlassDefaults.SATURATION] (1.0).
 *
 * @param edgeBrightness The edge lighting/highlight intensity.
 *   Default: [LiquidGlassDefaults.EDGE_BRIGHTNESS] (0.3).
 *
 * @param enabled If false, disables the effect and returns the original modifier.
 *
 * @return A [Modifier] with the Liquid Glass effect applied.
 *
 * @see LiquidGlassDefaults
 * @see LiquidGlassShaderSource
 */
@Composable
public expect fun Modifier.liquidGlass(
  mousePosition: Offset,
  lensSize: Size = LiquidGlassDefaults.LENS_SIZE,
  cornerRadius: Float = LiquidGlassDefaults.CORNER_RADIUS,
  refraction: Float = LiquidGlassDefaults.REFRACTION,
  blur: Float = LiquidGlassDefaults.BLUR,
  aberration: Float = LiquidGlassDefaults.ABERRATION,
  saturation: Float = LiquidGlassDefaults.SATURATION,
  edgeBrightness: Float = LiquidGlassDefaults.EDGE_BRIGHTNESS,
  enabled: Boolean = true,
): Modifier
