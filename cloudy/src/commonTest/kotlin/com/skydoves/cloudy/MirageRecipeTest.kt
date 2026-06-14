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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Records every uniform write so a [MirageRecipe.bindUniforms] block can be exercised without a
 * GPU-backed [MirageScope]. Each overload normalizes its value into a comparable [List].
 */
private class RecordingMirageScope : MirageScope {
  val writes = mutableMapOf<String, List<Float>>()

  override fun uniform(name: String, value: Float) {
    writes[name] = listOf(value)
  }

  override fun uniform(name: String, x: Float, y: Float) {
    writes[name] = listOf(x, y)
  }

  override fun uniform(name: String, x: Float, y: Float, z: Float, w: Float) {
    writes[name] = listOf(x, y, z, w)
  }
}

/**
 * Unit tests for [MirageRecipe] / [MirageScope] / [MirageInputMode].
 *
 * Covers the parts verifiable from the common types alone: cache-key equality (source text +
 * input mode, lambda excluded), the hashCode contract, and uniform binding via a recording scope.
 * Assertions that depend on graphics-owned shader text (the preamble consts and the SPECULAR
 * recipe) are staged below as TODOs and are activated after the graphics files are integrated.
 */
internal class MirageRecipeTest :
  FunSpec({

    context("MirageRecipe equality (cache key = agsl + sksl + inputMode)") {
      test("same source and input mode are equal regardless of the bindUniforms lambda") {
        val a = MirageRecipe(agsl = "A", sksl = "S", bindUniforms = { uniform("x", 1f) })
        val b = MirageRecipe(agsl = "A", sksl = "S", bindUniforms = { uniform("y", 2f) })

        // Distinct lambdas, identical source/mode -> equal: the cache key is the compiled program,
        // not the per-draw value feeder.
        a.shouldBe(b)
        a.hashCode().shouldBe(b.hashCode())
      }

      test("differing agsl makes recipes unequal") {
        val a = MirageRecipe(agsl = "A", sksl = "S")
        val b = MirageRecipe(agsl = "B", sksl = "S")
        a.shouldNotBe(b)
      }

      test("differing sksl makes recipes unequal") {
        val a = MirageRecipe(agsl = "A", sksl = "S1")
        val b = MirageRecipe(agsl = "A", sksl = "S2")
        a.shouldNotBe(b)
      }

      test("differing input mode makes recipes unequal") {
        val a = MirageRecipe(agsl = "A", sksl = "S", inputMode = MirageInputMode.ContentFilter)
        val b = MirageRecipe(agsl = "A", sksl = "S", inputMode = MirageInputMode.Overlay)
        a.shouldNotBe(b)
      }

      test("a recipe equals itself and is unequal to other types") {
        val a = MirageRecipe(agsl = "A", sksl = "S")
        a.shouldBe(a)
        a.equals(null).shouldBe(false)
        a.equals("A").shouldBe(false)
      }

      test("default input mode is ContentFilter") {
        MirageRecipe(agsl = "A", sksl = "S").inputMode.shouldBe(MirageInputMode.ContentFilter)
      }

      test("toString omits the source bodies and the lambda") {
        val s = MirageRecipe(agsl = "abc", sksl = "de").toString()
        // Lengths, not bodies; and never the lambda (which has no stable string form).
        s.shouldBe("MirageRecipe(agsl=3 chars, sksl=2 chars, inputMode=ContentFilter)")
      }
    }

    context("bindUniforms execution via a recording scope") {
      test("each uniform overload records its value vector") {
        val recipe = MirageRecipe(
          agsl = "A",
          sksl = "S",
          bindUniforms = {
            uniform("scalar", 0.5f)
            uniform("vec2", 1f, 2f)
            uniform("vec4", 1f, 2f, 3f, 4f)
          },
        )

        val scope = RecordingMirageScope()
        recipe.bindUniforms(scope)

        scope.writes["scalar"].shouldBe(listOf(0.5f))
        scope.writes["vec2"].shouldBe(listOf(1f, 2f))
        scope.writes["vec4"].shouldBe(listOf(1f, 2f, 3f, 4f))
      }

      test("MirageRecipes.Specular writes the 11 spec uniforms at the GlowTuning defaults") {
        val scope = RecordingMirageScope()
        MirageRecipes.Specular.bindUniforms(scope)

        // Bit-exact regression gate: the extracted recipe must feed the historical liquidGlass
        // specular defaults verbatim (LiquidGlass GlowTuning). Cross-checked against the shared
        // GLOW_* constants so the test moves in lockstep if those defaults ever change.
        scope.writes.shouldBe(
          mapOf(
            "specStrength" to listOf(LiquidGlassDefaults.GLOW_INTENSITY),
            "specPower" to listOf(LiquidGlassDefaults.GLOW_SHARPNESS),
            "specRimMix" to listOf(0.4f),
            "specWidthPx" to listOf(12.0f),
            "specLightZ" to listOf(0.55f),
            "specDomeFrac" to listOf(1.15f),
            "specBodyPower" to listOf(2.5f),
            "specBodyGain" to listOf(0.6f),
            "specFocalK" to listOf(0.55f),
            "specPoolFrac" to listOf(0.7f),
            "specPoolGain" to listOf(1.3f),
          ),
        )
      }
    }

    context("preamble assembly (library owns uniforms + helpers, recipe owns main)") {
      test("preamble declares the standard uniforms and helpers but no main") {
        // The library-owned preamble holds every standard uniform + shared helper, and declares no
        // entry point, so a recipe's single main() is never a duplicate definition.
        PREAMBLE_AGSL.contains("uniform float2 lensCenter").shouldBe(true)
        PREAMBLE_AGSL.contains("uniform shader content").shouldBe(true)
        PREAMBLE_AGSL.contains("float boxRoundedSDF(").shouldBe(true)
        PREAMBLE_AGSL.contains("half4 main(").shouldBe(false)

        PREAMBLE_SKSL.contains("uniform float2 lensCenter").shouldBe(true)
        PREAMBLE_SKSL.contains("float boxRoundedSDF(").shouldBe(true)
        PREAMBLE_SKSL.contains("half4 main(").shouldBe(false)
      }

      test("the Specular recipe body owns exactly one main and reuses preamble helpers") {
        SPECULAR_AGSL.contains("half4 main(float2 xy)").shouldBe(true)
        SPECULAR_SKSL.contains("half4 main(float2 xy)").shouldBe(true)
        // Calls boxRoundedSDF without redeclaring it (the preamble owns the definition).
        SPECULAR_AGSL.contains("float boxRoundedSDF(").shouldBe(false)
      }

      test("AGSL and SKSL preambles declare the same standard uniforms") {
        // The two backends must expose an identical standard-uniform surface so one recipe body
        // (modulo the AGSL/SKSL dialect) binds the same names on every platform.
        val uniforms = listOf(
          "uniform float2 iResolution",
          "uniform float2 lensCenter",
          "uniform float2 lensSize",
          "uniform float cornerRadius",
          "uniform float2 iLight",
          "uniform float iTime",
          "uniform shader content",
        )
        uniforms.forEach { decl ->
          PREAMBLE_AGSL.contains(decl).shouldBe(true)
          PREAMBLE_SKSL.contains(decl).shouldBe(true)
        }
      }
    }

    context("chromatic factory and named looks (const -> uniform, value-preserving)") {
      // The full 7-uniform set the parameterized chromatic shader requires. Every preset must write
      // all of them (Skia throws on an unset uniform; AGSL reads garbage).
      val chromaticUniformNames = setOf(
        "chromaticIntensity",
        "chromaticGain",
        "chromaticKRGB",
        "chromaticFloor",
        "chromaticWashout",
        "chromaticModulate",
        "chromaticRimBoost",
      )

      fun recordedUniforms(recipe: MirageRecipe): Map<String, List<Float>> =
        RecordingMirageScope().also { recipe.bindUniforms(it) }.writes

      test("every chromatic look is a ContentFilter recipe that writes all 7 uniforms") {
        listOf(
          MirageRecipes.Chromatic,
          MirageRecipes.OilSlick,
          MirageRecipes.SoapBubble,
          MirageRecipes.MetallicFoil,
          MirageRecipes.Pearl,
        ).forEach { recipe ->
          recipe.inputMode.shouldBe(MirageInputMode.ContentFilter)
          recordedUniforms(recipe).keys.shouldBe(chromaticUniformNames)
        }
      }

      test("Chromatic equals the chromatic() factory defaults (no regression)") {
        // chromaticKRGB is fed via the 4-arg overload (float3 + ignored w), so it records 4 floats.
        recordedUniforms(MirageRecipes.Chromatic).shouldBe(
          mapOf(
            "chromaticIntensity" to listOf(0.6f),
            "chromaticGain" to listOf(3.0f),
            "chromaticKRGB" to listOf(1f, 1.18f, 1.42f, 0f),
            "chromaticFloor" to listOf(0.12f),
            "chromaticWashout" to listOf(0.16f),
            "chromaticModulate" to listOf(1f),
            "chromaticRimBoost" to listOf(0f),
          ),
        )
      }

      test("OilSlick carries its #124 parameters in lockstep") {
        recordedUniforms(MirageRecipes.OilSlick).shouldBe(
          mapOf(
            "chromaticIntensity" to listOf(0.6f),
            "chromaticGain" to listOf(5.5f),
            "chromaticKRGB" to listOf(1f, 1.30f, 1.72f, 0f),
            "chromaticFloor" to listOf(0.05f),
            "chromaticWashout" to listOf(0.07f),
            "chromaticModulate" to listOf(0.75f),
            "chromaticRimBoost" to listOf(0f),
          ),
        )
      }

      test("SoapBubble carries its #124 parameters in lockstep") {
        recordedUniforms(MirageRecipes.SoapBubble).shouldBe(
          mapOf(
            "chromaticIntensity" to listOf(0.6f),
            "chromaticGain" to listOf(1.7f),
            "chromaticKRGB" to listOf(1f, 1.11f, 1.26f, 0f),
            "chromaticFloor" to listOf(0.22f),
            "chromaticWashout" to listOf(0.50f),
            "chromaticModulate" to listOf(0.22f),
            "chromaticRimBoost" to listOf(0f),
          ),
        )
      }

      test("MetallicFoil carries its #124 parameters in lockstep") {
        recordedUniforms(MirageRecipes.MetallicFoil).shouldBe(
          mapOf(
            "chromaticIntensity" to listOf(0.6f),
            "chromaticGain" to listOf(3.6f),
            "chromaticKRGB" to listOf(1f, 1.26f, 1.62f, 0f),
            "chromaticFloor" to listOf(0.03f),
            "chromaticWashout" to listOf(0.05f),
            "chromaticModulate" to listOf(0.82f),
            "chromaticRimBoost" to listOf(0.45f),
          ),
        )
      }

      test("Pearl carries its #124 parameters in lockstep") {
        recordedUniforms(MirageRecipes.Pearl).shouldBe(
          mapOf(
            "chromaticIntensity" to listOf(0.6f),
            "chromaticGain" to listOf(2.4f),
            "chromaticKRGB" to listOf(1f, 1.07f, 1.18f, 0f),
            "chromaticFloor" to listOf(0.46f),
            "chromaticWashout" to listOf(0.58f),
            "chromaticModulate" to listOf(0.20f),
            "chromaticRimBoost" to listOf(0.45f),
          ),
        )
      }
    }

    context("Foil overlay recipe") {
      test("Foil is an Overlay recipe that writes its 5 uniforms") {
        MirageRecipes.Foil.inputMode.shouldBe(MirageInputMode.Overlay)
        val scope = RecordingMirageScope()
        MirageRecipes.Foil.bindUniforms(scope)
        scope.writes.shouldBe(
          mapOf(
            "foilBands" to listOf(5f),
            "foilPhase" to listOf(0f),
            "chromaticGain" to listOf(3.6f),
            "sparkleDensity" to listOf(16f),
            "sparkleAmplitude" to listOf(0.3f),
          ),
        )
      }
    }

    context("Overlay preamble + Foil body (generator: no content, owns one main)") {
      test("the Overlay preamble drops the content sampler and declares no main") {
        // An Overlay main is a pure generator: it never samples content, so the content-free preamble
        // omits the `content` child sampler while keeping the lens helpers for SDF masking.
        PREAMBLE_OVERLAY_AGSL.contains("uniform shader content").shouldBe(false)
        PREAMBLE_OVERLAY_AGSL.contains("float boxRoundedSDF(").shouldBe(true)
        PREAMBLE_OVERLAY_AGSL.contains("half4 main(").shouldBe(false)

        PREAMBLE_OVERLAY_SKSL.contains("uniform shader content").shouldBe(false)
        PREAMBLE_OVERLAY_SKSL.contains("float boxRoundedSDF(").shouldBe(true)
        PREAMBLE_OVERLAY_SKSL.contains("half4 main(").shouldBe(false)
      }

      test("the Foil body owns one main and never samples content") {
        FOIL_AGSL.contains("half4 main(float2 xy)").shouldBe(true)
        FOIL_SKSL.contains("half4 main(float2 xy)").shouldBe(true)
        // Pure generator: it must not call content.eval (the content sampler is not even declared).
        FOIL_AGSL.contains("content.eval").shouldBe(false)
        FOIL_SKSL.contains("content.eval").shouldBe(false)
      }
    }
  })
