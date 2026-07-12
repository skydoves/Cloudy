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
import com.skydoves.cloudy.internal.CachedProgram
import com.skydoves.cloudy.internal.DUOTONE_KERNEL_AGSL
import com.skydoves.cloudy.internal.DUOTONE_KERNEL_SKSL
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.MirageProgramCache
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

/** Reads the `chromaticGain` schema-entry default (a Float) from a chromatic shader's program. */
private fun gainDefaultOf(program: CachedProgram): Float =
  program.compiled.schema.entries.first { it.name == "chromaticGain" }.default as Float

/** Params for the Duotone Colorize shader: two colors plus a blend amount. */
private class CacheDuotoneParams : MirageParams() {
  val shadow by uniformColor(Color(0xFF1B1B3A))
  val highlight by uniformColor(Color(0xFFFFC371))
  val amount by uniform(1f)
}

/**
 * Tests for [MirageProgramCache] — the process-wide compiled-program cache. Runs on desktop (JVM /
 * skiko) so [com.skydoves.cloudy.internal.createBackendProgram] actually compiles SKSL through the
 * skiko backend (a plain-JVM RuntimeEffect compile needs no GPU surface).
 *
 * The dedup contract is the point: the cache is keyed on the generated source text, so two shaders
 * that lower to the same program share one [com.skydoves.cloudy.internal.MirageBackendProgram] (the
 * expensive GPU artifact) — while each [com.skydoves.cloudy.internal.CachedProgram] carries that
 * call's own [com.skydoves.cloudy.internal.CompiledProgram], so per-shader schema defaults are not
 * aliased to whichever shader compiled first.
 */
internal class MirageProgramCacheTest :
  FunSpec({

    test("obtain compiles a backend program on skiko") {
      val shader = MirageShader.colorize(
        name = "duotone",
        paramsFactory = ::CacheDuotoneParams,
        agsl = DUOTONE_KERNEL_AGSL,
        sksl = DUOTONE_KERNEL_SKSL,
      )

      val cached = MirageProgramCache.obtain(shader, Dialect.Sksl).shouldNotBeNull()

      // The backend compiled from this exact source; sanity-check the source carries the codegen the
      // skiko RuntimeEffect just accepted (schema declarations + the content-sampling wrapper).
      cached.compiled.source.shouldContain("uniform float amount;")
      cached.compiled.source.shouldContain("half4 main(float2 xy)")
    }

    test("two shaders that lower to the same source share one backend program instance") {
      // Distinct MirageShader objects, structurally equal (same name + sources + category), so they compile
      // to byte-identical source and must hit the same backend cache slot.
      val first = MirageShader.colorize(
        name = "duotone",
        paramsFactory = ::CacheDuotoneParams,
        agsl = DUOTONE_KERNEL_AGSL,
        sksl = DUOTONE_KERNEL_SKSL,
      )
      val second = MirageShader.colorize(
        name = "duotone",
        paramsFactory = ::CacheDuotoneParams,
        agsl = DUOTONE_KERNEL_AGSL,
        sksl = DUOTONE_KERNEL_SKSL,
      )

      val a = MirageProgramCache.obtain(first, Dialect.Sksl).shouldNotBeNull()
      val b = MirageProgramCache.obtain(second, Dialect.Sksl).shouldNotBeNull()

      // Dedup: same generated source -> the very same shared backend (the expensive GPU program).
      b.backend.shouldBeSameInstanceAs(a.backend)
      // Each call returns its own CachedProgram wrapper carrying its own CompiledProgram, so a
      // caller's schema defaults are never aliased to another same-source shader's.
      b.shouldNotBeSameInstanceAs(a)
    }

    // OilSlick and Pearl are the same chromatic kernel at different ChromaticParams defaults, so they
    // share one backend but must keep separate schemas: a cache hit must return this shader's own
    // CachedProgram, not the first-compiled one, or every same-source shader would render identically.
    test("same-source shaders keep their own schema defaults over a shared backend") {
      val oil = MirageProgramCache.obtain(MirageShaders.OilSlick, Dialect.Sksl).shouldNotBeNull()
      val pearl = MirageProgramCache.obtain(MirageShaders.Pearl, Dialect.Sksl).shouldNotBeNull()

      // Shared expensive artifact: identical generated source -> one backend program.
      pearl.backend.shouldBeSameInstanceAs(oil.backend)

      // Per-shader schema: the chromaticGain default must differ (OilSlick 5.5 vs Pearl 2.4), proving
      // this shader's defaults — not the first winner's — reach the binder each draw.
      gainDefaultOf(oil).shouldBe(5.5f)
      gainDefaultOf(pearl).shouldBe(2.4f)
    }

    // Every bundled MirageShaders kernel must compile through the pipeline on the skiko backend
    // (schema uniforms emitted, no duplicate inline declaration, standard uniforms resolved): if the
    // inline-uniform strip or the iTime -> mirageTime rename were wrong, RuntimeEffect.makeForShader
    // would throw here.
    test("every bundled preset shader compiles through the skiko backend") {
      shouldNotThrowAny {
        listOf(
          MirageShaders.Specular,
          MirageShaders.Chromatic,
          MirageShaders.OilSlick,
          MirageShaders.SoapBubble,
          MirageShaders.MetallicFoil,
          MirageShaders.Pearl,
          MirageShaders.Foil,
          MirageShaders.Duotone,
        ).forEach { shader ->
          MirageProgramCache.obtain(shader, Dialect.Sksl).shouldNotBeNull()
        }
      }
    }
  })
