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
import com.skydoves.cloudy.UColor
import com.skydoves.cloudy.UFloat

/**
 * Traces the Duotone body: reads luminance from `src`, blends `shadow` -> `highlight` by that
 * luminance, then cross-fades by `amount`. This is the eDSL source of truth for the Duotone kernel —
 * [emitColorizeKernel]'s output replaces the hand-written `DUOTONE_KERNEL_AGSL` / `DUOTONE_KERNEL_SKSL`
 * text.
 *
 * Takes the three uniform handles directly rather than a `DuotoneParams` instance: a params-class
 * parameter would put `com.skydoves.cloudy.DuotoneParams` in this function's JVM signature, and
 * MirageCompilerTest.kt privately declares its own same-named `DuotoneParams` in the same package for
 * unrelated codegen tests — two same-named-but-different classes on one classpath is a JVM identity
 * trap this signature sidesteps entirely.
 */
internal fun traceDuotone(shadow: UColor, highlight: UColor, amount: UFloat): ShaderModule {
  val src = Half4(Argument("src", ShaderType.Half4))

  val g = luma(src.rgb)
  val dz = mix(shadow.lift().rgb, highlight.lift().rgb, g)
  val result = half4(mix(src.rgb, dz, half(amount.lift())), src.a)

  return ShaderModule(statements = emptyList(), returnExpr = result.e)
}
