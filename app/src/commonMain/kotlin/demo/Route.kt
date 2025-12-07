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

import kotlinx.serialization.Serializable

/**
 * Navigation routes for the Cloudy demo app.
 */
@Serializable
sealed interface Route {

  /**
   * The menu home screen with demo scenario selection.
   */
  @Serializable
  data object MenuHome : Route

  /**
   * The grid list screen showing Disney images in a 2-column grid with blur.
   */
  @Serializable
  data object GridList : Route

  /**
   * The radius selection list screen with Disney thumbnails.
   */
  @Serializable
  data object RadiusItems : Route

  /**
   * The blur detail screen for a specific radius with animated blur effect.
   *
   * @param radius The blur radius to demonstrate.
   */
  @Serializable
  data class RadiusDetail(val radius: Int) : Route

  /**
   * The grid screen with a blurred app bar overlay.
   */
  @Serializable
  data object BlurAppBarGrid : Route
}
