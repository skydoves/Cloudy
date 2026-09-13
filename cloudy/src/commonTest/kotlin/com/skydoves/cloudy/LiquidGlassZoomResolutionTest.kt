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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

internal class LiquidGlassZoomResolutionTest :
  FunSpec({
    test("automatic zoom selects fallback and shader defaults") {
      LiquidGlassDefaults.ZOOM.shouldBe(0f)
      resolveLiquidGlassZoom(0f, fallback = true).shouldBe(1.03f)
      resolveLiquidGlassZoom(0f, fallback = false).shouldBe(1f)
    }

    test("signed zero also selects automatic zoom") {
      resolveLiquidGlassZoom(-0f, fallback = true).shouldBe(1.03f)
      resolveLiquidGlassZoom(-0f, fallback = false).shouldBe(1f)
    }

    test("positive finite explicit zoom is preserved on both backends") {
      for (fallback in listOf(false, true)) {
        for (zoom in listOf(Float.MIN_VALUE, 0.5f, 1f, 1.03f, 1.2f, Float.MAX_VALUE)) {
          resolveLiquidGlassZoom(zoom, fallback).shouldBe(zoom)
        }
      }
    }

    test("negative and nonfinite zoom fail on both backends") {
      for (fallback in listOf(false, true)) {
        val invalid = listOf(
          -Float.MIN_VALUE,
          -1f,
          Float.NaN,
          Float.POSITIVE_INFINITY,
          Float.NEGATIVE_INFINITY,
        )
        for (zoom in invalid) {
          shouldThrow<IllegalArgumentException> { resolveLiquidGlassZoom(zoom, fallback) }
        }
      }
    }
  })
