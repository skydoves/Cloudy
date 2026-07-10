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
@file:OptIn(ExperimentalMirage::class)

package com.skydoves.cloudy

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.skydoves.cloudy.internal.BlurWeather
import com.skydoves.cloudy.internal.PostProcess
import com.skydoves.cloudy.internal.Skylight
import com.skydoves.cloudy.internal.Stage
import com.skydoves.cloudy.internal.WeatherElement

/**
 * Skiko implementation of the foreground [Modifier.cloudy], shared across iOS, macOS, JVM Desktop, and
 * WASM. Runs the self-lit blur through the unified [WeatherElement] spine: Skia's `BlurEffect` is
 * always available, so this is always the Clear path.
 */
@Composable
public actual fun Modifier.cloudy(
  @IntRange(from = 0) radius: Int,
  enabled: Boolean,
  onStateChanged: (CloudyState) -> Unit,
): Modifier {
  require(radius >= 0) { "Blur radius must be non-negative, but was $radius" }

  if (!enabled) {
    return this
  }

  val weather = remember { BlurWeather() }
  return this.then(
    WeatherElement(
      weather = weather,
      skylight = Skylight.SelfLit,
      clock = MirageClock.Paused,
      enabled = true,
      stages = listOf(Stage.PlatformFilter(radius, CloudyProgressive.None)),
      postProcess = PostProcess(RectangleShape, Color.Transparent, null),
      weatherKey = Unit,
      onStateChanged = onStateChanged,
    ),
  )
}
