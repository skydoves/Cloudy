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

import android.content.Context
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Android tilt source. Streams a smoothed, throttled screen-space light direction from the
 * device's motion sensors. Hard no-op below API 33 (the Liquid Glass fallback has no shader),
 * when disabled, in inspection/preview, when Reduce Motion is on, or when no sensor exists.
 */
@Composable
internal actual fun rememberTiltSource(
  enabled: Boolean,
  hz: Int,
  base: Offset,
  tiltGain: Float,
  out: MutableState<Offset>,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  // Hard no-op gate: below API 33 there is no RuntimeShader to consume lightDir, and preview must
  // stay static. Never touch SensorManager in these cases.
  if (!enabled ||
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    LocalInspectionMode.current
  ) {
    SideEffect { out.value = base }
    return
  }

  // Live Reduce Motion state, backed by a ContentObserver on ANIMATOR_DURATION_SCALE.
  val reduceMotion by rememberReduceMotionState(context)

  // The provider owns the background HandlerThread. It is remembered (NOT created inside the
  // lifecycle effect) and keyed only on values that change its math, so the thread is created
  // once and torn down exactly once — register/unregister cycling does not leak it.
  val provider = remember(base, tiltGain, hz) {
    TiltLightProvider(
      sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
      windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
      base = base,
      tiltGain = tiltGain,
      hz = hz,
      mainHandler = Handler(Looper.getMainLooper()),
      onLight = { out.value = it },
    )
  }

  // Registration lifetime: start while STARTED (unless reduce-motion), stop on ON_STOP/dispose.
  DisposableEffect(provider, lifecycleOwner, reduceMotion) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START ->
          if (!reduceMotion) provider.start() else { provider.stop(); out.value = base }
        Lifecycle.Event.ON_STOP -> provider.stop()
        else -> Unit
      }
    }
    // If we are already STARTED when this effect (re)runs, reflect the current state immediately.
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      if (!reduceMotion) provider.start() else { provider.stop(); out.value = base }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      provider.stop()
      out.value = base
    }
  }

  // Provider lifetime: tear down the HandlerThread when the provider leaves composition or is
  // re-keyed. This is the line the original sketch omitted; without quitSafely() the thread leaks.
  DisposableEffect(provider) {
    onDispose { provider.shutdown() }
  }
}

/** Live [Reduce Motion][isReduceMotionEnabled] state, observed via a [ContentObserver]. */
@Composable
private fun rememberReduceMotionState(context: Context): MutableState<Boolean> {
  val state = remember { mutableStateOf(isReduceMotionEnabled(context)) }
  DisposableEffect(context) {
    val resolver = context.contentResolver
    val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
      override fun onChange(selfChange: Boolean) {
        state.value = isReduceMotionEnabled(context)
      }
    }
    resolver.registerContentObserver(uri, false, observer)
    onDispose { resolver.unregisterContentObserver(observer) }
  }
  return state
}

/**
 * Reduce Motion proxy: [Settings.Global.ANIMATOR_DURATION_SCALE] == 0 means the user disabled
 * animations. There is no public reduce-motion boolean below API 33, so this is the correct
 * cross-version signal (the setting exists since API 17, safe at minSdk 23).
 */
private fun isReduceMotionEnabled(context: Context): Boolean =
  Settings.Global.getFloat(
    context.contentResolver,
    Settings.Global.ANIMATOR_DURATION_SCALE,
    1f,
  ) == 0f

/**
 * Reads one motion sensor and emits a smoothed, throttled, gated screen-space light direction.
 *
 * Threading contract:
 * - [start]/[stop]/[shutdown] are called on the MAIN thread (lifecycle + content-observer).
 * - [onSensorChanged] runs on a dedicated background [HandlerThread]; the math fields below are
 *   confined to it. The only cross-thread hop is the [mainHandler] post of the emitted value.
 */
private class TiltLightProvider(
  private val sensorManager: SensorManager,
  private val windowManager: WindowManager,
  private val base: Offset,
  private val tiltGain: Float,
  hz: Int,
  private val mainHandler: Handler,
  private val onLight: (Offset) -> Unit,
) : SensorEventListener {

  private val periodUs = 1_000_000 / hz // sensor rate hint (not honored exactly; we re-throttle)
  private val minEmitNs = 1_000_000_000L / hz
  private val alpha = 0.2f
  private val epsilon = 0.003f // ~0.17° on a unit vector, below sensor noise floor

  // Pick ONE sensor for the whole lifetime so there is no scale jump from runtime switching.
  private val sensor: Sensor? =
    sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
      ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
      ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
      ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

  private val thread = HandlerThread("cloudy-tilt").apply { start() }
  private val sensorHandler = Handler(thread.looper)

  // --- confined to main thread ---
  private var started = false

  // --- confined to the sensor HandlerThread (touched only in onSensorChanged) ---
  private var smooth = base
  private var lastEmitNs = 0L
  private var lastSent = base
  private val rotM = FloatArray(9)
  private val gFilt = FloatArray(3)
  // -------------------------------------------------------------------------------

  @Volatile
  private var alive = true

  fun start() {
    val s = sensor ?: return // no sensor → stay frozen at base
    if (started) return
    started = true
    sensorManager.registerListener(this, s, periodUs, sensorHandler)
  }

  fun stop() {
    if (!started) return
    started = false
    sensorManager.unregisterListener(this)
  }

  fun shutdown() {
    stop()
    alive = false
    thread.quitSafely()
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (!alive) return
    val raw: Offset = when (event.sensor.type) {
      Sensor.TYPE_GRAVITY -> {
        val (gx, gy) = remapGravityToScreen(event.values[0], event.values[1])
        gravityToLight(gx, gy, base, tiltGain)
      }
      Sensor.TYPE_GAME_ROTATION_VECTOR,
      Sensor.TYPE_ROTATION_VECTOR,
      -> {
        // Gravity direction ≈ negated 3rd row of the world→device rotation matrix.
        SensorManager.getRotationMatrixFromVector(rotM, event.values)
        val (gx, gy) = remapGravityToScreen(-rotM[2] * 9.81f, -rotM[5] * 9.81f)
        gravityToLight(gx, gy, base, tiltGain)
      }
      Sensor.TYPE_ACCELEROMETER -> {
        // Low-pass to extract gravity from raw acceleration.
        gFilt[0] += alpha * (event.values[0] - gFilt[0])
        gFilt[1] += alpha * (event.values[1] - gFilt[1])
        val (gx, gy) = remapGravityToScreen(gFilt[0], gFilt[1])
        gravityToLight(gx, gy, base, tiltGain)
      }
      else -> return
    }

    smooth = emaStep(smooth, raw, alpha)
    val now = System.nanoTime()
    if (now - lastEmitNs < minEmitNs) return // 30 Hz emit throttle
    val outVal = normalizeOr(smooth, base)
    if ((outVal - lastSent).getDistance() < epsilon) return // ε-gate
    smooth = outVal // snap EMA onto the emitted unit vector so a still phone truly stops emitting
    lastEmitNs = now
    lastSent = outVal
    mainHandler.post { if (alive) onLight(outVal) } // Compose snapshot write on the main thread
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  /** Rotates in-plane gravity (device axes) into screen-space, accounting for display rotation. */
  private fun remapGravityToScreen(gx: Float, gy: Float): Pair<Float, Float> {
    @Suppress("DEPRECATION") // context.display is API 30+; defaultDisplay works at minSdk 23
    return when (windowManager.defaultDisplay.rotation) {
      Surface.ROTATION_90 -> -gy to gx
      Surface.ROTATION_180 -> -gx to -gy
      Surface.ROTATION_270 -> gy to -gx
      else -> gx to gy // ROTATION_0
    }
  }
}
