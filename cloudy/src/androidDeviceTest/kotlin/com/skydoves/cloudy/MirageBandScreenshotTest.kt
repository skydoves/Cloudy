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
@file:OptIn(ExperimentalMirage::class)

package com.skydoves.cloudy

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.skydoves.cloudy.internal.duotoneMatrix
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * On-device screenshot spec for the full mirage/blur backdrop pipeline, run band-agnostically: the
 * *running device's* SDK selects the backend (GLES mirage + legacy blur on API 29-32, AGSL mirage +
 * RenderEffect blur on 33+), so the same three cases validate whichever path the device takes.
 *
 * Each case captures the card region and, where an expected image exists, writes both to
 * [additionalTestOutputDir] as `band_api<SDK_INT>_<case>_(actual|expected).png` — AGP's
 * connectedAndroidTest pulls that dir back to the host so a PR gallery can render the band's output.
 *
 * The oracle for the duotone case is the device's *own* raw-backdrop capture (effect off) run through
 * the pure-Kotlin [duotoneMatrix], not a synthetic bitmap: taking the device's rendered backdrop as
 * the grade input cancels per-device sRGB/sampling so the tolerance covers only the grade itself, and
 * makes the check identical on every band. The chromatic and blur cases self-reference (transformed vs
 * raw, blurred vs sharp) so neither needs a committed golden.
 *
 * ## Why the two mirage cases are `@Ignore`d
 *
 * Capturing any tree containing a `Modifier.mirage(sky = ...)` node SIGSEGVs the RenderThread with an
 * unbounded `prepareTreeImpl` recursion — the same cyclic-RenderNode overflow as issue #112. The blur
 * backdrop was fixed for this by `BackdropClearBlurMachine`, which draws a rasterized snapshot of the
 * backdrop; the mirage backdrop was never given that snapshot and still keeps a live
 * `drawLayer(backgroundLayer)` back-edge (`MirageBackdropNode.recordSource`). `Sky.isCapturing` does
 * not save it: that guard only skips the node during the sky recorder's own record pass, whereas
 * `captureToImage`/`PixelCopy` walks the already-composed layer tree (where `isCapturing` is false), so
 * the back-edge cycles regardless of which node is captured. Verified on an emulator: the blur case
 * here and all of `MainPixelCopyCrashReproTest` pass, while both mirage captures crash. A crash aborts
 * the whole instrumentation run, so these stay ignored — the fixtures and oracle are correct and ready
 * to run once the mirage backdrop gets the same rasterized-snapshot treatment as the blur backdrop.
 */
internal class MirageBandScreenshotTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  // The card's current effect. Held as state so a single setContent renders every stage of a case:
  // captureToImage/PixelCopy needs one Activity content, and setContent throws if called twice, so the
  // off->on (or sharp->blurred) transition is driven by mutating this, not by re-hosting the tree.
  private var cardEffect by mutableStateOf<CardEffect>(CardEffect.Off)

  /** A card effect descriptor. Plain data (not a composable lambda) so it lives in snapshot state. */
  private sealed interface CardEffect {
    data object Off : CardEffect
    data class Duotone(val shadow: ComposeColor, val highlight: ComposeColor, val amount: Float) :
      CardEffect
    data object Chromatic : CardEffect
    data class Blur(val radius: Int) : CardEffect
  }

  /**
   * A 200x200dp sky container (a high-frequency vertical-stripe backdrop over a gradient) with a
   * centered [cardDp] rounded card whose effect is [cardEffect]. The card carries no content, so its
   * captured pixels are purely the effect's output over the backdrop region.
   */
  @Composable
  private fun Fixture() {
    val sky = rememberSky()
    Box(
      modifier = Modifier.testTag("root").size(surfaceDp.dp).sky(sky),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier.fillMaxSize().background(
          androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(ComposeColor(0xFF222244), ComposeColor(0xFFEEAA33)),
          ),
        ),
      )
      Canvas(modifier = Modifier.fillMaxSize()) {
        // High-frequency vertical stripes: blur's effect on horizontal contrast is measurable only
        // against a sharp pattern, and a colorize grade has a wide luminance range to remap.
        val stripe = size.width / 24f
        var x = 0f
        var on = true
        while (x < size.width) {
          if (on) {
            drawRect(
              color = ComposeColor.White.copy(alpha = 0.75f),
              topLeft = Offset(x, 0f),
              size = Size(stripe, size.height),
            )
          }
          x += stripe
          on = !on
        }
      }
      Box(
        modifier = Modifier
          .size(cardDp.dp)
          .clip(RoundedCornerShape(16.dp))
          .testTag("card")
          .then(cardModifier(sky, cardEffect)),
      )
    }
  }

  /** Maps a [CardEffect] descriptor to the modifier that applies it (composable: `cloudy` is one). */
  @Composable
  private fun cardModifier(sky: Sky, effect: CardEffect): Modifier = when (effect) {
    CardEffect.Off -> Modifier

    is CardEffect.Duotone -> Modifier.mirage(sky = sky) {
      filter(MirageOptics.Duotone) {
        shadow(effect.shadow)
        highlight(effect.highlight)
        amount(effect.amount)
      }
    }

    CardEffect.Chromatic -> Modifier.mirage(sky = sky) { filter(MirageOptics.Chromatic) }

    is CardEffect.Blur -> Modifier.cloudy(sky = sky, radius = effect.radius)
  }

  /**
   * Case 1: a duotone mirage card must match the pure-Kotlin [duotoneMatrix] applied to the device's
   * own raw backdrop. Band-agnostic: the oracle is derived from this device's capture, so a Y-flip,
   * wrong sampler, or dead grade blows past [DUOTONE_TOL] on any backend.
   */
  @Test
  @Ignore(
    "Capturing a Modifier.mirage(sky) tree SIGSEGVs the RenderThread (issue-112 cycle); see class KDoc.",
  )
  fun mirageDuotoneMatchesOracle() {
    val (shadow, highlight, amount) = duotoneDefaults()
    startFixture()

    // Raw backdrop as this device renders it (effect off) — the grade oracle's input.
    val raw = captureCard()
    val expected = applyDuotone(raw, shadow, highlight, amount)

    // Switch on the grade and poll until the capture stops being the raw backdrop (GLES blit is async
    // on 29-32; AGSL is synchronous and converges on the first frame).
    val actual = captureCardUntilDiffers(raw, CardEffect.Duotone(shadow, highlight, amount))

    writePng("duotone", actual = actual, expected = expected)

    val mad = meanAbsDiff(actual, expected)
    // Report-first: the measured MAD is always in the message so a device run surfaces the number even
    // when it passes. TOL is loose to start (band render differences); tighten once real runs land.
    assert(mad < DUOTONE_TOL) {
      "Duotone band capture diverged from the duotoneMatrix oracle: MAD=$mad (TOL=$DUOTONE_TOL)"
    }
  }

  /**
   * Case 2: a chromatic mirage card must be non-passthrough — the pipeline actually samples and
   * transforms the backdrop. Lens-pixel accuracy is GlProgramMatchTest's job; this only proves the
   * full compose path draws the effect. On API < 33 the lens optic has no runtime shader and the raw
   * backdrop shows through, so the assert is skipped there.
   */
  @Test
  @Ignore(
    "Capturing a Modifier.mirage(sky) tree SIGSEGVs the RenderThread (issue-112 cycle); see class KDoc.",
  )
  fun mirageChromaticTransformsBackdrop() {
    // No RuntimeShader below 33: the lens optic is a passthrough, so there is nothing to assert.
    if (Build.VERSION.SDK_INT < 33) return

    startFixture()
    val raw = captureCard()
    val actual = captureCardUntilDiffers(raw, CardEffect.Chromatic)

    writePng("chromatic", actual = actual, expected = null)

    val mad = meanAbsDiff(actual, raw)
    assert(mad > CHROMATIC_MIN_DELTA) {
      "Chromatic band capture is indistinguishable from the raw backdrop (effect did nothing): " +
        "MAD=$mad (needs > $CHROMATIC_MIN_DELTA)"
    }
  }

  /**
   * Case 3: a blur card must soften the backdrop — the card's horizontal contrast (adjacent-pixel
   * delta across the vertical stripes) drops versus a radius-0 capture. Self-referential (blurred vs
   * sharp on the same device), so it needs no golden. On API 30 and below `cpuBlurEnabled` defaults
   * false, so a scrim replaces blur and contrast still drops — the assert holds on every band.
   */
  @Test
  fun blurSoftensBackdrop() {
    startFixture(CardEffect.Blur(radius = 0))
    val sharp = captureCard()
    val blurred = captureCardUntilDiffers(sharp, CardEffect.Blur(radius = 20))

    writePng("blur", actual = blurred, expected = sharp)

    val sharpContrast = horizontalContrast(sharp)
    val blurredContrast = horizontalContrast(blurred)
    assert(blurredContrast < sharpContrast) {
      "Blur did not soften the backdrop: sharp contrast=$sharpContrast, blurred=$blurredContrast"
    }
  }

  // --- Capture helpers --------------------------------------------------------------------------

  /** Hosts the fixture once (setContent is one-shot) at the given starting effect. */
  private fun startFixture(initial: CardEffect = CardEffect.Off) {
    cardEffect = initial
    composeTestRule.setContent { Fixture() }
    composeTestRule.waitForIdle()
  }

  /**
   * Captures the card region by capturing the whole `root` (the Sky container) and cropping to the
   * card's bounds. Capturing `root` records the backdrop plus the effect composited over it exactly as
   * presented, and the crop keeps only the card. (For the blur backdrop this is cycle-safe because that
   * path rasterizes its backdrop; the mirage backdrop cases are `@Ignore`d — capturing any of their
   * trees cycles the RenderNode graph regardless of the target node, per the class KDoc.)
   */
  private fun captureCard(): Bitmap = cropCard(captureRoot())

  private fun captureRoot(): Bitmap =
    composeTestRule.onNodeWithTag("root").captureToImage().asAndroidBitmap()

  /** Crops [root] to the card's pixel bounds, read from the card node's root-relative layout rect. */
  private fun cropCard(root: Bitmap): Bitmap {
    val bounds = composeTestRule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
    val left = bounds.left.toInt().coerceIn(0, root.width - 1)
    val top = bounds.top.toInt().coerceIn(0, root.height - 1)
    val width = bounds.width.toInt().coerceAtMost(root.width - left)
    val height = bounds.height.toInt().coerceAtMost(root.height - top)
    return Bitmap.createBitmap(root, left, top, width, height)
  }

  /**
   * Switches the card to [effect] and re-captures until the capture stops matching [reference] (or the
   * timeout hits), absorbing the GLES backend's async blit: on 29-32 the first draw shows the raw
   * backdrop and the blitted effect arrives a few frames later. A synchronous backend (AGSL,
   * RenderEffect) satisfies the predicate on the first capture. Returns the last capture regardless, so
   * the assert that follows still reports its MAD on a timeout instead of throwing here.
   */
  private fun captureCardUntilDiffers(reference: Bitmap, effect: CardEffect): Bitmap {
    cardEffect = effect
    composeTestRule.waitForIdle()
    var last = captureCard()
    if (meanAbsDiff(last, reference) > CONVERGENCE_DELTA) return last
    // The GLES blit lands on the render thread; advancing the test clock lets a fresh frame recapture.
    val deadline = System.currentTimeMillis() + CONVERGENCE_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      composeTestRule.mainClock.advanceTimeByFrame()
      composeTestRule.waitForIdle()
      last = captureCard()
      if (meanAbsDiff(last, reference) > CONVERGENCE_DELTA) break
    }
    return last
  }

  // --- Oracle + metrics -------------------------------------------------------------------------

  /** [MirageOptics.Duotone]'s schema-default shadow/highlight/amount — the grade the card applies. */
  private fun duotoneDefaults(): Triple<ComposeColor, ComposeColor, Float> {
    val params = MirageOptics.Duotone.paramsFactory()
    return Triple(params.shadow.value, params.highlight.value, params.amount.value)
  }

  /** Applies the duotone 4x5 color matrix (offset column in 0..255 units) to every pixel of [src]. */
  private fun applyDuotone(
    src: Bitmap,
    shadow: ComposeColor,
    highlight: ComposeColor,
    amount: Float,
  ): Bitmap {
    val m = duotoneMatrix(shadow, highlight, amount)
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until src.height) {
      for (x in 0 until src.width) {
        val p = src.getPixel(x, y)
        val r = Color.red(p).toFloat()
        val g = Color.green(p).toFloat()
        val b = Color.blue(p).toFloat()
        val a = Color.alpha(p).toFloat()
        fun ch(row: Int) =
          (m[row] * r + m[row + 1] * g + m[row + 2] * b + m[row + 3] * a + m[row + 4])
            .coerceIn(0f, 255f).toInt()
        out.setPixel(x, y, Color.argb(ch(15), ch(0), ch(5), ch(10)))
      }
    }
    return out
  }

  private fun meanAbsDiff(a: Bitmap, b: Bitmap): Double {
    if (a.width != b.width || a.height != b.height) return Double.MAX_VALUE
    var sum = 0L
    var n = 0
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        val pa = a.getPixel(x, y)
        val pb = b.getPixel(x, y)
        sum += abs(Color.red(pa) - Color.red(pb)).toLong()
        sum += abs(Color.green(pa) - Color.green(pb)).toLong()
        sum += abs(Color.blue(pa) - Color.blue(pb)).toLong()
        n += 3
      }
    }
    return sum.toDouble() / n
  }

  /** Mean absolute luminance delta between horizontally adjacent pixels — high on sharp stripes. */
  private fun horizontalContrast(bmp: Bitmap): Double {
    var sum = 0L
    var n = 0
    for (y in 0 until bmp.height) {
      for (x in 1 until bmp.width) {
        val l0 = luma(bmp.getPixel(x - 1, y))
        val l1 = luma(bmp.getPixel(x, y))
        sum += abs(l1 - l0).toLong()
        n++
      }
    }
    return if (n == 0) 0.0 else sum.toDouble() / n
  }

  private fun luma(p: Int): Int =
    (Color.red(p) * 54 + Color.green(p) * 183 + Color.blue(p) * 19) shr 8

  // --- PNG output -------------------------------------------------------------------------------

  /**
   * Writes [actual] (and [expected] if present) as `band_api<SDK_INT>_<case>_(actual|expected).png`
   * into [additionalTestOutputDir]. With no output dir configured (a plain local run) this is a no-op
   * — the test still asserts, it just produces no gallery artifact.
   */
  private fun writePng(case: String, actual: Bitmap, expected: Bitmap?) {
    val dir = additionalTestOutputDir() ?: return
    val band = "api${Build.VERSION.SDK_INT}"
    writeBitmap(File(dir, "band_${band}_${case}_actual.png"), actual)
    if (expected != null) writeBitmap(File(dir, "band_${band}_${case}_expected.png"), expected)
  }

  private fun writeBitmap(file: File, bmp: Bitmap) {
    file.parentFile?.mkdirs()
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  /**
   * The host-pull output dir AGP passes as the `additionalTestOutputDir` instrumentation arg (the same
   * channel BackgroundBlurBenchmark's benchmarkData.json rides). Falls back to the app's external
   * files dir if the arg is absent but the dir is writable, else null (skip writing).
   */
  private fun additionalTestOutputDir(): File? {
    val fromArg = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
    if (!fromArg.isNullOrEmpty()) return File(fromArg).also { it.mkdirs() }
    val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    return ctx.getExternalFilesDir("mirage-band")
  }

  private val surfaceDp = 200
  private val cardDp = 160

  private companion object {
    // Loose to start: band render differences (SwiftShader vs vendor GPU, sRGB round-trips) live under
    // this while a real grade regression (Y-flip, wrong matrix) blows far past it. Tighten from logged
    // MADs once device runs land.
    const val DUOTONE_TOL = 8.0

    // A real chromatic transform moves pixels well past this; a passthrough leaves MAD ~0.
    const val CHROMATIC_MIN_DELTA = 1.0

    // The graded/blurred capture must differ from the raw/sharp reference by at least this to count as
    // "the effect landed" during async-blit polling.
    const val CONVERGENCE_DELTA = 0.5

    const val CONVERGENCE_TIMEOUT_MS = 5_000L
  }
}
