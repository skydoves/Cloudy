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
import com.skydoves.cloudy.UOffset
import com.skydoves.cloudy.USize
import com.skydoves.cloudy.UVec4

/**
 * Lifts a [MirageParams][com.skydoves.cloudy.MirageParams] handle to the [Expression] node that reads
 * its uniform in the traced body. The handle's `slot` (assigned by `provideDelegate`, see
 * MirageParams.kt) is the only field this needs — it never reads `.value`, so this works during a
 * one-time trace exactly the same way it would mid-draw.
 *
 * [UFloat] declares a plain `uniform float` (MirageParams.kt's `registerHandle("float", ...)`), so it
 * lifts to [Float1] — a `half`-typed use (as Duotone's `amount` is) casts explicitly with [half],
 * exactly as the hand-written kernels write `half(amount)` rather than declaring the uniform itself
 * as `half`.
 */
internal fun UFloat.lift(): Float1 = Float1(UniformRef(slot, ShaderType.Float1))
internal fun UColor.lift(): Half4 = Half4(UniformRef(slot, ShaderType.Half4))
internal fun UOffset.lift(): Float2 = Float2(UniformRef(slot, ShaderType.Float2))
internal fun USize.lift(): Float2 = Float2(UniformRef(slot, ShaderType.Float2))
internal fun UVec4.lift(): Float4 = Float4(UniformRef(slot, ShaderType.Float4))
