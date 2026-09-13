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
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.skydoves.cloudy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs

/** Executes the production skiko modifier and SKSL on an actual Skia raster Compose scene. */
internal class LiquidGlassZoomRasterTest :
  FunSpec({
    test("shader automatic and explicit identity preserve original pixels") {
      val original = renderZoom(enabled = false)
      renderZoom(zoom = 1f).contentEquals(original).shouldBe(true)
      renderZoom(zoom = 0f).contentEquals(original).shouldBe(true)
      // The auto sentinel must also preserve existing shader sampling with optical effects active.
      renderZoom(zoom = 0f, refract = true)
        .contentEquals(renderZoom(zoom = 1f, refract = true)).shouldBe(true)
    }

    test("explicit zoom follows the off-center lens and leaves outside pixels alone") {
      val original = renderZoom(enabled = false)
      val before = redCenter(original)
      for (center in listOf(Offset(84f, 96f), Offset(148.8f, 144f))) {
        val enlarged = renderZoom(zoom = 1.2f, center = center)
        val after = redCenter(enlarged)
        val expected = center + (before - center) * 1.2f
        check(abs(after.x - expected.x) <= 2f) { "x: expected $expected, got $after" }
        check(abs(after.y - expected.y) <= 2f) { "y: expected $expected, got $after" }
        check((after - before).getDistance() > 3f) { "Zoom did not move the marker" }
        assertOutside(original, enlarged, center)
      }
    }

    test("zoom composes with refraction and dispersion without blanking content") {
      val original = renderZoom(enabled = false)
      val zoomOnly = renderZoom(zoom = 1.2f)
      val combined = renderZoom(zoom = 1.2f, refract = true)
      var changed = 0
      for (y in 24 until 168) {
        for (x in 12 until 156) {
          val i = (y * ZOOM_SURFACE + x) * 4
          check(channel(combined, i + 3) >= 253) { "Opaque content became transparent" }
          if ((0..2).any { abs(channel(combined, i + it) - channel(zoomOnly, i + it)) > 8 }) {
            changed++
          }
        }
      }
      check(changed > 100) { "Refraction/dispersion had no visible effect ($changed pixels)" }
      check(redCenter(combined).x > 0f) { "Red marker disappeared" }
      assertOutside(original, combined, Offset(84f, 96f))
    }

    test("zoom preserves translucent content opacity") {
      val original = renderZoom(zoom = 1f, translucent = true)
      val enlarged = renderZoom(zoom = 1.2f, translucent = true)
      for (y in 132 until 144) {
        for (x in 96 until 106) {
          val i = (y * ZOOM_SURFACE + x) * 4
          for (c in 0..2) {
            check(abs(channel(enlarged, i + c) - 128) <= 2) { "Unexpected alpha composite" }
            check(abs(channel(enlarged, i + c) - channel(original, i + c)) <= 2)
          }
        }
      }
    }
  })

private const val ZOOM_SURFACE = 240

private fun renderZoom(
  zoom: Float = 1f,
  center: Offset = Offset(84f, 96f),
  enabled: Boolean = true,
  refract: Boolean = false,
  translucent: Boolean = false,
): ByteArray = ImageComposeScene(
  width = ZOOM_SURFACE,
  height = ZOOM_SURFACE,
  density = Density(1f),
) {
  Box(Modifier.size(ZOOM_SURFACE.dp).background(Color.White)) {
    Box(
      Modifier.fillMaxSize().liquidGlass(
        lensCenter = center,
        lensSize = Size(153.6f, 153.6f),
        cornerRadius = 9.6f,
        refraction = if (refract) 0.5f else 0f,
        curve = if (refract) 0.5f else 0f,
        dispersion = if (refract) 0.8f else 0f,
        saturation = 1f,
        contrast = 1f,
        tint = Color.Transparent,
        edge = 0f,
        glow = LiquidGlassDefaults.NoGlow,
        enabled = enabled,
        zoom = zoom,
      ),
    ) {
      Canvas(Modifier.fillMaxSize()) {
        drawRect(if (translucent) Color.Black.copy(alpha = 0.5f) else Color(0xFF202020))
        if (!translucent) {
          for (row in 0 until 20) {
            for (column in 0 until 20) {
              if ((row + column) % 2 == 0) {
                drawRect(Color.Gray, Offset(column * 12f, row * 12f), Size(12f, 12f))
              }
            }
          }
        }
        drawRect(Color.Red, Offset(117.6f, 112.8f), Size(9.6f, 9.6f))
        drawRect(Color.Blue, Offset(7.2f, 211.2f), Size(12f, 12f))
      }
    }
  }
}.use { scene ->
  scene.render().close()
  scene.render().use { image ->
    Bitmap().use { bitmap ->
      bitmap.allocPixels(
        ImageInfo(ZOOM_SURFACE, ZOOM_SURFACE, ColorType.RGBA_8888, ColorAlphaType.PREMUL),
      )
      check(image.readPixels(bitmap))
      bitmap.readPixels() ?: error("Bitmap.readPixels returned null")
    }
  }
}

private fun channel(pixels: ByteArray, index: Int): Int = pixels[index].toInt() and 0xFF

private fun redCenter(pixels: ByteArray): Offset {
  var xSum = 0f
  var ySum = 0f
  var count = 0
  for (y in 0 until ZOOM_SURFACE) {
    for (x in 0 until ZOOM_SURFACE) {
      val i = (y * ZOOM_SURFACE + x) * 4
      if (channel(pixels, i) > 216 && channel(pixels, i + 1) < 39 &&
        channel(pixels, i + 2) < 39
      ) {
        xSum += x + 0.5f
        ySum += y + 0.5f
        count++
      }
    }
  }
  check(count > 4) { "Red fixture marker not present" }
  return Offset(xSum / count, ySum / count)
}

private fun assertOutside(before: ByteArray, after: ByteArray, center: Offset) {
  for (y in 0 until ZOOM_SURFACE) {
    for (x in 0 until ZOOM_SURFACE) {
      if (abs(x + 0.5f - center.x) > 78.8f || abs(y + 0.5f - center.y) > 78.8f) {
        val i = (y * ZOOM_SURFACE + x) * 4
        for (c in 0..3) {
          check(abs(channel(before, i + c) - channel(after, i + c)) <= 2) {
            "Zoom modified outside lens at ($x, $y), channel $c"
          }
        }
      }
    }
  }
}
