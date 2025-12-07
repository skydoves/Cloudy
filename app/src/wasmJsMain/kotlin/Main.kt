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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import demo.CloudyDemoApp
import kotlinx.browser.document

/**
 * WebAssembly browser entry point for the Cloudy Demo.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  SingletonImageLoader.setSafe {
    ImageLoader.Builder(PlatformContext.INSTANCE)
      .components {
        add(KtorNetworkFetcherFactory())
      }
      .build()
  }

  val body = document.body ?: return
  ComposeViewport(body) {
    CloudyDemoApp()
  }
}
