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
@file:OptIn(ExperimentalForeignApi::class)

package com.skydoves.cloudy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMAttitudeReferenceFrameXArbitraryCorrectedZVertical
import platform.CoreMotion.CMDeviceMotion
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS tilt source. Streams a smoothed, throttled portrait-space light direction from CoreMotion's
 * device-motion gravity vector. No-op when disabled, when Reduce Motion is on, or when device
 * motion is unavailable (e.g. the simulator).
 *
 * The consuming app's `Info.plist` must declare `NSMotionUsageDescription`, or recent iOS
 * terminates the app on the first device-motion read.
 *
 * v1 projects gravity assuming portrait orientation; landscape is not yet remapped.
 */
@Composable
internal actual fun rememberTiltSource(
  enabled: Boolean,
  hz: Int,
  base: Offset,
  tiltGain: Float,
  out: MutableState<Offset>,
) {
  // Read reduce-motion before any motion start so a reduce-motion user never starts updates (never
  // triggers the NSMotionUsageDescription prompt); keying the effect on it makes toggling reversible.
  val reduceMotion by rememberReduceMotionState()

  DisposableEffect(enabled, hz, tiltGain, reduceMotion) {
    if (!enabled || reduceMotion || !SharedMotionManager.deviceMotionAvailable) {
      out.value = base
      return@DisposableEffect onDispose { }
    }

    // Per-subscriber math state — confined to the shared serial queue (the only caller).
    var smooth = base
    var lastSent = base
    var lastEmitNs = 0L
    val minEmitNs = 1_000_000_000L / hz
    val alpha = 0.2f
    val epsilon = 0.003f

    val token = SharedMotionManager.acquire(intervalSec = 1.0 / hz.coerceAtLeast(1)) { motion ->
      val raw = motion.gravity().useContents {
        projectGravityPortrait(x.toFloat(), y.toFloat(), base, tiltGain)
      }
      smooth = emaStep(smooth, raw, alpha)
      val now = (NSProcessInfo.processInfo.systemUptime * 1e9).toLong()
      if (now - lastEmitNs < minEmitNs) return@acquire // 30 Hz throttle
      val o = normalizeOr(smooth, base)
      if ((o - lastSent).getDistance() < epsilon) return@acquire // ε-gate
      smooth = o // snap EMA onto the emitted unit vector so a still phone truly stops emitting
      lastEmitNs = now
      lastSent = o
      dispatch_async(dispatch_get_main_queue()) { out.value = o } // Compose write on main
    }

    onDispose {
      SharedMotionManager.release(token)
      out.value = base
    }
  }
}

/**
 * Live [Reduce Motion][UIAccessibilityIsReduceMotionEnabled] state. Seeds from the current value and
 * updates via a [UIAccessibilityReduceMotionStatusDidChangeNotification] observer registered for the
 * whole composition (main queue). Mirrors Android's `rememberReduceMotionState`, so toggling the
 * setting while on screen takes effect in both directions.
 */
@Composable
private fun rememberReduceMotionState(): State<Boolean> {
  val state = remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
  DisposableEffect(Unit) {
    val observer = NSNotificationCenter.defaultCenter.addObserverForName(
      name = UIAccessibilityReduceMotionStatusDidChangeNotification,
      `object` = null,
      queue = NSOperationQueue.mainQueue,
    ) { _ ->
      state.value = UIAccessibilityIsReduceMotionEnabled()
    }
    onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
  }
  return state
}

/**
 * App-wide single [CMMotionManager] with ref-counted, token-keyed subscribers. Only this object
 * calls start/stopDeviceMotionUpdates: updates start on the 0→1 subscriber transition and stop on
 * N→0, so no screen can stop another's stream. All subscriber-map mutation and the start/stop
 * happen on the shared serial [queue], so no lock is needed.
 */
private object SharedMotionManager {
  private val manager = CMMotionManager()
  private val queue = NSOperationQueue().apply { maxConcurrentOperationCount = 1 }
  private val subscribers = mutableMapOf<Any, (CMDeviceMotion) -> Unit>()

  val deviceMotionAvailable: Boolean get() = manager.deviceMotionAvailable

  fun acquire(intervalSec: Double, onMotion: (CMDeviceMotion) -> Unit): Any {
    val token = Any()
    queue.addOperationWithBlock {
      // Fastest requested interval wins; each subscriber still throttles its own emit.
      val current = manager.deviceMotionUpdateInterval
      if (current <= 0.0 || intervalSec < current) {
        manager.deviceMotionUpdateInterval = intervalSec
      }
      val wasEmpty = subscribers.isEmpty()
      subscribers[token] = onMotion
      if (wasEmpty) {
        manager.startDeviceMotionUpdatesUsingReferenceFrame(
          CMAttitudeReferenceFrameXArbitraryCorrectedZVertical,
          queue,
        ) { motion, _ ->
          motion ?: return@startDeviceMotionUpdatesUsingReferenceFrame
          subscribers.values.forEach { it(motion) }
        }
      }
    }
    return token
  }

  fun release(token: Any) {
    queue.addOperationWithBlock {
      subscribers.remove(token)
      if (subscribers.isEmpty()) {
        manager.stopDeviceMotionUpdates()
      }
    }
  }
}
