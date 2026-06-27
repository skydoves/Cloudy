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
   *
   * Published as a plain (non-snapshot) reference: an overlay re-reads the current layer every
   * time it draws (the recorder always records into the SAME instance per recorder), and the
   * per-frame refresh is driven by [SkyFrameDriver], not by a snapshot read. Writing this as
   * snapshot state during the recorder's draw is what created the original idle redraw loop —
   * the descendant overlay read the state, got invalidated, and forced another frame forever.
   */
  internal var backgroundLayer: GraphicsLayer? = null

  /**
   * Bounds of the sky container in local coordinates. Updated from [Modifier.sky]'s
   * `onGloballyPositioned`. Plain field: read during the overlay's draw, which the frame driver
   * already re-runs each frame, so no snapshot observation is needed.
   */
  internal var sourceBounds: Rect = Rect.Zero

  /**
   * `true` while this sky's [Modifier.sky] recorder is recording the blur source.
   *
   * A backdrop [Modifier.cloudy] overlay is a descendant of the recorder, so the capture pass
   * re-enters the overlay's draw. The overlay reads this and draws NOTHING during capture: if it
   * drew its blur (which samples [backgroundLayer]) into the layer being recorded, that layer would
   * sample a layer that samples it — a cyclic `RenderNode` graph that overflows the render thread
   * stack (https://github.com/skydoves/Cloudy/issues/112).
   *
   * Scoped to this instance, so an overlay of a different sky is never suppressed. Supports exactly
   * ONE recorder per sky (the single backdrop container per screen). Plain (non-snapshot): capture
   * and the nested draws run synchronously on one draw pass, so no snapshot observation is needed.
   */
  internal var isCapturing: Boolean = false

  /** Runs [block] with this sky marked as capturing. */
  internal inline fun <T> capturing(block: () -> T): T {
    isCapturing = true
    try {
      return block()
    } finally {
      isCapturing = false
    }
  }

  /**
   * Per-frame refresh driver that keeps the blur tracking the backdrop while content moves.
   *
   * A `Modifier.sky` recorder on a non-scrolling container is not draw-invalidated by a list
   * scrolling underneath it, so its [backgroundLayer] would freeze while the list moves. The driver
   * re-invalidates the recorder + overlays while scrolling, then parks so the app idles at zero
   * frames when untouched. @see SkyFrameDriver.
   */
  internal val frameDriver: SkyFrameDriver = SkyFrameDriver()

  /**
   * Content version counter that increments every time [invalidate] (or the legacy capture path)
   * signals a background change. Used by the API < 31 bitmap blur to key its cache.
   *
   * NOT bumped per-draw anymore: an unconditional per-draw bump wrote snapshot state read during
   * the overlay's draw, re-invalidating it and self-perpetuating the idle redraw loop. The frame
   * driver now drives per-frame refresh, and this counter only marks discrete, explicit changes.
   */
  internal var contentVersion: Long by mutableStateOf(0L)
    private set

  /** Increments the content version, signaling a discrete background-content change. */
  internal fun incrementContentVersion() {
    contentVersion++
  }

  /**
   * Invalidates the captured background content.
   *
   * Call this method when the background content changes and needs
   * to be re-captured for blur rendering. This increments [contentVersion],
   * which triggers dependent [Modifier.cloudy] modifiers to invalidate
   * their cached blur results, and requests a refresh frame from the driver.
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
    frameDriver.requestRefresh()
  }

  /**
   * Invalidates the background content for an ANIMATED change lasting [durationMillis], keeping the
   * blur refreshing for that whole duration. A plain [invalidate] only arms a short settle tail, so
   * a longer animation (e.g. a cross-fade between two backdrops) would freeze the blur partway once
   * the tail elapses. Pass the animation's duration so the blur tracks it to completion.
   */
  public fun invalidate(durationMillis: Long) {
    require(durationMillis >= 0) { "durationMillis must be non-negative, but was $durationMillis" }
    incrementContentVersion()
    frameDriver.requestRefresh(durationMillis)
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
