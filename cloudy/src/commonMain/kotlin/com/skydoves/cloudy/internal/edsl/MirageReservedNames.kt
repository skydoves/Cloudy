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
package com.skydoves.cloudy.internal.edsl

import com.skydoves.cloudy.internal.PREAMBLE_HELPER_NAMES

/**
 * The identifiers a future user-authored helper (`shaderFunction`) must not reuse — reusing one would
 * shadow a builtin/preamble/keyword and silently miscompile on the GPU.
 *
 * Nothing calls this yet: the only current name sources ([TraceContext.freshName] `_v`, hoist `_t`) are
 * `_`-prefixed and cannot collide with a user name, so the guard has no live use until `shaderFunction`
 * lands. It is built now as that feature's prerequisite, and its [BUILTIN_FUNCTIONS] half is fenced by
 * the "every emitted builtin is reserved" test so the list can't drift from what `ShaderValues` emits.
 */
internal object MirageReservedNames {

  /**
   * The GLSL intrinsic/type-constructor names `ShaderValues` prints via `Call("name", ...)`, plus
   * `foilHash` — a trace-registered helper, not a GLSL builtin or a [PREAMBLE_HELPER_NAMES] preamble
   * constant, so it has no other single source to draw from. Kept in sync with the emitter by
   * [MirageReservedNamesTest]'s divergence gate. Preamble helpers (`boxRoundedSDF`, ...) are *also*
   * emitted this way but are reserved via [PREAMBLE_HELPER_NAMES] instead — listing them here too would
   * be a second source for the same name.
   */
  val BUILTIN_FUNCTIONS: Set<String> = setOf(
    "abs", "clamp", "cos", "dot", "exp", "floor", "fract", "half", "half3", "half4",
    "float2", "float3", "float4", "length", "max", "min", "mirage_luma", "mix",
    "normalize", "pow", "sin", "smoothstep", "sqrt", "step", "foilHash",
  )

  /** The GLSL/SkSL keywords a helper name must not collide with. */
  val GLSL_KEYWORDS: Set<String> = setOf(
    "if", "else", "for", "while", "do", "return", "break", "continue", "discard",
    "in", "out", "inout", "const", "uniform", "layout", "main", "kernel",
    "true", "false", "half", "float", "int", "bool", "void",
  )

  val RESERVED: Set<String> = BUILTIN_FUNCTIONS + PREAMBLE_HELPER_NAMES + GLSL_KEYWORDS
}
