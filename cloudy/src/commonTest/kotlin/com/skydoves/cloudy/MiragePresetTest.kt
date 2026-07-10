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
@file:OptIn(ExperimentalMirage::class)

package com.skydoves.cloudy

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the [MirageOptics] preset defaults.
 *
 * Each preset's look lives entirely in its params' declared defaults, so these are the values a visual
 * regression would move. This test mints a fresh params instance from each optic's `paramsFactory` and
 * asserts the handle defaults in lockstep over the typed schema.
 *
 * The `spec*` defaults are cross-checked against the shared `LiquidGlassDefaults.GLOW_*` constants so
 * the test moves with them if those defaults ever change (the built-in `liquidGlass` glint contract).
 */
internal class MiragePresetTest :
  FunSpec({

    context("shared lens framing defaults (auto: node-framed at bind time)") {
      // Every lens-shaped preset starts unframed: Unspecified lens geometry resolves to the node's
      // center / full size when bound (see bindUniforms), so a bare preset covers the node it is
      // attached to. A fixed default would pin the lens at the origin and leave the rest of the node
      // as kernel passthrough. cornerRadius / iLight keep the built-in liquid-glass values.
      fun assertLensDefaults(params: MirageLensParams) {
        params.lensCenter.value.shouldBe(Offset.Unspecified)
        params.lensSize.value.shouldBe(Size.Unspecified)
        params.cornerRadius.value.shouldBe(50f)
        params.iLight.value.shouldBe(Offset(-1f, -1f))
      }

      test("Specular / Chromatic / Foil share the auto lens framing defaults") {
        assertLensDefaults(MirageOptics.Specular.paramsFactory())
        assertLensDefaults(MirageOptics.Chromatic.paramsFactory())
        assertLensDefaults(MirageOptics.Foil.paramsFactory())
      }
    }

    context("Specular carries the GlowTuning defaults (bit-exact liquid-glass glint)") {
      test("the 11 spec* defaults match the historical GlowTuning values") {
        val p = MirageOptics.Specular.paramsFactory()
        p.specStrength.value.shouldBe(LiquidGlassDefaults.GLOW_INTENSITY) // 0.7
        p.specPower.value.shouldBe(LiquidGlassDefaults.GLOW_SHARPNESS) // 10.0
        p.specRimMix.value.shouldBe(0.4f)
        p.specWidthPx.value.shouldBe(12.0f)
        p.specLightZ.value.shouldBe(0.55f)
        p.specDomeFrac.value.shouldBe(1.15f)
        p.specBodyPower.value.shouldBe(2.5f)
        p.specBodyGain.value.shouldBe(0.6f)
        p.specFocalK.value.shouldBe(0.55f)
        p.specPoolFrac.value.shouldBe(0.7f)
        p.specPoolGain.value.shouldBe(1.3f)
      }
    }

    context("thin-film looks carry their #124 parameters in lockstep") {
      // (intensity, gain, kRGB, floor, washout, modulate, rimBoost).
      fun assertChromatic(
        params: ChromaticParams,
        intensity: Float,
        gain: Float,
        krgb: FloatArray,
        floor: Float,
        washout: Float,
        modulate: Float,
        rimBoost: Float,
      ) {
        params.chromaticIntensity.value.shouldBe(intensity)
        params.chromaticGain.value.shouldBe(gain)
        params.chromaticKRGB.value.toList().shouldBe(krgb.toList())
        params.chromaticFloor.value.shouldBe(floor)
        params.chromaticWashout.value.shouldBe(washout)
        params.chromaticModulate.value.shouldBe(modulate)
        params.chromaticRimBoost.value.shouldBe(rimBoost)
        // Not a per-look factory argument: every look shares the specular pool framing (0.7).
        params.chromaticPoolFrac.value.shouldBe(0.7f)
      }

      test("Chromatic equals the factory defaults (no regression)") {
        assertChromatic(
          MirageOptics.Chromatic.paramsFactory(),
          intensity = 0.6f,
          gain = 3.0f,
          krgb = floatArrayOf(1f, 1.18f, 1.42f, 0f),
          floor = 0.12f,
          washout = 0.16f,
          modulate = 1f,
          rimBoost = 0f,
        )
      }

      test("OilSlick") {
        assertChromatic(
          MirageOptics.OilSlick.paramsFactory(),
          intensity = 0.6f,
          gain = 5.5f,
          krgb = floatArrayOf(1f, 1.30f, 1.72f, 0f),
          floor = 0.05f,
          washout = 0.07f,
          modulate = 0.75f,
          rimBoost = 0f,
        )
      }

      test("SoapBubble") {
        assertChromatic(
          MirageOptics.SoapBubble.paramsFactory(),
          intensity = 0.6f,
          gain = 1.7f,
          krgb = floatArrayOf(1f, 1.11f, 1.26f, 0f),
          floor = 0.22f,
          washout = 0.50f,
          modulate = 0.22f,
          rimBoost = 0f,
        )
      }

      test("MetallicFoil") {
        assertChromatic(
          MirageOptics.MetallicFoil.paramsFactory(),
          intensity = 0.6f,
          gain = 3.6f,
          krgb = floatArrayOf(1f, 1.26f, 1.62f, 0f),
          floor = 0.03f,
          washout = 0.05f,
          modulate = 0.82f,
          rimBoost = 0.45f,
        )
      }

      test("Pearl") {
        assertChromatic(
          MirageOptics.Pearl.paramsFactory(),
          intensity = 0.6f,
          gain = 2.4f,
          krgb = floatArrayOf(1f, 1.07f, 1.18f, 0f),
          floor = 0.46f,
          washout = 0.58f,
          modulate = 0.20f,
          rimBoost = 0.45f,
        )
      }
    }

    context("Foil overlay defaults carry the recipe-era values") {
      test("the 5 foil/sparkle defaults are in lockstep") {
        val p = MirageOptics.Foil.paramsFactory()
        p.foilBands.value.shouldBe(5f)
        p.foilPhase.value.shouldBe(0f)
        p.chromaticGain.value.shouldBe(3.6f)
        p.sparkleDensity.value.shouldBe(16f)
        p.sparkleAmplitude.value.shouldBe(0.3f)
      }
    }
  })
