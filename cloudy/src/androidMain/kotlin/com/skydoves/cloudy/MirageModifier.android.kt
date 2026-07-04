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
import com.skydoves.cloudy.internal.MirageElement

/**
 * Android implementation of the plan-based [Modifier.mirage].
 *
 * Attaches a `MirageNode` that orchestrates the plan. No API-level branch is needed here: a stage
 * whose `RuntimeShader` cannot be built below API 33 is skipped at draw time by the node (its
 * `MirageProgramCache.obtain` returns `null`), so on API < 33 the whole plan is a transparent
 * pass-through of the original content. The `MirageNode` reads its params blocks in the draw phase,
 * so a plan never forces recomposition.
 */
@ExperimentalMirage
public actual fun Modifier.mirage(
  clock: MirageClock,
  enabled: Boolean,
  plan: MiragePlanScope.() -> Unit,
): Modifier = this.then(MirageElement(clock, enabled, plan))
