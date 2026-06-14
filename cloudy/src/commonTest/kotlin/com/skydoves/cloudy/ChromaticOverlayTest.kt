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
@file:OptIn(ExperimentalLiquidGlassMaterial::class)

package com.skydoves.cloudy

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Unit tests for [ChromaticOverlay], [ChromaticMode], and the chromatic [LiquidGlassDefaults].
 *
 * Tests verify:
 * - The hand-written equals/hashCode contract (incl. -0.0f normalization)
 * - The factory's defensive `.toList()` copy of `spectrum`
 * - Default presets ([LiquidGlassDefaults.NoChromatic], [LiquidGlassDefaults.Holographic])
 */
internal class ChromaticOverlayTest :
  FunSpec({

    context("ChromaticOverlay equals / hashCode") {
      test("equal instances are equal and share a hash code") {
        val a = ChromaticOverlay(intensity = 0.6f, mode = ChromaticMode.Foil)
        val b = ChromaticOverlay(intensity = 0.6f, mode = ChromaticMode.Foil)
        a.shouldBe(b)
        a.hashCode().shouldBe(b.hashCode())
      }

      test("differing intensity is not equal") {
        val a = ChromaticOverlay(intensity = 0.6f, mode = ChromaticMode.Foil)
        val b = ChromaticOverlay(intensity = 0.3f, mode = ChromaticMode.Foil)
        a.shouldNotBe(b)
      }

      test("differing mode is not equal") {
        val a = ChromaticOverlay(intensity = 0.6f, mode = ChromaticMode.Foil)
        val b = ChromaticOverlay(intensity = 0.6f, mode = ChromaticMode.Iridescent)
        a.shouldNotBe(b)
      }

      test("differing spectrum is not equal") {
        val a = ChromaticOverlay(intensity = 0.6f, spectrum = listOf(Color.Red))
        val b = ChromaticOverlay(intensity = 0.6f, spectrum = listOf(Color.Blue))
        a.shouldNotBe(b)
      }

      test("structurally-equal spectrum lists are equal") {
        // Different list instances, same contents -> structural list equality.
        val a = ChromaticOverlay(intensity = 0.6f, spectrum = listOf(Color.Red, Color.Green))
        val b = ChromaticOverlay(intensity = 0.6f, spectrum = listOf(Color.Red, Color.Green))
        a.shouldBe(b)
        a.hashCode().shouldBe(b.hashCode())
      }

      test("-0.0f and 0.0f intensity compare equal AND hash equal") {
        // equals() uses `==` (so -0.0f == 0.0f), but Float.hashCode() hashes bits and would differ.
        // The -0.0f -> 0.0f normalization in hashCode() keeps the equals/hashCode contract.
        val zero = ChromaticOverlay(intensity = 0.0f, mode = ChromaticMode.Iridescent)
        val negZero = ChromaticOverlay(intensity = -0.0f, mode = ChromaticMode.Iridescent)
        zero.shouldBe(negZero)
        zero.hashCode().shouldBe(negZero.hashCode())
      }

      test("equals is reflexive and null-safe") {
        val a = ChromaticOverlay(intensity = 0.6f)
        a.shouldBe(a)
        a.equals(null).shouldBe(false)
        a.equals("not an overlay").shouldBe(false)
      }
    }

    context("ChromaticOverlay factory") {
      test("factory defensively copies the spectrum (.toList())") {
        // Mutating the caller's original list after construction must not affect the holder, so the
        // @Immutable contract holds.
        val original = mutableListOf(Color.Red, Color.Green)
        val overlay = ChromaticOverlay(intensity = 0.6f, spectrum = original)
        original.add(Color.Blue)
        overlay.spectrum.shouldBe(listOf(Color.Red, Color.Green))
        overlay.spectrum.size.shouldBe(2)
      }

      test("default factory values match the chromatic defaults") {
        val overlay = ChromaticOverlay()
        overlay.intensity.shouldBe(LiquidGlassDefaults.CHROMATIC_INTENSITY)
        overlay.mode.shouldBe(ChromaticMode.Iridescent)
        overlay.spectrum.shouldBe(LiquidGlassDefaults.HOLOGRAPHIC_SPECTRUM)
        overlay.customBrush.shouldBe(null)
      }
    }

    context("ChromaticMode") {
      test("has exactly two modes in declared order") {
        ChromaticMode.entries.size.shouldBe(2)
        ChromaticMode.entries[0].shouldBe(ChromaticMode.Iridescent)
        ChromaticMode.entries[1].shouldBe(ChromaticMode.Foil)
      }
    }

    context("LiquidGlassDefaults chromatic") {
      test("CHROMATIC_INTENSITY is the documented mid value") {
        LiquidGlassDefaults.CHROMATIC_INTENSITY.shouldBe(0.6f)
      }

      test("HOLOGRAPHIC_SPECTRUM is a non-empty rainbow") {
        LiquidGlassDefaults.HOLOGRAPHIC_SPECTRUM.size.shouldBe(7)
      }

      test("NoChromatic disables the sheen (zero intensity)") {
        LiquidGlassDefaults.NoChromatic.intensity.shouldBe(0f)
      }

      test("Holographic is an iridescent thin-film overlay at the default intensity") {
        LiquidGlassDefaults.Holographic.intensity.shouldBe(LiquidGlassDefaults.CHROMATIC_INTENSITY)
        LiquidGlassDefaults.Holographic.mode.shouldBe(ChromaticMode.Iridescent)
      }
    }
  })
