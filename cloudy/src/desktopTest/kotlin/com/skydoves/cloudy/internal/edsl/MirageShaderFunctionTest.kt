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

package com.skydoves.cloudy.internal.edsl

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
