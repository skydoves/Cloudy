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
import com.skydoves.cloudy.internal.MirageCompiler
import com.skydoves.cloudy.internal.ShaderCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Verifies mirage failures carry a stable [MirageDiagnosticCode] a tool can branch on, instead of only
 * a free-text message. Asserts on `.code`, never on wording.
 */
internal class MirageDiagnosticsTest :
  FunSpec({

    test("a Colorize kernel that references content fails with CONTENT_IN_COLORIZE") {
      val ex = shouldThrow<MirageDiagnosticException> {
        MirageCompiler.lint(
          "half4 kernel(float2 p, half4 src) { return content.eval(p); }",
          ShaderCategory.Colorize,
        )
      }
      ex.code shouldBe MirageDiagnosticCode.CONTENT_IN_COLORIZE
    }

    test("a forbidden derivative token fails with FORBIDDEN_TOKEN") {
      val ex = shouldThrow<MirageDiagnosticException> {
        MirageCompiler.lint("half4 main() { return half4(fwidth(x)); }", ShaderCategory.Composite)
      }
      ex.code shouldBe MirageDiagnosticCode.FORBIDDEN_TOKEN
    }
  })
