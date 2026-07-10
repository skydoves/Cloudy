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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Root-relative directory (under the `androidHostTest` assets source set) where Roborazzi
 * golden PNGs for Cloudy are recorded and verified.
 *
 * The KMP-library plugin maps `src/androidHostTest/assets` into the host-test assets by
 * convention, so a path rooted here resolves to `cloudy/src/androidHostTest/assets/screenshots/`.
 */
internal const val SCREENSHOT_DIR: String = "src/androidHostTest/assets/screenshots"

/**
 * Tolerant pixel comparator shared by every Cloudy golden.
 *
 * `maxDistance` is the maximum normalized per-pixel color distance that still counts as a
 * match, and `hShift`/`vShift` allow a pixel to match a neighbour within +/- 2px. This absorbs
 * the sub-pixel anti-aliasing and rasterizer jitter that Robolectric Native Graphics produces
 * across runs without masking real, radius-scale blur differences.
 *
 * Verified signature (roborazzi 1.64.0 -> com.dropbox.differ:differ-jvm:0.3.0):
 * `SimpleImageComparator(maxDistance: Float, hShift: Int, vShift: Int)`.
 */
internal val cloudyRoborazziOptions: RoborazziOptions = RoborazziOptions(
  compareOptions = RoborazziOptions.CompareOptions(
    imageComparator = SimpleImageComparator(maxDistance = 0.01f, hShift = 2, vShift = 2),
  ),
)

/**
 * Wraps [content] in a fixed-size, fully deterministic surface so that golden captures are
 * stable across hosts: no system insets, no theme, no fonts, no animation.
 *
 * @param size The square edge length (dp) of the capture surface. Defaults to 200dp.
 * @param background Solid backdrop color painted behind [content].
 */
@Composable
internal fun ScreenshotSurface(
  size: Int = 200,
  background: Color = Color(0xFF3366CC),
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier
      .size(size.dp)
      .background(background),
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/**
 * The shared synthetic fixture for the passthrough golden suites: an 80dp hard-edged white square
 * blurred by `Modifier.cloudy`. The hard edge against the surrounding [ScreenshotSurface] backdrop
 * is what makes the blur visibly diffuse, so radius/enabled changes produce a measurable pixel diff.
 *
 * Used by the smoke, passthrough, and state suites (state passes `enabled`); keeping it here avoids
 * three byte-for-byte copies and guarantees the goldens across suites describe the same shape.
 *
 * @param radius blur radius passed to `Modifier.cloudy`.
 * @param enabled whether the blur is enabled (`false` returns the receiver unchanged -> sharp).
 */
@Composable
internal fun BlurSquareFixture(radius: Int, enabled: Boolean = true) {
  Box(
    modifier = Modifier
      .size(80.dp)
      .cloudy(radius = radius, enabled = enabled)
      .background(Color.White),
  )
}

/**
 * Renders [content] inside a [ScreenshotSurface] and records a Roborazzi golden.
 *
 * Uses the `roborazzi-compose` `captureRoboImage(filePath, roborazziOptions) { content }` overload,
 * which launches its own Robolectric `ComponentActivity` internally (`launchRoborazziActivity`) and
 * settles the composition before capture. This is why no `ComposeContentTestRule` and no test
 * `AndroidManifest.xml` (declaring `ComponentActivity`) are required — the alternative
 * `createComposeRule()` path needs that activity registered, which this module does not ship.
 *
 * Determinism contract: no `Thread.sleep`; the compose overload drives the host frame clock to
 * idle (flushing any `LaunchedEffect` the blur facade uses) before it rasterizes.
 *
 * @param goldenName File name (without directory) of the golden, e.g. `"smoke_radius0.png"`.
 * @param content The composable to render inside a [ScreenshotSurface] and capture.
 */
internal fun captureCloudyGolden(goldenName: String, content: @Composable () -> Unit) {
  captureRoboImage(
    filePath = "$SCREENSHOT_DIR/$goldenName",
    roborazziOptions = cloudyRoborazziOptions,
  ) {
    ScreenshotSurface { content() }
  }
}

/** Absolute golden [File] for [goldenName] under the recorded screenshots directory. */
internal fun goldenFile(goldenName: String): File = File("$SCREENSHOT_DIR/$goldenName")

/** Decodes a recorded golden PNG into a [BufferedImage] for pixel-level gate assertions. */
internal fun readGolden(goldenName: String): BufferedImage {
  val file = goldenFile(goldenName)
  require(file.exists()) { "Golden not found (record first): ${file.absolutePath}" }
  return requireNotNull(ImageIO.read(file)) { "Failed to decode golden PNG: ${file.absolutePath}" }
}

/** Number of distinct ARGB colors in [image], capped at [cap] for cheap blank detection. */
internal fun distinctColors(image: BufferedImage, cap: Int = 2): Int {
  val seen = HashSet<Int>()
  for (y in 0 until image.height) {
    for (x in 0 until image.width) {
      seen.add(image.getRGB(x, y))
      if (seen.size >= cap) return seen.size
    }
  }
  return seen.size
}

/**
 * Fraction of pixels whose per-channel ARGB value differs by more than [channelEps] between
 * [a] and [b]. Both images must share dimensions.
 */
internal fun pixelDiffRatio(a: BufferedImage, b: BufferedImage, channelEps: Int = 5): Float {
  require(a.width == b.width && a.height == b.height) {
    "image size mismatch: ${a.width}x${a.height} vs ${b.width}x${b.height}"
  }
  var diff = 0
  val total = a.width * a.height
  for (y in 0 until a.height) {
    for (x in 0 until a.width) {
      val pa = a.getRGB(x, y)
      val pb = b.getRGB(x, y)
      if (channelsDiffer(pa, pb, channelEps)) diff++
    }
  }
  return diff.toFloat() / total
}

private fun channelsDiffer(a: Int, b: Int, eps: Int): Boolean {
  val da = (a ushr 24 and 0xFF) - (b ushr 24 and 0xFF)
  val dr = (a ushr 16 and 0xFF) - (b ushr 16 and 0xFF)
  val dg = (a ushr 8 and 0xFF) - (b ushr 8 and 0xFF)
  val db = (a and 0xFF) - (b and 0xFF)
  return kotlin.math.abs(da) > eps ||
    kotlin.math.abs(dr) > eps ||
    kotlin.math.abs(dg) > eps ||
    kotlin.math.abs(db) > eps
}
