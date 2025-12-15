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
package docs.navigation

sealed class DocsRoute(val path: String, val title: String) {
  data object Home : DocsRoute("/", "Home")
  data object GettingStarted : DocsRoute("/guide/getting-started", "Getting Started")
  data object Installation : DocsRoute("/guide/installation", "Installation")
  data object PlatformSupport : DocsRoute("/guide/platforms", "Platform Support")
  data object ApiCloudy : DocsRoute("/api/cloudy", "Modifier.cloudy()")
  data object ApiSky : DocsRoute("/api/sky", "Background Blur")
  data object ApiProgressive : DocsRoute("/api/progressive", "CloudyProgressive")
  data object ApiState : DocsRoute("/api/state", "CloudyState")
  data object Playground : DocsRoute("/playground", "Playground")

  companion object {
    val guideRoutes = listOf(GettingStarted, Installation, PlatformSupport)
    val apiRoutes = listOf(ApiCloudy, ApiSky, ApiProgressive, ApiState)
  }
}
