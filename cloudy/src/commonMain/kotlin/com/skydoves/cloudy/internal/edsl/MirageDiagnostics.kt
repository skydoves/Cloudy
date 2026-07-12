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

import com.skydoves.cloudy.ExperimentalMirage

/**
 * Stable, machine-readable mirage diagnostic codes — stable across versions so tests and tooling assert
 * on the code, never on the (freely reworded) message.
 *
 * The full set is declared up front even though this scope only throws four of them: the remaining
 * codes are the stable contract the later authoring features (`shaderFunction`, `branch`/`When`) will
 * raise, and pinning them now keeps their code values from shifting once those features land.
 */
@ExperimentalMirage
public enum class MirageDiagnosticCode {
  INTRINSIC_OUTSIDE_BODY, // statement-recording intrinsic ran with no open trace
  NESTED_TRACE, // a shader body started while another was tracing
  RESERVED_IDENTIFIER, // a user helper name shadows a builtin/preamble name
  FUNCTION_CYCLE, // shaderFunction call cycle (not yet raised)
  FUNCTION_RETURN_TYPE_MISMATCH, // declared vs traced return type (not yet raised)
  BRANCH_TYPE_MISMATCH, // branch/When arms produced different types (not yet raised)
  WHEN_WITHOUT_OTHERWISE, // When returned a value not produced by otherwise {} (not yet raised)
  FORBIDDEN_TOKEN, // raw-string body lint (fwidth / # / sk_FragCoord)
  CONTENT_IN_COLORIZE, // Colorize/Generate referenced content
}

/**
 * The single failure type for every mirage authoring/compile diagnostic. Carries a stable [code] so a
 * test or tool can branch on it, plus a human [message] and a [hint] on how to fix it. `internal`: the
 * [code] enum is the only part of the contract callers assert on, and it is public behind the marker.
 */
internal class MirageDiagnosticException(
  val code: MirageDiagnosticCode,
  override val message: String,
  val hint: String,
) : IllegalArgumentException("$message ($code) — $hint")
