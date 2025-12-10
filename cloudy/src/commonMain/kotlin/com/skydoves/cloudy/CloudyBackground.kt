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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Captures the content of this composable to a [GraphicsLayer] for background blur.
 *
 * Apply this modifier to the container that holds the background content you want
 * to blur. Child composables can then use [Modifier.cloudy] with the same [Sky]
 * to apply backdrop blur effects.
 *
 * ## Usage
 *
 * ```kotlin
 * val sky = rememberSky()
 *
 * Box(modifier = Modifier.sky(sky)) {
 *   // This content is captured for blur
 *   Image(painter = backgroundPainter, modifier = Modifier.fillMaxSize())
 *
 *   // This child blurs the captured background
 *   Card(modifier = Modifier.cloudy(sky = sky, radius = 20)) {
 *     Text("Glass Card")
 *   }
 * }
 * ```
 *
 * ## Performance
 *
 * - Content is recorded to a [GraphicsLayer] once per frame (or when [Sky.invalidate] is called)
 * - The original content is still drawn to screen after capture
 * - Layer is released when the modifier leaves composition
 *
 * @param sky The [Sky] state holder to store the captured content.
 * @return A [Modifier] that captures content for background blur.
 *
 * @see Sky
 * @see rememberSky
 * @see cloudy
 */
@Composable
public expect fun Modifier.sky(sky: Sky): Modifier

/**
 * Applies a background blur (backdrop blur) effect using the content captured by [Modifier.sky].
 *
 * This modifier reads the background content from [sky] and renders it with a blur effect,
 * clipped to this composable's bounds. This creates a glassmorphism/frosted glass effect
 * where content behind this composable appears blurred.
 *
 * ## Platform Behavior
 *
 * | Platform | API Level | Implementation | Progressive Blur |
 * |----------|-----------|----------------|------------------|
 * | Android | 33+ | AGSL RuntimeShader | Supported |
 * | Android | 31-32 | RenderEffect | Uniform only |
 * | Android | 30- | Bitmap + CPU | Uniform only |
 * | iOS/macOS/Desktop/WASM | - | Skia BlurEffect | Supported |
 *
 * ## Usage
 *
 * ```kotlin
 * val sky = rememberSky()
 *
 * Box(modifier = Modifier.sky(sky)) {
 *   AsyncImage(model = "background.jpg", modifier = Modifier.fillMaxSize())
 *
 *   // Basic backdrop blur
 *   Card(
 *     modifier = Modifier
 *       .cloudy(sky = sky, radius = 20)
 *   ) {
 *     Text("Glass Card")
 *   }
 *
 *   // Progressive blur (fades from blurred to clear)
 *   Box(
 *     modifier = Modifier
 *       .cloudy(
 *         sky = sky,
 *         radius = 25,
 *         progressive = CloudyProgressive.TopToBottom(),
 *         tint = Color.White.copy(alpha = 0.1f),
 *       )
 *   )
 * }
 * ```
 *
 * @param sky The [Sky] state holder containing the captured background content.
 * @param radius The blur radius in pixels. Must be non-negative.
 *               Converted to sigma using `sigma = radius / 2.0`.
 * @param progressive The progressive blur configuration. Use [CloudyProgressive.None]
 *                    for uniform blur, or [CloudyProgressive.TopToBottom]/[CloudyProgressive.BottomToTop]
 *                    for gradient blur effects. Defaults to [CloudyProgressive.None].
 * @param tint Optional tint color to blend over the blurred background.
 *             Use semi-transparent colors for best results.
 *             Defaults to [Color.Transparent] (no tint).
 * @param enabled If false, disables the blur effect and renders nothing (transparent).
 * @param onStateChanged Callback invoked when the blur state changes.
 * @return A [Modifier] with the background blur effect applied.
 *
 * @see Sky
 * @see Modifier.sky
 * @see CloudyProgressive
 * @see CloudyState
 */
@Composable
public expect fun Modifier.cloudy(
  sky: Sky,
  @androidx.annotation.IntRange(from = 0) radius: Int = CloudyDefaults.BackgroundRadius,
  progressive: CloudyProgressive = CloudyProgressive.None,
  tint: Color = Color.Transparent,
  enabled: Boolean = true,
  onStateChanged: (CloudyState) -> Unit = {},
): Modifier
