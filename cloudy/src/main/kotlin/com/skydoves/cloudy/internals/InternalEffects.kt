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
package com.skydoves.cloudy.internals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

@Composable
@NonRestartableComposable
@OptIn(InternalComposeApi::class)
internal fun InternalLaunchedEffect(
  key1: Any?,
  key2: Any?,
  key3: Any?,
  key4: Any?,
  block: suspend CoroutineScope.() -> Unit
) {
  val applyContext = currentComposer.applyCoroutineContext
  remember(key1, key2, key3, key4) { LaunchedEffectImpl(applyContext, block) }
}

internal class LaunchedEffectImpl(
  parentCoroutineContext: CoroutineContext,
  private val task: suspend CoroutineScope.() -> Unit
) : RememberObserver {
  private val scope = CoroutineScope(parentCoroutineContext)
  private var job: Job? = null

  override fun onRemembered() {
    job?.cancel("Old job was still running!")
    job = scope.launch(block = task)
  }

  override fun onForgotten() {
    job?.cancel()
    job = null
  }

  override fun onAbandoned() {
    job?.cancel()
    job = null
  }
}
