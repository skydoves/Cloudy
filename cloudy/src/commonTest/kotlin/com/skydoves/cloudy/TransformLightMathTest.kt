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
 * Unit tests for the platform-independent transform-light math in `TransformLight.kt`. The factory
 * needs Compose state, so the testable surface is the pure [transformToLight] projection the factory
 * delegates to. Pins the exact sign polarity of the screen-space (y-down) mapping.
 */
internal class TransformLightMathTest :
  FunSpec({

    val base = Offset(-1f, -1f)
    // base normalized once for displacement-from-rest comparisons.
    val flat = normalizeOr(base, base)

    context("transformToLight resting invariant") {
      test("flat surface returns the normalized base direction unchanged") {
        transformToLight(0f, 0f, base, 1.2f).shouldBe(flat)
      }

      test("flat surface is independent of gain") {
        transformToLight(0f, 0f, base, 5f).shouldBe(flat)
        transformToLight(0f, 0f, base, 0.1f).shouldBe(flat)
      }
    }

    context("rotationY drives screen-x") {
      test("positive rotationY slides the light toward +x (screen-right)") {
        val tilted = transformToLight(0f, 30f, base, 1.2f)
        (tilted.x > flat.x).shouldBe(true)
      }

      test("negative rotationY slides the light toward -x (screen-left)") {
        val tilted = transformToLight(0f, -30f, base, 1.2f)
        (tilted.x < flat.x).shouldBe(true)
      }

      test("rotationY leaves screen-y at the base value") {
        transformToLight(0f, 30f, base, 1.2f).y.shouldBe(flat.y plusOrMinus 1e-4f)
      }
    }

    context("rotationX drives screen-y (y-down)") {
      test("positive rotationX DECREASES light.y (hotspot slides up)") {
        val tilted = transformToLight(30f, 0f, base, 1.2f)
        (tilted.y < flat.y).shouldBe(true)
      }

      test("negative rotationX INCREASES light.y (hotspot slides down)") {
        val tilted = transformToLight(-30f, 0f, base, 1.2f)
        (tilted.y > flat.y).shouldBe(true)
      }

      test("rotationX leaves screen-x at the base value") {
        transformToLight(30f, 0f, base, 1.2f).x.shouldBe(flat.x plusOrMinus 1e-4f)
      }
    }

    context("gain scales the displacement monotonically") {
      test("larger gain displaces the light further from rest") {
        val low = transformToLight(0f, 30f, base, 0.5f)
        val high = transformToLight(0f, 30f, base, 2.0f)
        val lowDelta = abs(low.x - flat.x)
        val highDelta = abs(high.x - flat.x)
        (highDelta > lowDelta).shouldBe(true)
      }

      test("displacement is exactly linear in gain") {
        val g1 = transformToLight(0f, 30f, base, 1f)
        val g2 = transformToLight(0f, 30f, base, 2f)
        val d1 = g1.x - flat.x
        val d2 = g2.x - flat.x
        d2.shouldBe((d1 * 2f) plusOrMinus 1e-4f)
      }
    }

    context("base normalization") {
      test("any-magnitude base maps to the same direction (reuses normalizeOr)") {
        val small = transformToLight(0f, 30f, Offset(-1f, -1f), 1.2f)
        val large = transformToLight(0f, 30f, Offset(-1000f, -1000f), 1.2f)
        small.x.shouldBe(large.x plusOrMinus 1e-3f)
        small.y.shouldBe(large.y plusOrMinus 1e-3f)
      }
    }

    context("symmetry about the base") {
      test("(a, b) and (-a, -b) mirror across the base direction") {
        val pos = transformToLight(20f, 35f, base, 1.2f)
        val neg = transformToLight(-20f, -35f, base, 1.2f)
        // Each component is base ± the same displacement, so the two results are symmetric about base.
        ((pos.x - flat.x)).shouldBe((-(neg.x - flat.x)) plusOrMinus 1e-4f)
        ((pos.y - flat.y)).shouldBe((-(neg.y - flat.y)) plusOrMinus 1e-4f)
      }
    }
  })
