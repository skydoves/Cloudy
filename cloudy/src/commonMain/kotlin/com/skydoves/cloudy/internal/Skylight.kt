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
package com.skydoves.cloudy.internal

import com.skydoves.cloudy.Sky

/**
 * Where the stage-0 pixels of a mirage plan come from: the node's own content ([SelfLit]) or a [Sky]
 * [Backdrop]. This is the one axis on which the former self-content and backdrop nodes differed — the
 * clock, chain, and uniform binding are identical — so it is factored out here and [WeatherNode]
 * branches on it instead of existing as two near-duplicate nodes.
 */
internal sealed interface Skylight {

  /** Stage 0 records the node's own content: `Modifier.mirage { … }`. */
  object SelfLit : Skylight

  /**
   * Stage 0 records the [sky] backdrop region behind the node: `Modifier.mirage(sky) { … }`. Read-only
   * over [sky] — it is never written, so the capture pass (issue #112) is never re-entered by grading.
   * A data class so skylight reconciliation is a plain `!=`: two backdrops are the same source iff
   * they share the [sky] (identity-stable via `rememberSky`).
   */
  data class Backdrop(val sky: Sky) : Skylight
}
