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
package docs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import docs.component.DocsSidebar
import docs.navigation.DocsRoute
import docs.screen.ApiCloudyScreen
import docs.screen.ApiProgressiveScreen
import docs.screen.ApiSkyScreen
import docs.screen.ApiStateScreen
import docs.screen.GettingStartedScreen
import docs.screen.HomeScreen
import docs.screen.InstallationScreen
import docs.screen.PlatformSupportScreen
import docs.screen.PlaygroundScreen
import docs.theme.DocsTheme

@Composable
fun DocsApp() {
  var isDarkTheme by remember { mutableStateOf(true) }
  var currentRoute by remember { mutableStateOf<DocsRoute>(DocsRoute.Home) }

  DocsTheme(darkTheme = isDarkTheme) {
    Row(modifier = Modifier.fillMaxSize().background(DocsTheme.colors.background)) {
      DocsSidebar(
        currentRoute = currentRoute,
        onNavigate = { currentRoute = it },
        isDarkTheme = isDarkTheme,
        onToggleTheme = { isDarkTheme = !isDarkTheme },
      )

      Box(modifier = Modifier.weight(1f).fillMaxSize()) {
        when (currentRoute) {
          DocsRoute.Home -> HomeScreen(onNavigate = { currentRoute = it })
          DocsRoute.GettingStarted -> GettingStartedScreen()
          DocsRoute.Installation -> InstallationScreen()
          DocsRoute.PlatformSupport -> PlatformSupportScreen()
          DocsRoute.ApiCloudy -> ApiCloudyScreen()
          DocsRoute.ApiSky -> ApiSkyScreen()
          DocsRoute.ApiProgressive -> ApiProgressiveScreen()
          DocsRoute.ApiState -> ApiStateScreen()
          DocsRoute.Playground -> PlaygroundScreen()
        }
      }
    }
  }
}
