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
package com.skydoves.cloudy.internals

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.skydoves.cloudy.CloudyProgressive

/**
 * Immutable snapshot of background blur configuration for render thread.
 *
 * This data class captures all parameters needed to render background blur
 * at a specific moment, ensuring thread-safe access during rendering.
 */
@Immutable
internal data class SkySnapshot(
  /** Blur radius in pixels. */
  val radius: Int,
  /** Offset X of the child relative to the sky container. */
  val offsetX: Float,
  /** Offset Y of the child relative to the sky container. */
  val offsetY: Float,
  /** Width of the child composable. */
  val childWidth: Float,
  /** Height of the child composable. */
  val childHeight: Float,
  /** Progressive blur direction. */
  val direction: ProgressiveDirection,
  /** Normalized start position for progressive blur. */
  val fadeStart: Float,
  /** Normalized end position for progressive blur. */
  val fadeEnd: Float,
  /** Easing function for progressive blur transition. */
  val easing: Easing,
  /** Tint color to apply over the blur. */
  val tintColor: Color,
) {

  /**
   * Direction for progressive blur effects.
   */
  enum class ProgressiveDirection {
    /** Uniform blur across the entire region. */
    NONE,
    /** Blur decreases from top to bottom. */
    TOP_TO_BOTTOM,
    /** Blur decreases from bottom to top. */
    BOTTOM_TO_TOP,
    /** Blur at edges, clear in center. */
    EDGES,
  }

  companion object {
    /**
     * Creates a [SkySnapshot] from [CloudyProgressive] configuration.
     */
    fun fromProgressive(
      radius: Int,
      offsetX: Float,
      offsetY: Float,
      childWidth: Float,
      childHeight: Float,
      progressive: CloudyProgressive,
      tintColor: Color,
    ): SkySnapshot {
      val (direction, fadeStart, fadeEnd, easing) = when (progressive) {
        is CloudyProgressive.None -> ProgressiveParams(
          direction = ProgressiveDirection.NONE,
          fadeStart = 0f,
          fadeEnd = 1f,
          easing = FastOutSlowInEasing,
        )
        is CloudyProgressive.TopToBottom -> ProgressiveParams(
          direction = ProgressiveDirection.TOP_TO_BOTTOM,
          fadeStart = progressive.start,
          fadeEnd = progressive.end,
          easing = progressive.easing,
        )
        is CloudyProgressive.BottomToTop -> ProgressiveParams(
          direction = ProgressiveDirection.BOTTOM_TO_TOP,
          fadeStart = progressive.start,
          fadeEnd = progressive.end,
          easing = progressive.easing,
        )
        is CloudyProgressive.Edges -> ProgressiveParams(
          direction = ProgressiveDirection.EDGES,
          fadeStart = progressive.fadeDistance,
          fadeEnd = 1f - progressive.fadeDistance,
          easing = progressive.easing,
        )
      }

      return SkySnapshot(
        radius = radius,
        offsetX = offsetX,
        offsetY = offsetY,
        childWidth = childWidth,
        childHeight = childHeight,
        direction = direction,
        fadeStart = fadeStart,
        fadeEnd = fadeEnd,
        easing = easing,
        tintColor = tintColor,
      )
    }
  }
}

/**
 * Helper data class for destructuring progressive parameters.
 */
private data class ProgressiveParams(
  val direction: SkySnapshot.ProgressiveDirection,
  val fadeStart: Float,
  val fadeEnd: Float,
  val easing: Easing,
)
