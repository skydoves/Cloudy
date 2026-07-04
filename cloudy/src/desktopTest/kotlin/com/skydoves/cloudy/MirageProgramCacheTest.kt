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
import com.skydoves.cloudy.internal.DUOTONE_KERNEL_AGSL
import com.skydoves.cloudy.internal.DUOTONE_KERNEL_SKSL
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.MirageProgramCache
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs

/** Params for the Duotone Colorize optic: two colors plus a blend amount. */
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
 * The dedup contract is the point: the cache is keyed on the generated source text, so two optics
 * that lower to the same program must share one [com.skydoves.cloudy.internal.CachedProgram] instance.
 */
internal class MirageProgramCacheTest :
  FunSpec({

    test("obtain compiles a backend program on skiko") {
      val optic = Optic.colorize(
        name = "duotone",
        paramsFactory = ::CacheDuotoneParams,
        agsl = DUOTONE_KERNEL_AGSL,
        sksl = DUOTONE_KERNEL_SKSL,
      )

      val cached = MirageProgramCache.obtain(optic, Dialect.Sksl).shouldNotBeNull()

      // The backend compiled from this exact source; sanity-check the source carries the codegen the
      // skiko RuntimeEffect just accepted (schema declarations + the content-sampling wrapper).
      cached.compiled.source.shouldContain("uniform float amount;")
      cached.compiled.source.shouldContain("half4 main(float2 xy)")
    }

    test("two optics that lower to the same source share one CachedProgram instance") {
      // Distinct Optic objects, structurally equal (same name + sources + category), so they compile
      // to byte-identical source and must hit the same cache slot.
      val first = Optic.colorize(
        name = "duotone",
        paramsFactory = ::CacheDuotoneParams,
        agsl = DUOTONE_KERNEL_AGSL,
        sksl = DUOTONE_KERNEL_SKSL,
      )
      val second = Optic.colorize(
        name = "duotone",
        paramsFactory = ::CacheDuotoneParams,
        agsl = DUOTONE_KERNEL_AGSL,
        sksl = DUOTONE_KERNEL_SKSL,
      )

      val a = MirageProgramCache.obtain(first, Dialect.Sksl).shouldNotBeNull()
      val b = MirageProgramCache.obtain(second, Dialect.Sksl).shouldNotBeNull()

      // Dedup: same generated source -> the very same cached instance (not just an equal one).
      b.shouldBeSameInstanceAs(a)
      b.backend.shouldBeSameInstanceAs(a.backend)
    }
  })
