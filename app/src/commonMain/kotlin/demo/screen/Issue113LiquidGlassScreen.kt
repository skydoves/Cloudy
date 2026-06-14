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
package demo.screen

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skydoves.cloudy.liquidGlass
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens

/**
 * Reproduction of https://github.com/skydoves/cloudy/issues/113.
 *
 * The reporter places a `Card` at the screen center with `Box(contentAlignment = Center)` and
 * applies `Modifier.liquidGlass`, expecting the glass lens to sit in the middle of the card.
 * Instead the lens hugs the top-left corner.
 *
 * The cause is the `lensCenter` coordinate space. `lensCenter` is expressed in the LOCAL pixel
 * space of the element the modifier is attached to (origin at that element's top-left), and it is
 * left at [Offset.Zero] until the first drag. So before any interaction the lens centers on the
 * card's (0, 0) corner, and a `lensSize` larger than the card (400x200 over a 300dp card) spills
 * most of the lens outside the card — only the top-left sliver shows the effect. The card itself
 * is centered correctly; `liquidGlass` never affects layout.
 *
 * The [seedCenter] toggle reproduces both states on one screen:
 *   - OFF  -> the reporter's code verbatim: lens stuck at the top-left corner (the bug).
 *   - ON   -> the fix the library demo already uses: seed `lensCenter` to the element's center via
 *             [Modifier.onSizeChanged] once it is measured, and keep `lensSize` within the element.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Issue113LiquidGlassScreen(onBackClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }

  // When false, mirrors the issue report exactly (lens never seeded, oversized lens).
  // When true, applies the demo's recommended usage (seed to center, lens within bounds).
  var seedCenter by remember { mutableStateOf(false) }

  // The reporter starts the lens at Offset.Zero and only updates it on drag.
  var lensCenter by remember { mutableStateOf(Offset.Zero) }

  CollapsingAppBarScaffold(
    title = "Issue #113 — Liquid Glass",
    onBackClick = onBackClick,
  ) { paddingValues, _ ->
    MaxWidthContainer(modifier = Modifier.padding(paddingValues)) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        // Background image fills the screen (stands in for the reporter's R.drawable.image).
        CoilImage(
          modifier = Modifier.fillMaxSize(),
          imageModel = { poster.image },
          imageOptions = ImageOptions(contentScale = ContentScale.Crop),
        )

        // The card the reporter centers and applies liquidGlass to.
        Card(
          modifier = Modifier
            .size(300.dp)
            .onSizeChanged { size ->
              // Demo's recommended fix: seed the lens to the element center once measured.
              if (seedCenter && lensCenter == Offset.Zero) {
                lensCenter = Offset(size.width / 2f, size.height / 2f)
              }
            }
            .liquidGlass(
              lensCenter = lensCenter,
              // Within bounds when seeding; the reporter's oversized 400x200 otherwise.
              lensSize = if (seedCenter) Size(220f, 220f) else Size(400f, 200f),
              cornerRadius = 30f,
              refraction = 0.27f,
              curve = 1.0f,
              dispersion = 0.01f,
              edge = 0.0f,
              saturation = 1.64f,
            )
            .pointerInput(Unit) {
              detectDragGestures { change, _ ->
                change.consume()
                lensCenter = change.position
              }
            },
        ) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              "This is a text",
              modifier = Modifier.fillMaxWidth(),
              textAlign = TextAlign.Center,
            )
          }
        }

        // Toggle between the reporter's code (off) and the recommended usage (on).
        Row(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(Dimens.contentPadding),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(if (seedCenter) "Seeded to center" else "Reporter's code (lens at 0,0)")
          Spacer(modifier = Modifier.width(Dimens.itemSpacing))
          Switch(
            checked = seedCenter,
            onCheckedChange = { seedCenter = it },
          )
        }
      }
    }
  }
}
