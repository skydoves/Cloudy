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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

/**
 * Creates a motion-driven [LiquidGlassLight] whose direction tracks the device tilt, so the
 * Liquid Glass rim specular sweeps as the phone is tilted (à la iOS 26 "lights move in space").
 *
 * Pass the result to [Modifier.liquidGlass]'s `light` parameter. The returned holder has a
 * stable identity; only its value changes (throttled, smoothed), so it invalidates the draw
 * without recomposing the modifier.
 *
 * ## Behavior & fallbacks
 * - **Android API < 33**: no-op (the Liquid Glass fallback has no shader). The light stays at
 *   [base] and no sensors are registered.
 * - **No motion sensor / Desktop / Web**: no-op; light stays at [base].
 * - **Reduce Motion** (Android `ANIMATOR_DURATION_SCALE == 0`, iOS `isReduceMotionEnabled`):
 *   the light is frozen at [base] and sensors are not started; observed live so toggling the
 *   setting while on screen takes effect.
 *
 * ## Usage in lists
 * Hoist this **once** above a list and share the returned [LiquidGlassLight] across items — one
 * call registers one sensor listener. Calling it per item registers one listener per item.
 *
 * ## iOS
 * The consuming app's `Info.plist` **must** declare `NSMotionUsageDescription`, or recent iOS
 * terminates the app on the first device-motion read. v1 uses a portrait-oriented projection;
 * landscape on iOS is not yet remapped.
 *
 * @param enabled When false, the light is frozen at [base] and no sensors are registered.
 * @param hz Target emit rate (Hz). The value is throttled to this rate regardless of the raw
 *   sensor rate.
 * @param base The resting light direction used when flat / disabled / unsupported. Defaults to
 *   [LiquidGlassDefaults.LIGHT_DIR].
 * @param tiltGain How strongly tilt displaces the light from [base]. Higher = more sweep.
 */
@ExperimentalLiquidGlassMotion
@Composable
public fun rememberGyroLightSource(
  enabled: Boolean = true,
  hz: Int = 30,
  base: Offset = LiquidGlassDefaults.LIGHT_DIR,
  tiltGain: Float = 1.2f,
): LiquidGlassLight {
  val dirState = remember { mutableStateOf(base) }
  // Platform actual owns sensor registration/teardown, throttling, smoothing and gating.
  rememberTiltSource(enabled = enabled, hz = hz, base = base, tiltGain = tiltGain, out = dirState)
  // Stable holder identity; only dirState.value changes per tick.
  return remember(dirState) { LiquidGlassLight(dirState) }
}

/**
 * Platform sensor binding. Writes a smoothed, throttled screen-space light direction into [out].
 * Implemented with a real provider on Android/iOS and as a no-op elsewhere.
 */
@Composable
internal expect fun rememberTiltSource(
  enabled: Boolean,
  hz: Int,
  base: Offset,
  tiltGain: Float,
  out: MutableState<Offset>,
)

// Pure math — platform-independent so it is unit-testable (commonTest) and reused by actuals.

/** One step of a 1-pole exponential moving average toward [raw]. [alpha] in (0,1]. */
internal fun emaStep(prev: Offset, raw: Offset, alpha: Float): Offset = prev + (raw - prev) * alpha

/**
 * Projects a device gravity vector onto a screen-space light direction.
 *
 * A flat device (gx≈0, gy≈0) yields [base]; tilting displaces the light by `tiltGain` scaled
 * by the in-plane gravity components (normalized by ~g). [base] is normalized first so the
 * sweep magnitude is consistent regardless of base length.
 *
 * @param gx in-plane gravity x (screen axes, after any display remap), m/s²
 * @param gy in-plane gravity y, m/s²
 */
internal fun gravityToLight(gx: Float, gy: Float, base: Offset, tiltGain: Float): Offset {
  val b = normalizeOr(base, Offset(-1f, -1f))
  // ~9.81 m/s²; divide so a full 90° tilt maps to ~tiltGain of displacement.
  val nx = (-gx) / 9.81f
  val ny = (-gy) / 9.81f
  return Offset(b.x + tiltGain * nx, b.y + tiltGain * ny)
}

/** Returns the unit vector of [o], or [fallback] if [o] is ~zero. */
internal fun normalizeOr(o: Offset, fallback: Offset): Offset {
  val len = sqrt(o.x * o.x + o.y * o.y)
  return if (len < 1e-4f) fallback else Offset(o.x / len, o.y / len)
}

/**
 * Projects a gravity vector in **G units** (~[-1, 1], as CoreMotion reports) onto a portrait
 * screen-space light direction (y-down).
 *
 * Unlike [gravityToLight] (m/s², divides by ~9.81), the input here is already in G and used
 * directly; reusing [gravityToLight] would make the sweep ~10x too weak. Used by the iOS provider.
 *
 * Device axes: +x = right edge, +y = top edge (y-up); `dy = -gy` flips to screen y-down. A flat
 * device (gx≈gy≈0) returns the normalized [base] exactly.
 */
internal fun projectGravityPortrait(gx: Float, gy: Float, base: Offset, tiltGain: Float): Offset {
  val b = normalizeOr(base, Offset(-1f, -1f))
  return Offset(b.x + tiltGain * gx, b.y + tiltGain * -gy)
}

/**
 * Spec/oracle helper: the AND of every gate that must hold for the sensor to run. Not wired into
 * production (each actual applies these gates inline); it exists to pin the truth table for tests.
 */
internal fun shouldRunSensor(
  enabled: Boolean,
  reduceMotion: Boolean,
  hasSensor: Boolean,
  shaderPathActive: Boolean,
): Boolean = enabled && !reduceMotion && hasSensor && shaderPathActive
