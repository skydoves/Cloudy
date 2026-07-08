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
@file:OptIn(com.skydoves.cloudy.ExperimentalMirage::class)

package demo.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.MirageOptics
import com.skydoves.cloudy.MirageScope
import com.skydoves.cloudy.mirage
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.theme.Dimens

/**
 * Demonstrates `Modifier.mirage(sky = ...)`: a card grades the `Sky` backdrop behind it instead of its
 * own content — the backdrop counterpart of `Modifier.cloudy(sky = ...)`'s blur. The container carries
 * `Modifier.sky`, a high-contrast list scrolls behind the card, and the card samples that region and
 * runs the picked optic over it.
 *
 * The headline look is [MirageOptics.Duotone], a colorize "material" rendered from the backdrop; the
 * chips also switch to the content-sampling looks ([MirageOptics.Chromatic] / [MirageOptics.Specular])
 * to show the same overload carries any filter optic. The Enabled toggle bypasses the plan so the raw
 * backdrop region shows through, making the grade easy to compare.
 *
 * Backdrop capture needs a real GPU (Android 13+ for the runtime shader), so this renders on a device
 * or emulator, not the Robolectric host.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MirageSkyScreen(onBackClick: () -> Unit) {
  val sky = rememberSky()
  val listState = rememberLazyListState()

  var pick: BackdropPick by remember { mutableStateOf(BackdropPick.Duotone) }
  var effectEnabled by remember { mutableStateOf(true) }
  var duotone: DuotonePreset by remember { mutableStateOf(DuotonePreset.Indigo) }
  var amount by remember { mutableStateOf(1f) }

  val toneShadow = duotone.shadow
  val toneHighlight = duotone.highlight
  val toneAmount = amount

  CollapsingAppBarScaffold(
    title = "Mirage (sky backdrop)",
    onBackClick = onBackClick,
  ) { paddingValues, _ ->
    MaxWidthContainer(modifier = Modifier.padding(paddingValues)) {
      Column(modifier = Modifier.fillMaxSize().padding(Dimens.contentPadding)) {
        // Stage: the sky backdrop (a scrolling, high-contrast list) with the graded card floating over
        // it. Fixed height so it never overlaps the controls below — the card's region is always clear.
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().sky(sky),
            verticalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
          ) {
            items(BACKDROP_ITEM_COUNT, key = { it }) { index -> BackdropRow(index) }
          }

          // The card samples the backdrop region directly behind it and runs the picked optic over it.
          // A visible border marks the graded region against the raw backdrop around it; the label names
          // the recipe. Transparent fill so only the graded backdrop shows through.
          Box(
            modifier = Modifier
              .align(Alignment.Center)
              .fillMaxWidth()
              .height(180.dp)
              .clip(RoundedCornerShape(Dimens.cardCornerRadius))
              .mirage(sky = sky, enabled = effectEnabled) {
                with(pick) { declare(toneShadow, toneHighlight, toneAmount) }
              }
              .border(
                width = 2.dp,
                color = Color.White,
                shape = RoundedCornerShape(Dimens.cardCornerRadius),
              ),
            contentAlignment = Alignment.TopStart,
          ) {
            Text(
              text = if (effectEnabled) "mirage(sky) { filter(${pick.label}) }" else "effect off",
              modifier = Modifier.padding(12.dp),
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Spacer(modifier = Modifier.height(Dimens.contentPadding))

        ControlsCard(
          modifier = Modifier,
          pick = pick,
          onPick = { pick = it },
          effectEnabled = effectEnabled,
          onEffectEnabledChange = { effectEnabled = it },
          duotone = duotone,
          onDuotoneChange = { duotone = it },
          amount = amount,
          onAmountChange = { amount = it },
        )
      }
    }
  }
}

private const val BACKDROP_ITEM_COUNT = 40

/**
 * A backdrop look the card can apply. Each keeps a typed optic so its `declare` sets that optic's own
 * uniforms with no cast, mirroring the self-content [MirageScreen]'s pick model.
 */
private sealed interface BackdropPick {
  val label: String

  /** Declares this pick's filter stage into the plan; only [Duotone] reads the tone/amount controls. */
  fun MirageScope.declare(shadow: Color, highlight: Color, amount: Float)

  /** The headline colorize material: maps backdrop luminance onto a shadow -> highlight duotone. */
  data object Duotone : BackdropPick {
    override val label = "Duotone"
    override fun MirageScope.declare(shadow: Color, highlight: Color, amount: Float) {
      filter(MirageOptics.Duotone) {
        shadow(shadow)
        highlight(highlight)
        amount(amount)
      }
    }
  }

  /** A content-sampling thin-film look, proving the overload carries any filter optic over a backdrop. */
  data object Chromatic : BackdropPick {
    override val label = "Chromatic"
    override fun MirageScope.declare(shadow: Color, highlight: Color, amount: Float) {
      filter(MirageOptics.Chromatic)
    }
  }

  /** The liquid-glass specular glint, also content-sampling, over the backdrop. */
  data object Specular : BackdropPick {
    override val label = "Specular"
    override fun MirageScope.declare(shadow: Color, highlight: Color, amount: Float) {
      filter(MirageOptics.Specular)
    }
  }
}

private val BACKDROP_PICKS =
  listOf(BackdropPick.Duotone, BackdropPick.Chromatic, BackdropPick.Specular)

/** Duotone grade presets — the shadow/highlight endpoints the luminance ramp maps between. */
private enum class DuotonePreset(val label: String, val shadow: Color, val highlight: Color) {
  Indigo("Indigo / Cream", Color(0xFF1B1B3A), Color(0xFFFFE8C7)),
  Teal("Teal / Sand", Color(0xFF06373B), Color(0xFFF2E2B6)),
  Plum("Plum / Blush", Color(0xFF2E1030), Color(0xFFF7D6E0)),
}

@Composable
private fun ControlsCard(
  modifier: Modifier,
  pick: BackdropPick,
  onPick: (BackdropPick) -> Unit,
  effectEnabled: Boolean,
  onEffectEnabledChange: (Boolean) -> Unit,
  duotone: DuotonePreset,
  onDuotoneChange: (DuotonePreset) -> Unit,
  amount: Float,
  onAmountChange: (Float) -> Unit,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation),
    shape = RoundedCornerShape(Dimens.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.padding(Dimens.contentPadding)) {
      Text(
        text = "Modifier.mirage(sky) { filter(${pick.label}) } grades the list behind the card.",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(12.dp))

      SectionLabel("Optic")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BACKDROP_PICKS.forEach { entry ->
          FilterChip(
            selected = pick == entry,
            onClick = { onPick(entry) },
            label = { Text(entry.label) },
          )
        }
      }

      if (pick == BackdropPick.Duotone) {
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel("Tone")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DuotonePreset.entries.forEach { preset ->
            FilterChip(
              selected = duotone == preset,
              onClick = { onDuotoneChange(preset) },
              label = { Text(preset.label) },
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel("Amount")
        Slider(value = amount, onValueChange = onAmountChange, valueRange = 0f..1f)
      }

      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "Effect enabled",
          modifier = Modifier.weight(1f),
          fontSize = 15.sp,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = effectEnabled, onCheckedChange = onEffectEnabledChange)
      }
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text,
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurface,
  )
}

@Composable
private fun BackdropRow(index: Int) {
  // Alternating high-contrast bands give the grade an obvious luminance range to remap.
  val colors = remember {
    listOf(
      Color(0xFF1565C0),
      Color(0xFFC62828),
      Color(0xFF2E7D32),
      Color(0xFF6A1B9A),
      Color(0xFFEF6C00),
    )
  }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(64.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(colors[index % colors.size]),
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(
      text = "Item #$index",
      modifier = Modifier.padding(start = 16.dp),
      color = Color.White,
      fontSize = 16.sp,
      fontWeight = FontWeight.SemiBold,
    )
  }
}
