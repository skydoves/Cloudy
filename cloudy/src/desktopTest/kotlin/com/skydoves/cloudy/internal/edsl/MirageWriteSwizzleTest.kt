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
import com.skydoves.cloudy.MirageParams
import com.skydoves.cloudy.MirageShader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

/** A uniform-free probe params for synthetic F4/F6/F7 test kernels. */
@ExperimentalMirage
internal class EmptyParams : MirageParams()

/**
 * F4 write-swizzle: assigning `pixel.rgb` / `pixel.a` on a `var pixel by local(...)` emits an in-place
 * channel write (`_v0.rgb = ...;`), not a rebuilt `half4(...)`. Verified on the emitted source text.
 */
internal class MirageWriteSwizzleTest :
  FunSpec({

    test("pixel.rgb and pixel.a writes emit in-place channel assignments") {
      val kernel = MirageShader.generate("writeSwizzle", ::EmptyParams) { xy ->
        var pixel by local(half4(half3(0.2f, 0.4f, 0.6f), half(1f)))
        pixel.rgb = mix(pixel.rgb, half3(1f, 1f, 1f), half(0.5f))
        pixel.a = half(0.25f)
        pixel
      }.agsl

      kernel shouldContain ".rgb = "
      kernel shouldContain ".a = "
    }

    test("pixel.rgb += lowers through plus to a single .rgb write (no plusAssign)") {
      val kernel = MirageShader.generate("writeSwizzlePlus", ::EmptyParams) { xy ->
        var pixel by local(half4(half3(0.1f, 0.1f, 0.1f), half(1f)))
        pixel.rgb += half3(0.2f, 0.2f, 0.2f)
        pixel
      }.agsl

      // get -> plus -> set: exactly one `.rgb =` write whose RHS adds to the read `.rgb`.
      kernel shouldContain ".rgb = ("
    }
  })
