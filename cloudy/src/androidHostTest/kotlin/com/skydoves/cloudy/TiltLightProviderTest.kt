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

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

/**
 * Lifecycle tests for the Android tilt registrar/provider seam. Drives [TiltLightProvider] with a
 * [FakeSensorRegistrar] so register/unregister/quit and the post-stop stale-emission race can be
 * asserted deterministically, without a real [android.hardware.SensorManager] or device.
 */
@RunWith(RobolectricTestRunner::class)
internal class TiltLightProviderTest {

  private val base = Offset(-1f, -1f)

  /** Records register/unregister/quit calls; [hasSensor] is configurable so both branches are testable. */
  private class FakeSensorRegistrar(override val hasSensor: Boolean = true) : SensorRegistrar {
    var registerCount = 0
    var unregisterCount = 0
    var quitCount = 0
    var listener: SensorEventListener? = null

    override fun register(listener: SensorEventListener) {
      if (!hasSensor) return
      registerCount++
      this.listener = listener
    }

    override fun unregister(listener: SensorEventListener) {
      unregisterCount++
    }

    override fun quit() {
      quitCount++
    }
  }

  private fun newProvider(registrar: SensorRegistrar, out: (Offset) -> Unit): TiltLightProvider =
    TiltLightProvider(
      registrar = registrar,
      currentRotation = { Surface.ROTATION_0 },
      base = base,
      tiltGain = 1.2f,
      hz = 30,
      mainHandler = Handler(Looper.getMainLooper()),
      onLight = out,
    )

  private fun gravitySensor(): Sensor = ShadowSensor.newInstance(Sensor.TYPE_GRAVITY)

  private fun tiltEvent(gx: Float, gy: Float): SensorEvent = SensorEventBuilder.newBuilder()
    .setSensor(gravitySensor())
    .setValues(floatArrayOf(gx, gy, 9.0f))
    .build()

  private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

  @Test
  fun `start registers exactly once and is idempotent`() {
    val registrar = FakeSensorRegistrar()
    val provider = newProvider(registrar) {}

    provider.start()
    provider.start() // double start must not re-register

    assertEquals(1, registrar.registerCount)
  }

  @Test
  fun `stop unregisters and shutdown quits exactly once, both idempotent`() {
    val registrar = FakeSensorRegistrar()
    val provider = newProvider(registrar) {}

    provider.start()
    provider.stop()
    provider.stop() // double stop must not re-unregister

    assertEquals(1, registrar.unregisterCount)

    provider.shutdown() // already stopped: no extra unregister, quits once
    provider.shutdown()

    assertEquals(1, registrar.unregisterCount)
    assertEquals(1, registrar.quitCount)
  }

  @Test
  fun `no sensor means start never registers`() {
    val registrar = FakeSensorRegistrar(hasSensor = false)
    val provider = newProvider(registrar) {}

    provider.start()

    assertEquals(0, registrar.registerCount)
  }

  @Test
  fun `a late sensor event delivered after stop does not overwrite the frozen base`() {
    val registrar = FakeSensorRegistrar()
    var out: Offset = base
    val provider = newProvider(registrar) { out = it }

    provider.start()

    // stop() runs first (bumps generation, clears the main-confined `started` flag), then a sensor
    // event already queued on the sensor thread runs onSensorChanged and posts to main. The post's
    // `started` guard must drop it so the frozen base is never overwritten.
    provider.stop()
    registrar.listener!!.onSensorChanged(tiltEvent(gx = 8.0f, gy = 1.0f))
    idleMain()

    assertEquals(base, out)
  }

  @Test
  fun `an event delivered while started does emit a tilted light`() {
    val registrar = FakeSensorRegistrar()
    var out: Offset = base
    val provider = newProvider(registrar) { out = it }

    provider.start()
    registrar.listener!!.onSensorChanged(tiltEvent(gx = 8.0f, gy = 1.0f))
    idleMain()

    // Sanity: the same event that is dropped post-stop DOES reach `out` while started, so the
    // post-stop assertion is meaningful (the drop is the guard, not a suppressed emission).
    assertTrue("expected a tilted emission distinct from base, was $out", out != base)
  }
}
