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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

/**
 * Shared tuning for the background-blur specular highlight drawn by [Modifier.cloudy] when a
 * [LiquidGlassLight] is supplied. These mirror the focal-pool terms of the AGSL/SKSL Liquid Glass
 * shader (`LiquidGlassShaderSource`) so the Compose-side approximation lines up with the shader look,
 * but are kept here (not in the shader's `GlowTuning`) because the cloudy highlight is a flat radial
 * gradient composited over an already-blurred backdrop, not a per-pixel lens evaluation.
 *
 * Internal: both platform bindings ([CloudyBackground.android] / [CloudyBackground.skiko]) read these.
 */

/** Moving-hotspot offset toward the light, as a fraction of the min lens dimension. Mirrors `GlowTuning.focalK`. */
internal const val HIGHLIGHT_FOCAL_K: Float = 0.55f

/** Focal-pool radius as a fraction of the min lens dimension. Mirrors `GlowTuning.poolFrac`. */
internal const val HIGHLIGHT_POOL_FRAC: Float = 0.70f

/**
 * Warm white used for the highlight. The shader's specular is a near-white screen-blended term;
 * a slight warm bias reads as a physically-lit glint rather than a flat white wash.
 */
internal val HIGHLIGHT_WARM: Color = Color(0xFFFFF7E6)

/**
 * Radial alpha falloff approximating the shader's `smoothstep(poolR, 0, d)^2 * 0.5` focal pool
 * (peak ~0.5 at the center, fading to 0 at the rim). Six stops keep the ramp smooth enough to avoid
 * banding while staying a constant (zero per-frame allocation — only the [androidx.compose.ui.graphics.Brush]
 * built from it is rebuilt when the center/radius move). The peak matches the shader's perceived
 * specular punch (`specStrength * specPoolGain`) over a mid-tone backdrop; over bright/warm content the
 * SrcOver pool self-attenuates, so it never blows out.
 */
internal val HIGHLIGHT_STOPS: Array<Pair<Float, Color>> = arrayOf(
  0.00f to HIGHLIGHT_WARM.copy(alpha = 0.50f),
  0.15f to HIGHLIGHT_WARM.copy(alpha = 0.44f),
  0.35f to HIGHLIGHT_WARM.copy(alpha = 0.258f),
  0.55f to HIGHLIGHT_WARM.copy(alpha = 0.09f),
  0.75f to HIGHLIGHT_WARM.copy(alpha = 0.012f),
  1.00f to HIGHLIGHT_WARM.copy(alpha = 0.00f),
)

// ---------------------------------------------------------------------------------------------
// Pure geometry — platform-independent so it is unit-testable (commonTest) and is the exact code
// the platform draw helpers call (no duplicated math). Mirrors the shader's focal-pool placement.
// ---------------------------------------------------------------------------------------------

/**
 * Screen-space center of the focal-pool highlight: the lens center pushed toward the (normalized)
 * light [dir] by `minDim * focalK`. This is what makes the highlight pour across the face on both
 * axes as the surface tilts. [dir] is normalized first (via [normalizeOr], falling back to
 * [LiquidGlassDefaults.LIGHT_DIR]) so magnitude is irrelevant. Screen space is y-down.
 *
 * @param size the lens (child) size in pixels.
 * @param dir the screen-space light direction (y-down); any magnitude.
 * @param focalK offset toward the light as a fraction of the min lens dimension.
 */
internal fun highlightPoolCenter(size: IntSize, dir: Offset, focalK: Float): Offset =
  Offset(size.width / 2f, size.height / 2f) +
    normalizeOr(dir, LiquidGlassDefaults.LIGHT_DIR) * (minOf(size.width, size.height).toFloat() * focalK)

/**
 * Radius of the focal-pool highlight: `minDim * poolFrac`, clamped to at least `1f` to mirror the
 * shader's `max(minHalf * specPoolFrac, 1.0)` zero-width guard (a zero radius gives an undefined
 * radial gradient).
 *
 * @param size the lens (child) size in pixels.
 * @param poolFrac pool radius as a fraction of the min lens dimension.
 */
internal fun highlightPoolRadius(size: IntSize, poolFrac: Float): Float =
  maxOf(minOf(size.width, size.height).toFloat() * poolFrac, 1f)
