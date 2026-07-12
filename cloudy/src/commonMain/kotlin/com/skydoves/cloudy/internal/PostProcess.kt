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
package com.skydoves.cloudy.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.skydoves.cloudy.HIGHLIGHT_FOCAL_K
import com.skydoves.cloudy.HIGHLIGHT_POOL_FRAC
import com.skydoves.cloudy.HIGHLIGHT_STOPS
import com.skydoves.cloudy.LiquidGlassLight
import com.skydoves.cloudy.highlightPoolCenter
import com.skydoves.cloudy.highlightPoolRadius

/**
 * Node-level presentation applied around an [Effect] draw: clip the result to [shape], blend a [tint]
 * over it (on the GPU/CPU paths — the scrim path carries the tint itself), and draw a moving
 * specular highlight for [light]. Split out of the two backdrop nodes, where the clip/tint/highlight
 * code was byte-for-byte duplicated between androidMain and skikoMain.
 */
internal data class PostProcess(val shape: Shape, val tint: Color, val light: LiquidGlassLight?)

/**
 * Mutable per-node caches the [PostProcess] runner reuses so a steady-state frame allocates nothing:
 * the shape-clip [Path] (rebuilt only when the outline changes) and the highlight [Brush] (rebuilt
 * only when the pool center/radius change). Owned by [EffectNode] and released on detach.
 */
internal class PostProcessCache {
  var clipPath: Path? = null
  var highlightBrush: Brush? = null
  var highlightCenter: Offset = Offset.Unspecified
  var highlightRadius: Float = -1f

  fun clear() {
    clipPath = null
    highlightBrush = null
    highlightCenter = Offset.Unspecified
    highlightRadius = -1f
  }
}

/**
 * Runs [content] clipped to [post].shape, then blends the tint and draws the specular highlight over
 * it — the shared clip/tint/highlight order the two backdrop nodes had after `drawLayer`. [size] is
 * the node's measured size; [cache] holds the reusable path/brush. [content] receives the outer
 * [ContentDrawScope] (not the clip's inner [DrawScope]) so it can invoke an [Effect]'s
 * `ContentDrawScope` draw; the clip transform is still in effect on it inside the block.
 */
internal fun ContentDrawScope.applyPostProcess(
  post: PostProcess,
  size: IntSize,
  cache: PostProcessCache,
  clipRectangle: Boolean,
  content: ContentDrawScope.() -> Unit,
) {
  val contentScope = this
  clipToShape(post.shape, size, cache, clipRectangle) {
    contentScope.content()
    if (post.tint != Color.Transparent) {
      drawRect(color = post.tint, blendMode = BlendMode.SrcOver)
    }
    post.light?.let { drawHighlight(it, size, cache) }
  }
}

/**
 * Clips [block] to [shape]'s outline so the fill follows rounded corners instead of a hard rectangle.
 *
 * [clipRectangle] decides whether a plain [RectangleShape][androidx.compose.ui.graphics.RectangleShape]
 * clips at all, because the two sources need opposite behaviour: a BACKDROP must always clip — its
 * blur layer carries a CLAMP `RenderEffect` whose bloom would otherwise smear past the node onto
 * siblings (the pre-refactor backdrop node always clipped). The CONTENT (foreground) blur must NOT
 * rect-clip — its bloom is meant to bleed past the content edge, matching the pre-pipeline
 * `graphicsLayer { renderEffect = .. }` behaviour the screenshot goldens encode. Rounded/generic
 * shapes always clip (they carry corner geometry).
 */
internal fun ContentDrawScope.clipToShape(
  shape: Shape,
  size: IntSize,
  cache: PostProcessCache,
  clipRectangle: Boolean,
  block: DrawScope.() -> Unit,
) {
  when (val outline = shape.createOutline(size.toSize(), layoutDirection, this)) {
    is Outline.Rectangle -> if (clipRectangle) clipRect { block() } else block()

    is Outline.Rounded -> {
      val path = cache.clipPath ?: Path().also { cache.clipPath = it }
      path.rewind()
      path.addRoundRect(outline.roundRect)
      clipPath(path) { block() }
    }

    is Outline.Generic -> clipPath(outline.path) { block() }
  }
}

/**
 * Draws the moving specular highlight over the already-composited result. Uses `SrcOver` (not
 * `Screen`): the brush already encodes an additive falloff, and `SrcOver` is the same HWUI-safe path
 * used for tint. Moved verbatim from the two nodes' `drawHighlight`.
 */
private fun DrawScope.drawHighlight(
  light: LiquidGlassLight,
  size: IntSize,
  cache: PostProcessCache,
) {
  if (minOf(size.width, size.height) <= 0) return
  // Pure geometry lives in commonMain so the shipped path is the unit-tested path.
  val center = highlightPoolCenter(size, light.direction.value, HIGHLIGHT_FOCAL_K)
  val radius = highlightPoolRadius(size, HIGHLIGHT_POOL_FRAC)
  val brush = if (center != cache.highlightCenter || radius != cache.highlightRadius) {
    Brush.radialGradient(
      colorStops = HIGHLIGHT_STOPS,
      center = center,
      radius = radius,
      tileMode = TileMode.Clamp,
    ).also {
      cache.highlightBrush = it
      cache.highlightCenter = center
      cache.highlightRadius = radius
    }
  } else {
    cache.highlightBrush!!
  }
  drawRect(brush = brush)
}
