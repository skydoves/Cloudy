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
@file:OptIn(ExperimentalMirage::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.skydoves.cloudy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs

/**
 * End-to-end skiko proof of the backdrop render path — the first test to actually drive
 * `Modifier.sky` + backdrop sampling (`Modifier.cloudy(sky = …)` / `Modifier.mirage(sky = …)`) through
 * a real Compose scene, not raw SKSL rasterization like [MirageChromaticRasterTest].
 *
 * It renders a fixed-size hierarchy into an [ImageComposeScene] (raster N32 surface, so no GPU) and
 * reads back the composed pixels. The sky recorder records the striped backdrop into
 * [Sky.backgroundLayer] during its draw and the descendant overlay samples it in the same draw pass,
 * so a single [ImageComposeScene.render] captures and samples — but `onGloballyPositioned` (which sets
 * [Sky.sourceBounds]) lands in the layout pass, and the overlay needs those bounds to offset into the
 * backdrop, so this pumps two frames before reading to keep the two-pass capture robust.
 *
 * The contracts asserted (all against the CURRENT skiko behavior, a safety net before the M2 blur
 * refactor):
 *  - radius-20 backdrop blur differs from radius-0 (the blur executed),
 *  - radius-0 equals the raw backdrop (passthrough),
 *  - a mirage colorize (`filter(Duotone)`) over the backdrop differs from the same node with the plan
 *    disabled, and the disabled render equals the raw backdrop (passthrough).
 */
internal class SkyBackdropRasterTest :
  FunSpec({

    test(
      "radius-20 backdrop blur differs from radius-0, and radius-0 is the raw backdrop passthrough",
    ) {
      val raw = renderScene { StripedBackdrop() }
      val radius0 = renderScene { CloudyCard(radius = 0) }
      val radius20 = renderScene { CloudyCard(radius = 20) }

      // radius-0 draws the backdrop region unblurred: the card region must match the raw backdrop.
      cardMeanAbsDiff(radius0, raw).shouldBe(0.0)
      // radius-20 blurs the striped backdrop inside the card: the stripes soften, so the card pixels
      // must move measurably away from the sharp radius-0 render.
      cardMeanAbsDiff(radius20, radius0).shouldBeGreaterThan(1.0)
    }

    test("a tint over the radius-0 backdrop changes the card pixels") {
      // The Android instrumented path can't screenshot a tinted backdrop (captureToImage of the
      // compositing tint over the sky layer SIGSEGVs the RenderThread — see SkyBackdropScreenshotTest),
      // so the tint pixel contract is asserted here on skiko where the capture is safe.
      val untinted = renderScene { CloudyCard(radius = 0) }
      val tinted = renderScene { CloudyCard(radius = 0, tint = Color.White.copy(alpha = 0.4f)) }
      cardMeanAbsDiff(tinted, untinted).shouldBeGreaterThan(1.0)
    }

    test(
      "mirage backdrop colorize grades the backdrop, and disabled is the raw backdrop passthrough",
    ) {
      val raw = renderScene { StripedBackdrop() }
      val disabled = renderScene { MirageCard(enabled = false) }
      val graded = renderScene { MirageCard(enabled = true) }

      // A bypassed plan draws the node content over the raw backdrop unchanged in the card region.
      cardMeanAbsDiff(disabled, raw).shouldBe(0.0)
      // The Duotone colorize remaps the backdrop luminance, so the graded card must differ from both
      // the disabled render and the raw backdrop.
      cardMeanAbsDiff(graded, disabled).shouldBeGreaterThan(1.0)
    }
  })

private const val SURFACE = 240
private const val CARD_W = 120
private const val CARD_H = 80

/** Sky container with a high-contrast striped backdrop; [overlay] is the sampling card (if any). */
@Composable
private fun BackdropScene(overlay: @Composable (Sky) -> Unit) {
  val sky = rememberSky()
  Box(
    modifier = Modifier.size(SURFACE.dp).sky(sky),
    contentAlignment = Alignment.Center,
  ) {
    // High-frequency horizontal stripes so blur is measurably distinguishable from no-blur.
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF222244)))
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stripe = size.height / 24f
      var y = 0f
      var on = true
      while (y < size.height) {
        if (on) {
          drawRect(color = Color.White, topLeft = Offset(0f, y), size = Size(size.width, stripe))
        }
        y += stripe
        on = !on
      }
    }
    overlay(sky)
  }
}

/** Just the backdrop, no sampling node — the passthrough reference. */
@Composable
private fun StripedBackdrop() = BackdropScene { }

@Composable
private fun CloudyCard(radius: Int, tint: Color = Color.Transparent) = BackdropScene { sky ->
  Box(
    modifier = Modifier.size(CARD_W.dp, CARD_H.dp).cloudy(sky = sky, radius = radius, tint = tint),
  )
}

@Composable
private fun MirageCard(enabled: Boolean) = BackdropScene { sky ->
  Box(
    modifier = Modifier
      .size(CARD_W.dp, CARD_H.dp)
      .mirage(sky = sky, enabled = enabled) { filter(MirageShaders.Duotone) },
  )
}

/**
 * Renders [content] into a [SURFACE]-square raster scene and returns its RGBA_8888 bytes. Pumps two
 * frames so the layout pass has set [Sky.sourceBounds] before the overlay samples the backdrop.
 */
private fun renderScene(content: @Composable () -> Unit): ByteArray =
  ImageComposeScene(width = SURFACE, height = SURFACE, density = Density(1f), content = content)
    .use { scene ->
      scene.render()
      imageBytes(scene.render())
    }

/** Reads an [Image] into a PREMUL RGBA_8888 byte buffer. */
private fun imageBytes(image: Image): ByteArray {
  val info = ImageInfo(SURFACE, SURFACE, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
  val bitmap = Bitmap().apply { allocPixels(info) }
  require(image.readPixels(bitmap)) { "Image.readPixels returned false" }
  return bitmap.readPixels() ?: error("Bitmap.readPixels returned null")
}

/**
 * Mean absolute per-byte difference over the centered [CARD_W]x[CARD_H] card region only — the region
 * the sampling node covers. Comparing the whole surface would dilute the card-local effect with the
 * unchanged surrounding backdrop.
 */
private fun cardMeanAbsDiff(a: ByteArray, b: ByteArray): Double {
  require(a.size == b.size) { "buffers differ in size: ${a.size} vs ${b.size}" }
  val left = (SURFACE - CARD_W) / 2
  val top = (SURFACE - CARD_H) / 2
  var sum = 0L
  var count = 0L
  for (y in top until top + CARD_H) {
    for (x in left until left + CARD_W) {
      val i = (y * SURFACE + x) * 4
      for (c in 0 until 4) {
        sum += abs((a[i + c].toInt() and 0xFF) - (b[i + c].toInt() and 0xFF))
        count++
      }
    }
  }
  return sum.toDouble() / count
}
