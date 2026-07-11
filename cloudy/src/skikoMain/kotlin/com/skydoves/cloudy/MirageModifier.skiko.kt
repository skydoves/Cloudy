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
package com.skydoves.cloudy

import androidx.compose.ui.Modifier

/**
 * Skiko implementation of the plan-based [Modifier.mirage] — shared across iOS, macOS, Desktop, and
 * Wasm. Attaches a `MirageNode` that orchestrates the plan. Skia is always present, so every stage's
 * program compiles and the plan always renders — the [MirageFallback] therefore never triggers here.
 * The node reads its params blocks in the draw phase, so a plan never forces recomposition.
 */
@ExperimentalMirage
public actual fun Modifier.mirage(
  clock: MirageClock,
  enabled: Boolean,
  fallback: MirageFallback,
  plan: MirageScope.() -> Unit,
): Modifier = mirageOrFallback(clock, enabled, fallback, plan)
