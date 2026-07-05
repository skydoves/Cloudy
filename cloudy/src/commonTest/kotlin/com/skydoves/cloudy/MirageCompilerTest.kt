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

import androidx.compose.ui.graphics.Color
import com.skydoves.cloudy.internal.CHROMATIC_KERNEL_AGSL
import com.skydoves.cloudy.internal.DUOTONE_KERNEL_AGSL
import com.skydoves.cloudy.internal.DUOTONE_KERNEL_SKSL
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.FOIL_KERNEL_AGSL
import com.skydoves.cloudy.internal.MirageCompiler
import com.skydoves.cloudy.internal.MirageLintException
import com.skydoves.cloudy.internal.OpticCategory
import com.skydoves.cloudy.internal.SPECULAR_KERNEL_AGSL
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/** Params for the Duotone demo Colorize optic: two colors plus a blend amount. */
private class DuotoneParams : MirageParams() {
  val shadow by uniformColor(Color(0xFF1B1B3A))
  val highlight by uniformColor(Color(0xFFFFC371))
  val amount by uniform(1f)
}

/** Empty params, for kernels that declare no schema uniforms. */
private class EmptyParams : MirageParams()

/**
 * Unit tests for [MirageCompiler] — the pure, device-free codegen layer.
 *
 * Exercises the four assembly parts (preamble gating, referenced-only standard uniforms, schema
 * declarations, kernel wrapping), the raw verbatim path, and every lint rule. No GPU is touched:
 * assertions are on the generated source string and the [com.skydoves.cloudy.internal.CompiledProgram]
 * flags.
 */
internal class MirageCompilerTest :
  FunSpec({

    context("Colorize codegen") {
      test("wraps the kernel in a content-sampling main") {
        val optic = Optic.colorize(
          name = "duotone",
          paramsFactory = ::DuotoneParams,
          agsl = DUOTONE_KERNEL_AGSL,
          sksl = DUOTONE_KERNEL_SKSL,
        )

        val program = MirageCompiler.compile(optic, Dialect.Agsl)

        // The generated wrapper samples content once and feeds the pixel to the author's kernel.
        program.source.shouldContain(
          "half4 main(float2 xy) { return kernel(xy, content.eval(xy)); }",
        )
        program.source.shouldContain("uniform shader content;")
        program.usesContent.shouldBe(true)
        program.category.shouldBe(OpticCategory.Colorize)
      }

      test("emits one declaration per schema entry in declaration order") {
        val optic = Optic.colorize(
          name = "duotone",
          paramsFactory = ::DuotoneParams,
          agsl = DUOTONE_KERNEL_AGSL,
          sksl = DUOTONE_KERNEL_SKSL,
        )

        val src = MirageCompiler.compile(optic, Dialect.Agsl).source

        // Colors become layout(color) float4; the scalar becomes a plain float; order is preserved.
        src.shouldContain("layout(color) uniform float4 shadow;")
        src.shouldContain("layout(color) uniform float4 highlight;")
        src.shouldContain("uniform float amount;")
        src.indexOf("float4 shadow").shouldBeLessThan(src.indexOf("float4 highlight"))
        src.indexOf("float4 highlight").shouldBeLessThan(src.indexOf("uniform float amount"))
      }

      test("does not prepend the lens preamble (point-wise kernels need no helpers)") {
        val optic = Optic.colorize(
          name = "duotone",
          paramsFactory = ::DuotoneParams,
          agsl = DUOTONE_KERNEL_AGSL,
          sksl = DUOTONE_KERNEL_SKSL,
        )

        MirageCompiler.compile(optic, Dialect.Agsl).source.shouldNotContain("float boxRoundedSDF(")
      }
    }

    context("Composite codegen") {
      test("prepends the lens preamble and splices the author's main without a wrapper") {
        val kernel = """
          half4 main(float2 xy) {
              float sdf = boxRoundedSDF(xy - lensCenter, lensSize * 0.5, cornerRadius);
              return content.eval(xy);
          }
        """.trimIndent()
        val optic = Optic.composite(
          name = "c",
          paramsFactory = ::EmptyParams,
          agsl = kernel,
          sksl = kernel,
        )

        val program = MirageCompiler.compile(optic, Dialect.Agsl)

        program.source.shouldContain("float boxRoundedSDF(")
        program.source.shouldContain("uniform shader content;")
        // No Colorize wrapper is appended: the author owns the single main.
        program.source.shouldNotContain("return kernel(xy, content.eval(xy));")
        program.usesContent.shouldBe(true)
        program.category.shouldBe(OpticCategory.Composite)
      }
    }

    context("Generate codegen") {
      test("has the preamble but no content sampler, and reports usesContent=false") {
        val kernel = """
          half4 main(float2 xy) {
              float sdf = boxRoundedSDF(xy - lensCenter, lensSize * 0.5, cornerRadius);
              return half4(0.0);
          }
        """.trimIndent()
        val optic = Optic.generate(
          name = "g",
          paramsFactory = ::EmptyParams,
          agsl = kernel,
          sksl = kernel,
        )

        val program = MirageCompiler.compile(optic, Dialect.Agsl)

        program.source.shouldContain("float boxRoundedSDF(")
        program.source.shouldNotContain("uniform shader content;")
        program.usesContent.shouldBe(false)
        program.category.shouldBe(OpticCategory.Generate)
      }
    }

    context("standard uniforms are referenced-only") {
      test("mirageTime is declared only when the kernel names it, and drives usesTime") {
        val withTime = """
          half4 main(float2 xy) { return half4(half(sin(mirageTime)), 0.0, 0.0, 1.0); }
        """.trimIndent()
        val withoutTime = """
          half4 main(float2 xy) { return half4(0.0); }
        """.trimIndent()

        val timed = MirageCompiler.compile(
          Optic.generate("t", ::EmptyParams, withTime, withTime),
          Dialect.Agsl,
        )
        val untimed = MirageCompiler.compile(
          Optic.generate("u", ::EmptyParams, withoutTime, withoutTime),
          Dialect.Agsl,
        )

        timed.usesTime.shouldBe(true)
        timed.source.shouldContain("uniform float mirageTime;")

        untimed.usesTime.shouldBe(false)
        untimed.source.shouldNotContain("uniform float mirageTime;")
      }

      test(
        "mirageResolution / mirageDensity are declared only when referenced, and drive their flags",
      ) {
        val kernel = """
          half4 main(float2 xy) { return half4(half(mirageResolution.x), 0.0, 0.0, 1.0); }
        """.trimIndent()

        val program = MirageCompiler.compile(
          Optic.generate("r", ::EmptyParams, kernel, kernel),
          Dialect.Agsl,
        )

        program.source.shouldContain("uniform float2 mirageResolution;")
        program.source.shouldNotContain("uniform float mirageDensity;")
        program.usesResolution.shouldBe(true)
        program.usesDensity.shouldBe(false)
      }

      // Android's RuntimeShader throws IllegalArgumentException if the node binds a standard uniform
      // the shader never declared, so a kernel that names no standard uniform must report every uses*
      // flag false and the node must bind none.
      test("a kernel that names no standard uniform reports every uses* flag false") {
        val optic = Optic.colorize(
          name = "duotone",
          paramsFactory = ::DuotoneParams,
          agsl = DUOTONE_KERNEL_AGSL,
          sksl = DUOTONE_KERNEL_SKSL,
        )

        val program = MirageCompiler.compile(optic, Dialect.Agsl)

        program.usesResolution.shouldBe(false)
        program.usesTime.shouldBe(false)
        program.usesDensity.shouldBe(false)
        program.source.shouldNotContain("uniform float2 mirageResolution;")
        program.source.shouldNotContain("uniform float mirageTime;")
        program.source.shouldNotContain("uniform float mirageDensity;")
      }
    }

    context("raw optic emits its source verbatim") {
      test("no preamble, no generated declarations, no wrapper — flags still scanned") {
        val fullSource = """
          uniform float2 mirageResolution;
          half4 main(float2 xy) { return half4(half(mirageTime), 0.0, 0.0, 1.0); }
        """.trimIndent()
        val optic = Optic.raw(
          name = "raw",
          paramsFactory = ::EmptyParams,
          agsl = fullSource,
          sksl = fullSource,
        )

        val program = MirageCompiler.compile(optic, Dialect.Agsl)

        // The source is exactly what the author wrote — codegen added nothing.
        program.source.shouldBe(fullSource)
        program.source.shouldNotContain("float boxRoundedSDF(")
        // raw is always Composite and samples content; usesTime is still text-scanned.
        program.category.shouldBe(OpticCategory.Composite)
        program.usesContent.shouldBe(true)
        program.usesTime.shouldBe(true)
      }

      test("a forbidden token in a raw source is NOT linted (the author owns the whole shader)") {
        val fullSource = """
          half4 main(float2 xy) { float e = fwidth(xy.x); return half4(half(e)); }
        """.trimIndent()
        val optic = Optic.raw("raw", ::EmptyParams, fullSource, fullSource)

        shouldNotThrowAny { MirageCompiler.compile(optic, Dialect.Agsl) }
      }
    }

    context("lint rejects tokens that cannot compile") {
      test("fwidth is rejected") {
        val e = shouldThrow<MirageLintException> {
          MirageCompiler.lint("half4 main() { return half4(fwidth(x)); }", OpticCategory.Composite)
        }
        e.message.shouldContain("fwidth")
      }

      test("a #version directive is rejected") {
        val e = shouldThrow<MirageLintException> {
          MirageCompiler.lint(
            "#version 300 es\nhalf4 main() { return half4(0.0); }",
            OpticCategory.Composite,
          )
        }
        e.message.shouldContain("#version")
      }

      test("dFdx / sk_FragCoord are rejected") {
        shouldThrow<MirageLintException> {
          MirageCompiler.lint("float d = dFdx(x);", OpticCategory.Composite)
        }
        shouldThrow<MirageLintException> {
          MirageCompiler.lint("float2 c = sk_FragCoord.xy;", OpticCategory.Composite)
        }
      }
    }

    context("lint enforces the content-access category contract") {
      test("a Colorize kernel that references content is rejected") {
        val e = shouldThrow<MirageLintException> {
          MirageCompiler.lint(
            "half4 kernel(float2 p, half4 src) { return content.eval(p); }",
            OpticCategory.Colorize,
          )
        }
        e.message.shouldContain("Colorize")
        e.message.shouldContain("content")
      }

      test("a Generate kernel that references content is rejected") {
        val e = shouldThrow<MirageLintException> {
          MirageCompiler.lint(
            "half4 main(float2 xy) { return content.eval(xy); }",
            OpticCategory.Generate,
          )
        }
        e.message.shouldContain("Generate")
      }

      test("a Composite kernel may reference content freely") {
        shouldNotThrowAny {
          MirageCompiler.lint(
            "half4 main(float2 xy) { return content.eval(xy); }",
            OpticCategory.Composite,
          )
        }
      }
    }

    context("carried kernels compile through the pipeline") {
      // The specular/chromatic/foil bodies in MirageKernels.kt must pass lint (they use no forbidden
      // tokens) so they can be wired as optics.
      test("the ported Composite bodies pass lint") {
        shouldNotThrowAny {
          MirageCompiler.lint(SPECULAR_KERNEL_AGSL, OpticCategory.Composite)
          MirageCompiler.lint(CHROMATIC_KERNEL_AGSL, OpticCategory.Composite)
        }
      }

      test("the ported Foil overlay body passes Generate lint (never samples content)") {
        shouldNotThrowAny {
          MirageCompiler.lint(FOIL_KERNEL_AGSL, OpticCategory.Generate)
        }
      }
    }
  })

private fun Int.shouldBeLessThan(other: Int) {
  check(this < other) { "expected $this < $other" }
}
