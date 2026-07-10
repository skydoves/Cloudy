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
package com.skydoves.cloudydemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import demo.CloudyDemoApp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    // Draw behind the transparent system bars so the full-bleed sky background reaches the screen
    // edges; inner content is inset by WindowInsets.safeDrawing in CollapsingAppBarScaffold. Default
    // SystemBarStyle.auto flips bar-icon contrast with the system light/dark theme, matching
    // PosterTheme's isSystemInDarkTheme() palette.
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    setContent { CloudyDemoApp() }
  }
}
