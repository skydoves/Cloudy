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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/**
 * Unit tests for the platform-independent gyro light math in `GyroLight.kt`. Sensors cannot run in
 * unit tests, so the testable surface is the pure functions the platform providers delegate to.
 */
internal class GyroMathTest :
  FunSpec({

    context("emaStep") {
      test("converges to a constant target") {
        var s = Offset(0f, 0f)
        val target = Offset(1f, 1f)
        repeat(50) { s = emaStep(s, target, 0.2f) }
        s.x.shouldBe(1f plusOrMinus 0.01f)
        s.y.shouldBe(1f plusOrMinus 0.01f)
      }

      test("a smaller alpha damps a single spike more than a larger alpha") {
        val prev = Offset(0f, 0f)
        val spike = Offset(1f, 0f)
        val low = emaStep(prev, spike, 0.1f)
        val high = emaStep(prev, spike, 0.9f)
        (low.x < high.x).shouldBe(true)
      }
    }

    context("gravityToLight (Android, m/s²)") {
      test("flat device returns the base direction unchanged") {
        val base = Offset(-1f, -1f)
        gravityToLight(0f, 0f, base, 1.2f).shouldBe(base.let { normalizeOr(it, it) })
      }

      test("tilt sign: positive in-plane gravity x shifts the light negative on x") {
        // base normalized is (-0.707, -0.707); -gx/9.81 with gx>0 is negative.
        val base = Offset(-1f, -1f)
        val flat = gravityToLight(0f, 0f, base, 1.2f)
        val tilted = gravityToLight(5f, 0f, base, 1.2f)
        (tilted.x < flat.x).shouldBe(true)
      }
    }

    context("projectGravityPortrait (iOS, G units)") {
      test("flat device returns the base direction unchanged") {
        val base = Offset(-1f, -1f)
        projectGravityPortrait(0f, 0f, base, 1.2f).shouldBe(base.let { normalizeOr(it, it) })
      }

      test("G-unit gravity is not divided, so sweep is ~10x stronger than the m/s² helper") {
        val base = Offset(-1f, -1f)
        val ios = projectGravityPortrait(0.5f, 0f, base, 1.2f)
        val android = gravityToLight(0.5f, 0f, base, 1.2f)
        val iosDelta = abs(ios.x - normalizeOr(base, base).x)
        val androidDelta = abs(android.x - normalizeOr(base, base).x)
        (iosDelta > androidDelta * 5f).shouldBe(true)
      }

      test("tilt sign: lowering the right edge (gx>0) shifts the light toward screen-right") {
        val base = Offset(-1f, -1f)
        val flat = projectGravityPortrait(0f, 0f, base, 1.2f)
        val tilted = projectGravityPortrait(0.5f, 0f, base, 1.2f)
        (tilted.x > flat.x).shouldBe(true)
      }
    }

    context("normalizeOr") {
      test("returns the unit vector of a non-zero offset") {
        val n = normalizeOr(Offset(3f, 4f), Offset(-1f, -1f))
        n.x.shouldBe(0.6f plusOrMinus 0.001f)
        n.y.shouldBe(0.8f plusOrMinus 0.001f)
      }

      test("falls back when the offset is ~zero") {
        val fallback = Offset(-1f, -1f)
        normalizeOr(Offset(0f, 0f), fallback).shouldBe(fallback)
      }
    }

    context("shouldRunSensor truth table") {
      test("runs only when enabled, motion allowed, sensor present, shader path active") {
        shouldRunSensor(enabled = true, reduceMotion = false, hasSensor = true, shaderPathActive = true)
          .shouldBe(true)
      }
      test("reduce motion disables it") {
        shouldRunSensor(enabled = true, reduceMotion = true, hasSensor = true, shaderPathActive = true)
          .shouldBe(false)
      }
      test("disabled disables it") {
        shouldRunSensor(enabled = false, reduceMotion = false, hasSensor = true, shaderPathActive = true)
          .shouldBe(false)
      }
      test("no sensor disables it") {
        shouldRunSensor(enabled = true, reduceMotion = false, hasSensor = false, shaderPathActive = true)
          .shouldBe(false)
      }
      test("inactive shader path disables it") {
        shouldRunSensor(enabled = true, reduceMotion = false, hasSensor = true, shaderPathActive = false)
          .shouldBe(false)
      }
    }
  })
