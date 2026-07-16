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

package com.skydoves.cloudy.edsl

import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.MirageShader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

private const val RASTER = 32

/**
 * F7 value-branches. [branch]…[Else] is a 2-way if-expression; [When] is an n-way first-match-wins
 * when-expression lowering to nested `if/else`. A missing `otherwise` is a trace diagnostic; mismatched
 * arm types are a diagnostic. Verified on emitted text and by rasterizing a `When`-built kernel.
 */
internal class MirageBranchTest :
  FunSpec({

    test("branch...Else emits an if/else that writes one temp on both paths") {
      val kernel = MirageShader.generate("branch2", ::EmptyParams) { xy ->
        val tint = branch(xy.x greaterThan 16f) {
          half3(1f, 0f, 0f)
        } Else {
          half3(0f, 0f, 1f)
        }
        half4(tint, half(1f))
      }.agsl

      kernel shouldContain "if ("
      kernel shouldContain "} else {"
    }

    test("When emits nested if/else for n cases + otherwise, first-match-wins order") {
      val kernel = MirageShader.generate("whenN", ::EmptyParams) { xy ->
        val zone = When {
          (xy.x greaterThan 24f) then { half3(1f, 0f, 0f) }
          (xy.x greaterThan 8f) then { half3(0f, 1f, 0f) }
          otherwise { half3(0f, 0f, 1f) }
        }
        half4(zone, half(1f))
      }.agsl

      // Two case conditions nested via else, then the otherwise else — nested `if/else` chain.
      Regex("if \\(").findAll(kernel).count() shouldBe 2
      kernel shouldContain "} else {"
    }

    test("a When without otherwise fails with WHEN_WITHOUT_OTHERWISE") {
      val ex = shouldThrow<MirageDiagnosticException> {
        MirageShader.generate("noOtherwise", ::EmptyParams) { xy ->
          val zone = When {
            (xy.x greaterThan 8f) then { half3(1f, 0f, 0f) }
            // No otherwise — the block ends in a stray value instead.
            half3(0f, 0f, 0f)
          }
          half4(zone, half(1f))
        }
      }
      ex.code shouldBe MirageDiagnosticCode.WHEN_WITHOUT_OTHERWISE
    }

    test("branch arms of different types fail with BRANCH_TYPE_MISMATCH") {
      val ex = shouldThrow<MirageDiagnosticException> {
        MirageShader.generate("mismatch", ::EmptyParams) { xy ->
          // Arms return different value types (Half3 vs Float3): Kotlin infers T = ShaderValue, and the
          // trace-time arm-type check rejects the disagreement before the result is wrapped.
          branch<ShaderValue>(xy.x greaterThan 8f) {
            half3(1f, 0f, 0f)
          } Else {
            float3(0f, 0f, 1f)
          }
          half4(0f) // unreachable: the arm-type mismatch above throws during the trace
        }
      }
      ex.code shouldBe MirageDiagnosticCode.BRANCH_TYPE_MISMATCH
    }

    test("a When-built kernel rasterizes like its hand-written if/else equivalent") {
      meanAbsDiff(
        rasterize(edslWhenShader(), RASTER),
        rasterize(handRolledWhenShader(), RASTER),
      ).shouldBeLessThanOrEqual(0.5)
    }
  })

private fun edslWhenShader(): Shader {
  val kernel = MirageShader.generate("whenRaster", ::EmptyParams) { xy ->
    val zone = When {
      (xy.x greaterThan 24f) then { half3(1f, 0f, 0f) }
      (xy.x greaterThan 8f) then { half3(0f, 1f, 0f) }
      otherwise { half3(0f, 0f, 1f) }
    }
    half4(zone, half(1f))
  }.agsl
  return RuntimeShaderBuilder(RuntimeEffect.makeForShader(kernel)).makeShader()
}

private fun handRolledWhenShader(): Shader = RuntimeShaderBuilder(
  RuntimeEffect.makeForShader(
    """
    half4 main(float2 xy) {
      half3 zone;
      if ((xy.x > 24.0)) { zone = half3(1.0, 0.0, 0.0); }
      else { if ((xy.x > 8.0)) { zone = half3(0.0, 1.0, 0.0); }
      else { zone = half3(0.0, 0.0, 1.0); } }
      return half4(zone, half(1.0));
    }
    """.trimIndent(),
  ),
).makeShader()
