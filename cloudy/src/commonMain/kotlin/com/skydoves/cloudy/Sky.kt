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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer

/**
 * State holder for background blur (backdrop blur) functionality.
 *
 * [Sky] manages the captured background content and coordinate information
 * for backdrop blur rendering. The name "Sky" represents the background
 * (sky) that "cloudy" (blur) effects are applied over.
 *
 * ## Usage
 *
 * ```kotlin
 * val sky = rememberSky()
 *
 * Box(modifier = Modifier.sky(sky)) {
 *   // Background content (images, lists, etc.)
 *   AsyncImage(model = "background.jpg", modifier = Modifier.fillMaxSize())
 *
 *   // Glassmorphism overlay (cloud over the sky)
 *   Card(
 *     modifier = Modifier
 *       .align(Alignment.Center)
 *       .cloudy(sky = sky, radius = 25)
 *   ) {
 *     Text("Frosted Glass Card")
 *   }
 * }
 * ```
 *
 * ## State Lifecycle
 *
 * 1. [Sky] is created via [rememberSky]
 * 2. [Modifier.sky] captures content to [backgroundLayer]
 * 3. [Modifier.cloudy] with sky parameter reads and blurs the captured content
 * 4. Call [invalidate] when background content changes to trigger re-capture
 *
 * @see rememberSky
 * @see Modifier.sky
 * @see Modifier.cloudy
 */
@Stable
public class Sky internal constructor() {

  /**
   * GraphicsLayer containing the captured background content.
   * Set by [Modifier.sky] and read by [Modifier.cloudy].
   *
   * This is `null` initially and when the sky modifier is detached.
   */
  internal var backgroundLayer: GraphicsLayer? by mutableStateOf(null)

  /**
   * Bounds of the sky container in local coordinates.
   * Used to calculate relative positioning for child composables
   * that apply background blur.
   */
  internal var sourceBounds: Rect by mutableStateOf(Rect.Zero)

  /**
   * Content version counter that increments every time the background
   * content is re-captured. Used by child modifiers to detect when
   * cached blur results should be invalidated.
   *
   * This counter enables proper cache invalidation during scrolling
   * on devices that use bitmap-based blur (API < 31).
   */
  internal var contentVersion: Long by mutableStateOf(0L)
    private set

  /**
   * Increments the content version, signaling that the background
   * content has changed. Called by [Modifier.sky] after capturing.
   */
  internal fun incrementContentVersion() {
    contentVersion++
  }

  /**
   * Invalidates the captured background content.
   *
   * Call this method when the background content changes and needs
   * to be re-captured for blur rendering. This increments [contentVersion],
   * which triggers dependent [Modifier.cloudy] modifiers to invalidate
   * their cached blur results.
   *
   * ## Example
   *
   * ```kotlin
   * val sky = rememberSky()
   * var imageUrl by remember { mutableStateOf("image1.jpg") }
   *
   * Box(modifier = Modifier.sky(sky)) {
   *   AsyncImage(
   *     model = imageUrl,
   *     onSuccess = { sky.invalidate() } // Re-capture when image loads
   *   )
   *
   *   Card(modifier = Modifier.cloudy(sky = sky, radius = 20)) {
   *     Text("Glass Card")
   *   }
   * }
   * ```
   */
  public fun invalidate() {
    incrementContentVersion()
  }
}

/**
 * Creates and remembers a [Sky] instance for background blur functionality.
 *
 * This function should be called at the top of your composable hierarchy
 * where you want to enable backdrop blur effects.
 *
 * ## Example
 *
 * ```kotlin
 * @Composable
 * fun GlassmorphismScreen() {
 *   val sky = rememberSky()
 *
 *   Box(modifier = Modifier.sky(sky)) {
 *     BackgroundImage()
 *     GlassCard(modifier = Modifier.cloudy(sky = sky, radius = 20))
 *   }
 * }
 * ```
 *
 * @return A remembered [Sky] instance that persists across recompositions.
 * @see Sky
 * @see Modifier.sky
 * @see Modifier.cloudy
 */
@Composable
public fun rememberSky(): Sky = remember { Sky() }
