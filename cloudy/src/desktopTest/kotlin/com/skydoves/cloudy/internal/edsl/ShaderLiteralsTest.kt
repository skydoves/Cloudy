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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.skydoves.cloudy.ExperimentalMirage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Host-type literals bake a color/geometry constant into the source as a plain constructor call — no new
 * IR node, and (per the color-space contract) the channels are emitted raw, with no sRGB conversion.
 */
internal class ShaderLiteralsTest :
  FunSpec({

    test("color(argb) splits 0xAARRGGBB into raw 0..1 channels as a half4 call") {
      val call = color(0xFF3366CCL).e.shouldBeInstanceOf<Call>()
      call.functionName shouldBe "half4"
      // 0x33=51, 0x66=102, 0xCC=204, 0xFF=255 over 255.
      call.args.map { (it as Literal).value } shouldBe
        listOf(51f / 255f, 102f / 255f, 204f / 255f, 1f)
    }

    test("offset/size emit a float2 constructor with the raw components") {
      (offset(Offset(3f, 4f)).e as Call).args.map { (it as Literal).value } shouldBe listOf(3f, 4f)
      (size(Size(10f, 20f)).e as Call).args.map { (it as Literal).value } shouldBe listOf(10f, 20f)
    }
  })
