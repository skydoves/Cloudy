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
@file:OptIn(
  com.skydoves.cloudy.ExperimentalMirage::class,
  com.skydoves.cloudy.ExperimentalLiquidGlassMotion::class,
)

package demo.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.FilterOptic
import com.skydoves.cloudy.MirageLensParams
import com.skydoves.cloudy.MirageOptics
import com.skydoves.cloudy.mirage
import com.skydoves.cloudy.rememberGyroLightSource
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens

/**
 * Picks the bundled [MirageOptics] look applied by the demo's chips. Each is a filter optic; the Foil
 * overlay is offered through the chaining toggle instead of the filter picker.
 */
private enum class MiragePick(val label: String, val optic: FilterOptic<out MirageLensParams>) {
  Specular("Specular", MirageOptics.Specular),
  Chromatic("Iridescent", MirageOptics.Chromatic),
  OilSlick("Oil Slick", MirageOptics.OilSlick),
  SoapBubble("Soap Bubble", MirageOptics.SoapBubble),
  MetallicFoil("Metallic Foil", MirageOptics.MetallicFoil),
  Pearl("Pearl", MirageOptics.Pearl),
}

/**
 * Showcases the open [mirage] plan mechanism: an arbitrary poster gets an effect from a single
 * `Modifier.mirage { … }` block, no bespoke component.
 *
 * Two things are demonstrated:
 * - **Optic catalog:** chips swap between the bundled looks ([MirageOptics.Specular] / the thin-film
 *   family), each a distinct GPU program proving the parameterized shader renders all looks with no
 *   core changes.
 * - **Overlay over filter:** the toggle adds an `overlay(MirageOptics.Foil)` stage on top of an
 *   `filter(MirageOptics.OilSlick)` stage, so the foil overlay composites over the refracted content —
 *   the plan orders it correctly regardless of declaration position.
 *
 * The lens center is seeded to the pane center via `onSizeChanged` (an `Offset.Zero` center would pin
 * the lens to the corner) and passed into each optic's `lensCenter` uniform in the per-draw params
 * block; the shared [LENS] / [CORNER] framing likewise.
 *
 * The gyro toggle keeps [rememberGyroLightSource] wired (registering the sensor), but the optic's
 * `iLight` stays at its default direction — the plan API reads a light direction from the params block
 * and the motion holder's direction is not part of the public surface yet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MirageScreen(onBackClick: () -> Unit) {
  var pick by remember { mutableStateOf(MiragePick.Specular) }
  var gyroEnabled by remember { mutableStateOf(true) }
  var chained by remember { mutableStateOf(false) }
  var lensCenter by remember { mutableStateOf(Offset.Zero) }

  // Keeps the sensor path exercised; the direction holder is not read into iLight (see the KDoc).
  rememberGyroLightSource(enabled = gyroEnabled)

  // Sets the shared lens framing (center + size + corner) into any lens-shaped optic's params. Read
  // the seeded center into a local so the params-receiver's `lensCenter` handle does not shadow it.
  val center = lensCenter
  val lensFraming: MirageLensParams.() -> Unit = {
    lensCenter(center)
    lensSize(LENS)
    cornerRadius(CORNER)
  }

  CollapsingAppBarScaffold(
    title = "Mirage (plan)",
    onBackClick = onBackClick,
  ) { paddingValues, _ ->
    MaxWidthContainer(modifier = Modifier.padding(paddingValues)) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(Dimens.contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text =
          "One Modifier.mirage { } block applies an open optic plan to any content. Pick a " +
            "look, or enable chaining to overlay Foil over a refracting Oil Slick. Android 13+ / Skia.",
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          textAlign = TextAlign.Center,
        )

        PaneLabel(
          if (chained) {
            "mirage { filter(OilSlick); overlay(Foil) } — overlay over refraction"
          } else {
            "mirage { filter(MirageOptics.${pick.name}) }"
          },
        )

        PosterPane(onCentered = { lensCenter = it }) { paneModifier ->
          if (chained) {
            // A filter stage refracts the content; the overlay stage composites Foil on top of the
            // filtered result (the plan orders overlays over filters).
            paneModifier.mirage {
              filter(MirageOptics.OilSlick) { lensFraming() }
              overlay(MirageOptics.Foil) { lensFraming() }
            }
          } else {
            paneModifier.mirage {
              filter(pick.optic) { lensFraming() }
            }
          }
        }

        Card(
          modifier = Modifier.fillMaxWidth(),
          elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation),
          shape = RoundedCornerShape(Dimens.cardCornerRadius),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.padding(Dimens.contentPadding)) {
            Text(
              text = "Recipe",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              MiragePick.entries.forEach { entry ->
                FilterChip(
                  selected = !chained && pick == entry,
                  onClick = {
                    pick = entry
                    chained = false
                  },
                  label = { Text(entry.label) },
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ToggleRow(
              label = "Chain Foil over Oil Slick",
              checked = chained,
              onCheckedChange = { chained = it },
            )
            ToggleRow(
              label = "Gyro lighting",
              checked = gyroEnabled,
              onCheckedChange = { gyroEnabled = it },
            )
          }
        }
      }
    }
  }
}

private val LENS = Size(520f, 520f)
private const val CORNER = 120f

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = label,
      modifier = Modifier.weight(1f),
      fontSize = 15.sp,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun PaneLabel(text: String) {
  Text(
    text = text,
    modifier = Modifier.fillMaxWidth(),
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
  )
}

/**
 * A poster pane that reports its center (for lens seeding) and applies the caller-supplied effect
 * modifier over a [CoilImage]. The effect modifier is built lazily from [effect] so the lens center
 * read happens inside the caller's modifier chain.
 */
@Composable
private fun PosterPane(onCentered: (Offset) -> Unit, effect: @Composable (Modifier) -> Modifier) {
  val poster = remember { MockUtil.getMockPoster() }
  val base = Modifier
    .fillMaxWidth()
    .height(360.dp)
    .clip(RoundedCornerShape(Dimens.itemSpacing))
    .background(MaterialTheme.colorScheme.surfaceVariant)
    .onSizeChanged { onCentered(Offset(it.width / 2f, it.height / 2f)) }

  Box(modifier = effect(base)) {
    CoilImage(
      modifier = Modifier.fillMaxSize(),
      imageModel = { poster.image },
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
    )
  }
}
