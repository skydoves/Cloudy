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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.sin

/**
 * Creates a transform-driven [LiquidGlassLight] whose direction tracks the **target composable's own
 * 3D rotation** (the `rotationX` / `rotationY` it is drawn with via `Modifier.graphicsLayer`), so the
 * Liquid Glass specular highlight responds to the surface tilting in space — no gyroscope, no sensor,
 * no platform code. Unlike `rememberGyroLightSource`, this is fully deterministic and runs on every
 * target including Desktop and Web.
 *
 * ## Ownership (the caller owns the `graphicsLayer`)
 * This factory does **not** rotate anything. The caller applies the rotation on the target itself:
 *
 * ```kotlin
 * var rx by remember { mutableFloatStateOf(0f) }
 * var ry by remember { mutableFloatStateOf(0f) }
 * val light = rememberTransformLightSource(rotationX = { rx }, rotationY = { ry })
 * Box(
 *   Modifier
 *     .graphicsLayer { rotationX = rx; rotationY = ry; cameraDistance = 12f * density }
 *     .liquidGlass(light = light, /* … */),
 * )
 * ```
 *
 * The same `rx` / `ry` that drive the layer drive the light, so the highlight stays consistent with
 * the visible tilt. This keeps the holder identity stable (only its value changes), so a per-frame
 * rotation update invalidates the **draw** without recomposing the modifier — the same guarantee the
 * gyro source gives.
 *
 * ## Sign convention (screen space, y-down)
 * - **`rotationY`** (yaw about the screen X axis... i.e. about the vertical screen-Y axis): positive
 *   `rotationY` slides the hotspot toward **+x** (screen-right).
 * - **`rotationX`** (pitch about the horizontal screen-X axis): positive `rotationX` slides the
 *   hotspot toward **-y** (screen-up, because screen space is y-down).
 *
 * A flat surface (`rotationX == 0 && rotationY == 0`) yields the normalized [base] exactly — the same
 * resting invariant as the gyro projection.
 *
 * @param rotationX Deferred read of the target's current `rotationX` in **degrees** (pitch). Passed
 *   as a lambda so per-frame updates re-emit through `snapshotFlow` without recomposing this factory.
 * @param rotationY Deferred read of the target's current `rotationY` in **degrees** (yaw).
 * @param base The resting light direction used when the surface is flat. Defaults to
 *   [LiquidGlassDefaults.LIGHT_DIR]. Normalized internally, so magnitude is irrelevant.
 * @param gain How strongly rotation displaces the light from [base]. Higher = more sweep.
 * @return A [LiquidGlassLight] suitable for [Modifier.liquidGlass]'s / [Modifier.liquidGlassTuned]'s
 *   `light` parameter.
 */
@ExperimentalLiquidGlassMotion
@Composable
public fun rememberTransformLightSource(
  rotationX: () -> Float,
  rotationY: () -> Float,
  base: Offset = LiquidGlassDefaults.LIGHT_DIR,
  gain: Float = 1.2f,
): LiquidGlassLight {
  // Keep the latest lambdas without re-keying the LaunchedEffect: a caller passing a non-State-backed
  // lambda (recreated each recomposition) would otherwise leave the effect holding a stale lambda and
  // silently freeze. rememberUpdatedState swaps in the newest lambda while the collect keeps running.
  val rx by rememberUpdatedState(rotationX)
  val ry by rememberUpdatedState(rotationY)
  val dir = remember { mutableStateOf(transformToLight(rotationX(), rotationY(), base, gain)) }
  // Deferred reads inside snapshotFlow: per-frame rotation changes re-emit and update the holder
  // value (draw invalidation) without recomposing this factory. Re-keyed only on base/gain.
  LaunchedEffect(base, gain) {
    snapshotFlow { transformToLight(rx(), ry(), base, gain) }
      .collect { dir.value = it }
  }
  // Stable holder identity; only dir.value changes per frame.
  return remember(dir) { LiquidGlassLight(dir) }
}

// ---------------------------------------------------------------------------------------------
// Pure math — platform-independent so it is unit-testable (commonTest) and free of Compose state.
// ---------------------------------------------------------------------------------------------

/** Degrees → radians as a single-precision factor (mirrors the gyro math's float-only style). */
private val DEG_TO_RAD: Float = (PI / 180.0).toFloat()

/**
 * Projects the target's 3D rotation (in degrees) onto a screen-space light direction (y-down).
 *
 * A flat surface (`rotationXDeg == 0 && rotationYDeg == 0`) returns the normalized [base] exactly,
 * the same invariant as `gravityToLight(0, 0, …)`. Otherwise the in-plane displacement is the sine
 * of each rotation, scaled by [gain]:
 * - `+rotationY` → hotspot slides toward **+x** (screen-right).
 * - `+rotationX` → hotspot slides toward **-y** (screen-up; screen space is y-down).
 *
 * [base] is normalized first so the sweep magnitude is consistent regardless of base length.
 *
 * @param rotationXDeg pitch about the screen-X axis, in degrees.
 * @param rotationYDeg yaw about the screen-Y axis, in degrees.
 */
internal fun transformToLight(
  rotationXDeg: Float,
  rotationYDeg: Float,
  base: Offset,
  gain: Float,
): Offset {
  val b = normalizeOr(base, Offset(-1f, -1f))
  val rx = rotationXDeg * DEG_TO_RAD // pitch about screen-X
  val ry = rotationYDeg * DEG_TO_RAD // yaw about screen-Y
  return Offset(
    b.x + gain * sin(ry), // +rotationY -> hotspot slides +x (right)
    b.y - gain * sin(rx), // +rotationX -> hotspot slides -y (up; screen y-down)
  )
}
