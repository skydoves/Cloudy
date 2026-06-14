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
@file:OptIn(ExperimentalShaderEffect::class)

package com.skydoves.cloudy

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Records every uniform write so a [ShaderRecipe.bindUniforms] block can be exercised without a
 * GPU-backed [ShaderEffectScope]. Each overload normalizes its value into a comparable [List].
 */
private class RecordingShaderEffectScope : ShaderEffectScope {
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
 * Unit tests for [ShaderRecipe] / [ShaderEffectScope] / [ShaderInputMode].
 *
 * Covers the parts verifiable from the common types alone: cache-key equality (source text +
 * input mode, lambda excluded), the hashCode contract, and uniform binding via a recording scope.
 * Assertions that depend on graphics-owned shader text (the preamble consts and the SPECULAR
 * recipe) are staged below as TODOs and are activated after the graphics files are integrated.
 */
internal class ShaderRecipeTest :
  FunSpec({

    context("ShaderRecipe equality (cache key = agsl + sksl + inputMode)") {
      test("same source and input mode are equal regardless of the bindUniforms lambda") {
        val a = ShaderRecipe(agsl = "A", sksl = "S", bindUniforms = { uniform("x", 1f) })
        val b = ShaderRecipe(agsl = "A", sksl = "S", bindUniforms = { uniform("y", 2f) })

        // Distinct lambdas, identical source/mode -> equal: the cache key is the compiled program,
        // not the per-draw value feeder.
        a.shouldBe(b)
        a.hashCode().shouldBe(b.hashCode())
      }

      test("differing agsl makes recipes unequal") {
        val a = ShaderRecipe(agsl = "A", sksl = "S")
        val b = ShaderRecipe(agsl = "B", sksl = "S")
        a.shouldNotBe(b)
      }

      test("differing sksl makes recipes unequal") {
        val a = ShaderRecipe(agsl = "A", sksl = "S1")
        val b = ShaderRecipe(agsl = "A", sksl = "S2")
        a.shouldNotBe(b)
      }

      test("differing input mode makes recipes unequal") {
        val a = ShaderRecipe(agsl = "A", sksl = "S", inputMode = ShaderInputMode.ContentFilter)
        val b = ShaderRecipe(agsl = "A", sksl = "S", inputMode = ShaderInputMode.Overlay)
        a.shouldNotBe(b)
      }

      test("a recipe equals itself and is unequal to other types") {
        val a = ShaderRecipe(agsl = "A", sksl = "S")
        a.shouldBe(a)
        a.equals(null).shouldBe(false)
        a.equals("A").shouldBe(false)
      }

      test("default input mode is ContentFilter") {
        ShaderRecipe(agsl = "A", sksl = "S").inputMode.shouldBe(ShaderInputMode.ContentFilter)
      }

      test("toString omits the source bodies and the lambda") {
        val s = ShaderRecipe(agsl = "abc", sksl = "de").toString()
        // Lengths, not bodies; and never the lambda (which has no stable string form).
        s.shouldBe("ShaderRecipe(agsl=3 chars, sksl=2 chars, inputMode=ContentFilter)")
      }
    }

    context("bindUniforms execution via a recording scope") {
      test("each uniform overload records its value vector") {
        val recipe = ShaderRecipe(
          agsl = "A",
          sksl = "S",
          bindUniforms = {
            uniform("scalar", 0.5f)
            uniform("vec2", 1f, 2f)
            uniform("vec4", 1f, 2f, 3f, 4f)
          },
        )

        val scope = RecordingShaderEffectScope()
        recipe.bindUniforms(scope)

        scope.writes["scalar"].shouldBe(listOf(0.5f))
        scope.writes["vec2"].shouldBe(listOf(1f, 2f))
        scope.writes["vec4"].shouldBe(listOf(1f, 2f, 3f, 4f))
      }

      test("ShaderRecipes.Specular writes the 11 spec uniforms at the GlowTuning defaults") {
        val scope = RecordingShaderEffectScope()
        ShaderRecipes.Specular.bindUniforms(scope)

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
  })
