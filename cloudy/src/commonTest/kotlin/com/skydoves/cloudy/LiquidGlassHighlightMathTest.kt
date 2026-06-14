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
import androidx.compose.ui.unit.IntSize
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * Unit tests for the platform-independent focal-pool geometry in `LiquidGlassHighlight.kt`. The draw
 * helpers in both platform bindings call [highlightPoolCenter] / [highlightPoolRadius] directly, so
 * these pin the exact center placement and radius clamp the shipped highlight uses. Screen space is
 * y-down (matches the shader and the gyro/transform light projection).
 */
internal class LiquidGlassHighlightMathTest :
  FunSpec({

    val size = IntSize(400, 200) // minDim = 200
    val poolFrac = HIGHLIGHT_POOL_FRAC
    val focalK = HIGHLIGHT_FOCAL_K

    context("highlightPoolRadius") {
      test("radius is minDim * poolFrac in the normal case") {
        highlightPoolRadius(size, poolFrac).shouldBe((200f * poolFrac) plusOrMinus 1e-3f)
      }

      test("uses the smaller dimension (min, not max)") {
        // 200 is the min dim of 400x200, so radius keys off 200 regardless of width.
        highlightPoolRadius(IntSize(50, 200), poolFrac)
          .shouldBe((50f * poolFrac) plusOrMinus 1e-3f)
      }

      test("clamps to 1f when minDim is 0 (zero-width gradient guard)") {
        highlightPoolRadius(IntSize(0, 0), poolFrac).shouldBe(1f)
        highlightPoolRadius(IntSize(400, 0), poolFrac).shouldBe(1f)
      }

      test("clamps to 1f when minDim * poolFrac would be below 1f") {
        // 1px * 0.7 = 0.7 -> clamps up to 1f.
        highlightPoolRadius(IntSize(1, 1), poolFrac).shouldBe(1f)
      }
    }

    context("highlightPoolCenter direction & sign (y-down)") {
      val halfW = size.width / 2f
      val halfH = size.height / 2f

      test("flat default dir (-1,-1) shifts the center up-left") {
        val c = highlightPoolCenter(size, Offset(-1f, -1f), focalK)
        (c.x < halfW).shouldBe(true) // left
        (c.y < halfH).shouldBe(true) // up (screen y-down)
      }

      test("center for (-1,-1) equals size/2 + normalize(-1,-1) * minDim * focalK") {
        val c = highlightPoolCenter(size, Offset(-1f, -1f), focalK)
        val n = normalizeOr(Offset(-1f, -1f), Offset(-1f, -1f))
        val expected = Offset(halfW, halfH) + n * (200f * focalK)
        c.x.shouldBe(expected.x plusOrMinus 1e-3f)
        c.y.shouldBe(expected.y plusOrMinus 1e-3f)
      }

      test("+x light direction pushes center.x right (> w/2)") {
        val c = highlightPoolCenter(size, Offset(1f, 0f), focalK)
        (c.x > halfW).shouldBe(true)
      }

      test("+y light direction (y-down) pushes center.y down (> h/2)") {
        val c = highlightPoolCenter(size, Offset(0f, 1f), focalK)
        (c.y > halfH).shouldBe(true)
      }
    }

    context("highlightPoolCenter axis independence") {
      val halfW = size.width / 2f
      val halfH = size.height / 2f

      test("a pure-x dir leaves center.y at h/2") {
        highlightPoolCenter(size, Offset(1f, 0f), focalK).y.shouldBe(halfH plusOrMinus 1e-3f)
      }

      test("a pure-y dir leaves center.x at w/2") {
        highlightPoolCenter(size, Offset(0f, 1f), focalK).x.shouldBe(halfW plusOrMinus 1e-3f)
      }
    }

    context("highlightPoolCenter magnitude independence (reuses normalizeOr)") {
      test("(-1,-1) and (-1000,-1000) give the same center") {
        val small = highlightPoolCenter(size, Offset(-1f, -1f), focalK)
        val large = highlightPoolCenter(size, Offset(-1000f, -1000f), focalK)
        small.x.shouldBe(large.x plusOrMinus 1e-3f)
        small.y.shouldBe(large.y plusOrMinus 1e-3f)
      }
    }
  })
