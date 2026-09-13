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

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Real GPU/fallback captures; the same assertions run on API 27 and API 33+. */
@RunWith(AndroidJUnit4::class)
internal class LiquidGlassZoomTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  private data class Scene(
    val zoom: Float = 1f,
    val center: Offset = Offset(0.35f, 0.40f),
    val enabled: Boolean = true,
    val translucent: Boolean = false,
  )

  private var scene by mutableStateOf(Scene())
  private var contentSet = false

  @Test
  fun explicitZoom_movesFiducialAroundLensCenter_andPreservesOutside() {
    val identity = capture("identity", Scene())
    val enlarged = capture("zoom_1_2", Scene(zoom = 1.2f))
    assertMagnified(identity, enlarged, Scene().center)
    assertOutsideUnchanged(identity, enlarged, Scene().center)
  }

  @Test
  fun movingLens_updatesZoomPivot() {
    val identity = capture("moving_identity", Scene())
    val first = capture("moving_first", Scene(zoom = 1.2f))
    val movedCenter = Offset(0.62f, 0.60f)
    val moved = capture("moving_second", Scene(zoom = 1.2f, center = movedCenter))
    assertMagnified(identity, first, Scene().center)
    assertMagnified(identity, moved, movedCenter)
    assertOutsideUnchanged(identity, moved, movedCenter)
    assertTrue(
      "Moving the lens must move the red marker",
      abs(redCenter(first).x - redCenter(moved).x) > 0.04f,
    )
  }

  @Test
  fun automaticZoom_matchesExplicitPlatformDefault() {
    val automatic = capture("automatic", Scene(zoom = 0f))
    val expectedZoom = if (Build.VERSION.SDK_INT < 33) 1.03f else 1f
    val explicit = capture("automatic_explicit", Scene(zoom = expectedZoom))
    assertPixelsEqual(automatic, explicit)
    if (Build.VERSION.SDK_INT < 33) {
      val identity = capture("automatic_identity", Scene())
      // Assert visible movement too: equality alone could pass if both paths ignored zoom.
      assertTrue(
        "Automatic fallback zoom must magnify",
        redCenter(automatic).x - redCenter(identity).x > 0.002f,
      )
    }
  }

  @Test
  fun disabledZoom_preservesOriginalContent() {
    val original = capture("disabled_identity", Scene(enabled = false))
    val disabled = capture("disabled_zoom", Scene(zoom = 1.2f, enabled = false))
    assertPixelsEqual(original, disabled)
    val identity = capture("enabled_identity", Scene())
    assertPixelsEqual(original, identity)
  }

  @Test
  fun translucentContent_isNotCompositedTwiceInsideLens() {
    val identity = capture("translucent_identity", Scene(translucent = true))
    val enlarged = capture("translucent_zoom", Scene(zoom = 1.2f, translucent = true))
    assertMagnified(identity, enlarged, Scene().center)
    // This patch and its zoomed source contain only 50%-alpha black over white. Drawing the
    // unscaled content and then the scaled content would darken it from ~128 to ~64.
    for (y in 55..60) {
      for (x in 40..44) {
        val px = x * identity.width / 100
        val py = y * identity.height / 100
        assertColorNear("translucent patch ($px,$py)", identity[px, py], enlarged[px, py])
        assertEquals("single composite", 0.5f, enlarged[px, py].red, 0.02f)
      }
    }
    assertOutsideUnchanged(identity, enlarged, Scene().center)
  }

  private fun capture(name: String, configuration: Scene): PixelMap {
    composeTestRule.runOnIdle { scene = configuration }
    if (!contentSet) {
      composeTestRule.setContent {
        val current = scene
        val sidePx = with(LocalDensity.current) { 240.dp.toPx() }
        // Capture the parent so its white background participates in alpha compositing. All
        // fixture drawing is CHILD content of the glass Box, not a preceding background modifier.
        Box(Modifier.size(240.dp).testTag("zoom-root").background(Color.White)) {
          Box(
            Modifier.fillMaxSize().liquidGlass(
              lensCenter = current.center * sidePx,
              lensSize = Size(sidePx * 0.64f, sidePx * 0.64f),
              cornerRadius = sidePx * 0.04f,
              refraction = 0f,
              curve = 0f,
              dispersion = 0f,
              saturation = 1f,
              contrast = 1f,
              tint = Color.Transparent,
              edge = 0f,
              glow = LiquidGlassDefaults.NoGlow,
              enabled = current.enabled,
              zoom = current.zoom,
            ),
          ) {
            Canvas(Modifier.fillMaxSize()) {
              drawRect(
                if (current.translucent) Color.Black.copy(alpha = 0.5f) else Color(0xFF202020),
              )
              if (!current.translucent) {
                for (index in 1..9) {
                  val coordinate = size.width * index / 10f
                  drawLine(
                    Color.Gray,
                    Offset(coordinate, 0f),
                    Offset(coordinate, size.height),
                    strokeWidth = 1f,
                  )
                  drawLine(
                    Color.Gray,
                    Offset(0f, coordinate),
                    Offset(size.width, coordinate),
                    strokeWidth = 1f,
                  )
                }
              }
              drawRect(
                Color.Red,
                Offset(size.width * 0.49f, size.height * 0.47f),
                Size(size.width * 0.04f, size.height * 0.04f),
              )
              drawRect(
                Color.Green,
                Offset(size.width * 0.36f, size.height * 0.31f),
                Size(size.width * 0.04f, size.height * 0.04f),
              )
              // Blue registration mark is well outside either lens position.
              drawRect(
                Color.Blue,
                Offset(size.width * 0.03f, size.height * 0.88f),
                Size(size.width * 0.05f, size.height * 0.05f),
              )
            }
          }
        }
      }
      contentSet = true
    }
    composeTestRule.waitForIdle()
    val image = composeTestRule.onNodeWithTag("zoom-root").captureToImage()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(
      context.getExternalFilesDir(null) ?: context.filesDir,
      "liquid-glass-zoom/api-${Build.VERSION.SDK_INT}",
    )
    check(directory.isDirectory || directory.mkdirs())
    File(directory, "$name.png").outputStream().use { output ->
      check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
    }
    return image.toPixelMap()
  }

  private fun assertMagnified(identity: PixelMap, enlarged: PixelMap, center: Offset) {
    val before = redCenter(identity)
    val after = redCenter(enlarged)
    // Measured marker position supplies the input; this exercises the public modifier and GPU
    // output rather than calling or duplicating the production zoom resolver.
    val expected = center + (before - center) * 1.2f
    assertEquals("red marker x", expected.x, after.x, 2f / identity.width)
    assertEquals("red marker y", expected.y, after.y, 2f / identity.height)
    assertTrue("zoom must visibly move the marker", (after - before).getDistance() > 0.015f)
  }

  private fun redCenter(pixels: PixelMap): Offset {
    var xSum = 0.0
    var ySum = 0.0
    var count = 0
    for (y in 0 until pixels.height) {
      for (x in 0 until pixels.width) {
        val color = pixels[x, y]
        if (color.red > 0.85f && color.green < 0.15f && color.blue < 0.15f) {
          xSum += x + 0.5
          ySum += y + 0.5
          count++
        }
      }
    }
    assertTrue("Fixture red marker must be present", count > 4)
    return Offset(
      (xSum / count / pixels.width).toFloat(),
      (ySum / count / pixels.height).toFloat(),
    )
  }

  private fun assertOutsideUnchanged(before: PixelMap, after: PixelMap, center: Offset) {
    for (y in 0 until before.height) {
      for (x in 0 until before.width) {
        // Keep a two-pixel guard beyond the bounding rectangle for shader edge smoothing.
        val outside = abs(x + 0.5f - center.x * before.width) > before.width * 0.32f + 2f ||
          abs(y + 0.5f - center.y * before.height) > before.height * 0.32f + 2f
        if (outside) assertColorNear("outside lens ($x,$y)", before[x, y], after[x, y])
      }
    }
  }

  private fun assertPixelsEqual(before: PixelMap, after: PixelMap) {
    assertEquals(before.width, after.width)
    assertEquals(before.height, after.height)
    for (y in 0 until before.height) {
      for (x in 0 until before.width) {
        assertColorNear("pixel ($x,$y)", before[x, y], after[x, y])
      }
    }
  }

  private fun assertColorNear(message: String, expected: Color, actual: Color) {
    val tolerance = 2f / 255f
    assertEquals("$message red", expected.red, actual.red, tolerance)
    assertEquals("$message green", expected.green, actual.green, tolerance)
    assertEquals("$message blue", expected.blue, actual.blue, tolerance)
    assertEquals("$message alpha", expected.alpha, actual.alpha, tolerance)
  }
}
