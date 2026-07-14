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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * F5 `shaderFunction`: a user helper declared with `val <name> by shaderFunction(...)` is emitted as a
 * real GLSL function named after the property and called from the kernel. Reserved builtin names are
 * rejected, and a declared/traced return-type mismatch is a diagnostic.
 */
internal class MirageShaderFunctionTest :
  FunSpec({

    test("a user shaderFunction is emitted as a named GLSL function the kernel calls") {
      val kernel = MirageShader.generate("rings", ::EmptyParams) { xy ->
        val sdCircle by shaderFunction(Float2Type, Float1Type) { p -> length(p) - 0.5f }
        val d = sdCircle(xy)
        half4(half3(float3(d, d, d)), half(1f))
      }.agsl

      kernel shouldContain "float sdCircle(float2 p0)"
      kernel shouldContain "sdCircle(xy)"
    }

    test("nested shaderFunction calls emit dependency-first (callee declared before caller)") {
      // 3-level nesting mirroring RainyWindow's drops -> dropLayer2 -> n13:
      // a 5-param helper calls a 2-param helper, which calls a 1-param helper.
      val kernel = MirageShader.generate("nested", ::EmptyParams) { xy ->
        val leaf by shaderFunction(Float1Type, Float1Type) { p -> p * 2f }
        val mid by shaderFunction(Float2Type, Float1Type, Float1Type) { uv, t ->
          leaf(uv.x) + t
        }
        val top by shaderFunction(
          Float2Type,
          Float1Type,
          Float1Type,
          Float1Type,
          Float1Type,
          Float1Type,
        ) { uv, t, l0, l1, l2 ->
          mid(uv, t) + l0 + l1 + l2
        }
        val d = top(xy, float1(1f), float1(2f), float1(3f), float1(4f))
        half4(half3(float3(d, d, d)), half(1f))
      }.agsl

      kernel shouldContain "float leaf(float p0)"
      kernel shouldContain "float mid(float2 p0, float p1)"
      kernel shouldContain "float top(float2 p0, float p1, float p2, float p3, float p4)"

      // Dependency-first: each callee's declaration precedes its caller's declaration.
      val leafAt = kernel.indexOf("float leaf(")
      val midAt = kernel.indexOf("float mid(")
      val topAt = kernel.indexOf("float top(")
      (leafAt in 0 until midAt) shouldBe true
      (midAt in 0 until topAt) shouldBe true
    }

    test("a shaderFunction named after a builtin fails with RESERVED_IDENTIFIER") {
      val ex = shouldThrow<MirageDiagnosticException> {
        MirageShader.generate("reservedFn", ::EmptyParams) { xy ->
          val mix by shaderFunction(Float2Type, Float1Type) { p -> length(p) }
          half4(half3(float3(mix(xy), mix(xy), mix(xy))), half(1f))
        }
      }
      ex.code shouldBe MirageDiagnosticCode.RESERVED_IDENTIFIER
    }
  })
