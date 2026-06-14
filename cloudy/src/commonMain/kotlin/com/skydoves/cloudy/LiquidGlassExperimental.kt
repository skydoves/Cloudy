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
 * Marks motion-driven Liquid Glass APIs (e.g. `rememberGyroLightSource`) as experimental.
 *
 * These APIs read device-motion sensors and may change. Opt in with
 * `@OptIn(ExperimentalLiquidGlassMotion::class)` or by propagating the annotation.
 */
@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message = "Motion-driven Liquid Glass APIs are experimental and may change.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
public annotation class ExperimentalLiquidGlassMotion

/**
 * Experimental variant of [Modifier.liquidGlass] that exposes the **full** specular-glint tuning,
 * not just the two perceptual knobs of [LiquidGlassGlow].
 *
 * The stable public surface intentionally keeps glow tuning to [LiquidGlassGlow] (intensity +
 * sharpness). This modifier opens the remaining shader tunables — [glowRimMix] and [glowWidthPx] —
 * for live experimentation (e.g. the demo's slider screen), behind the [ExperimentalLiquidGlassMotion]
 * opt-in so they are not part of the committed API contract.
 *
 * Takes the knobs as plain floats rather than a tuning object: the full `GlowTuning` is `internal`
 * so it never leaks into the stable ABI, and the internal binding folds the floats into it, sharing
 * one uniform-writing path per platform with [Modifier.liquidGlass].
 *
 * All other parameters mirror [Modifier.liquidGlass]; see that modifier for their semantics.
 *
 * @param glowIntensity peak highlight brightness (`0..1`, screen-blended).
 * @param glowSharpness lobe sharpness; higher = one tighter glint.
 * @param glowRimMix body↔rim crossfade (0 = body sheen, 1 = rim glint).
 * @param glowWidthPx specular band thickness in pixels, decoupled from `edge`.
 *
 * @see Modifier.liquidGlass
 */
@Deprecated(
  "The open shader-recipe mechanism supersedes the loose-float tuning. " +
    "Use Modifier.liquidGlass(...) for refraction and " +
    "Modifier.mirage(MirageRecipes.Specular) for the full specular glint instead.",
  level = DeprecationLevel.WARNING,
)
@ExperimentalLiquidGlassMotion
@Composable
public expect fun Modifier.liquidGlassTuned(
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
  light: LiquidGlassLight = LiquidGlassDefaults.Light,
  glowIntensity: Float = LiquidGlassDefaults.GLOW_INTENSITY,
  glowSharpness: Float = LiquidGlassDefaults.GLOW_SHARPNESS,
  glowRimMix: Float = 0.6f,
  glowWidthPx: Float = 12.0f,
  enabled: Boolean = true,
): Modifier
