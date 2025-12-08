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
package demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * WASM/Browser platform back-handler implementation.
 * Integrates with browser's history API to handle the back button.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
  val currentOnBack = rememberUpdatedState(onBack)
  val hasState = remember { mutableStateOf(false) }

  DisposableEffect(enabled) {
    if (!enabled) {
      return@DisposableEffect onDispose { }
    }

    // Push a state to history only once to prevent history pollution
    if (!hasState.value) {
      window.history.pushState(null, "", null)
      hasState.value = true
    }

    val listener: (Event) -> Unit = { event ->
      event.preventDefault()
      currentOnBack.value()
    }

    window.addEventListener("popstate", listener)

    onDispose {
      window.removeEventListener("popstate", listener)
    }
  }
}
