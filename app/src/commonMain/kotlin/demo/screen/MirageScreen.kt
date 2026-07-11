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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.ChromaticParams
import com.skydoves.cloudy.CompositeShader
import com.skydoves.cloudy.MirageLensParams
import com.skydoves.cloudy.MirageScope
import com.skydoves.cloudy.MirageShaders
import com.skydoves.cloudy.SpecularParams
import com.skydoves.cloudy.mirage
import com.skydoves.cloudy.rememberGyroLightSource
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.shader.DropletMap
import demo.shader.RainyWindowParams
import demo.shader.RainyWindowShader
import demo.theme.Dimens

/**
 * The look applied by the demo's chips. Each pick keeps a fully typed shader reference so the per-draw
 * params block can set that shader's own subclass uniform (e.g. `specStrength`, `chromaticIntensity`,
 * `rainAmount`) with no unchecked cast, which an erased `FilterShader<out MirageLensParams>` would
 * require. A pick declares its stage into the [MirageScope] via [declare], driving the shared strength
 * slider ([strength] `0..1`) into whichever param reads as "how strong" for that look.
 */
private sealed interface MiragePick {
  val label: String

  /** Declares this pick's filter stage, applying the shared lens [framing] and the [strength] slider. */
  fun MirageScope.declare(framing: MirageLensParams.() -> Unit, strength: Float)

  /** The liquid-glass specular glint; strength drives its peak highlight (`specStrength`, default 0.7). */
  data object Specular : MiragePick {
    override val label = "Specular"
    override fun MirageScope.declare(framing: MirageLensParams.() -> Unit, strength: Float) {
      filter(MirageShaders.Specular) {
        framing()
        specStrength(strength)
      }
    }
  }

  /**
   * A thin-film iridescence look; strength drives its overall `chromaticIntensity` (default 0.6). Holds
   * the concrete [shader] so the block keeps `ChromaticParams` typed across all five named variants.
   */
  data class Chromatic(
    override val label: String,
    private val shader: CompositeShader<ChromaticParams>,
  ) : MiragePick {
    override fun MirageScope.declare(framing: MirageLensParams.() -> Unit, strength: Float) {
      filter(shader) {
        framing()
        chromaticIntensity(strength)
      }
    }
  }

  /**
   * The demo-authored rainy-window shader (open-API showcase). Full-bleed by design and content-shaped,
   * so it ignores the lens [framing]; strength maps to `rainAmount`. Carries the baked, tileable
   * [dropletMap] (generated once and remembered in the composable) that the texture-backed kernel taps.
   */
  data class RainyWindow(private val dropletMap: ImageBitmap) : MiragePick {
    override val label = "Rainy Window"
    override fun MirageScope.declare(framing: MirageLensParams.() -> Unit, strength: Float) {
      filter(RainyWindowShader.RainyWindow) {
        dropletMap(this@RainyWindow.dropletMap)
        rainAmount(strength)
      }
    }
  }
}

/**
 * The looks that need no per-composition state. The rainy-window pick is appended in the composable
 * because it carries a remembered droplet texture (see [MirageScreen]).
 */
private val BASE_PICKS: List<MiragePick> = listOf(
  MiragePick.Specular,
  MiragePick.Chromatic("Iridescent", MirageShaders.Chromatic),
  MiragePick.Chromatic("Oil Slick", MirageShaders.OilSlick),
  MiragePick.Chromatic("Soap Bubble", MirageShaders.SoapBubble),
  MiragePick.Chromatic("Metallic Foil", MirageShaders.MetallicFoil),
  MiragePick.Chromatic("Pearl", MirageShaders.Pearl),
)

/**
 * Showcases the open [mirage] pipeline mechanism: an arbitrary poster gets an effect from a single
 * `Modifier.mirage { … }` block, no bespoke component.
 *
 * Demonstrated here:
 * - **MirageShader catalog:** chips swap between the bundled looks and a demo-authored [RainyWindowShader]
 *   proving any app can author an shader through the public API with no core change.
 * - **Strength slider:** one `0..1` slider feeds each look's "how strong" uniform (`specStrength` /
 *   `chromaticIntensity` / `rainAmount`) from the per-draw params block, so sliding re-renders live
 *   (the block identity is part of the node's element equality, so it updates cheaply).
 * - **Full-bleed lens:** a toggle grows the lens to the whole pane, so the bevel/rim terms hug the
 *   pane edges. The rainy-window look is full-bleed regardless (it is content-shaped, not lens-shaped).
 * - **Overlay over filter:** the chaining toggle adds an `overlay(MirageShaders.Foil)` on top of an
 *   `filter(MirageShaders.OilSlick)` stage; the pipeline orders it correctly regardless of declaration.
 *
 * The lens center/size are seeded from the pane via `onSizeChanged` and passed into each shader's
 * `lensCenter` / `lensSize` uniforms in the per-draw params block.
 *
 * The gyro toggle keeps [rememberGyroLightSource] wired (registering the sensor), but the shader's
 * `iLight` stays at its default direction — the motion holder's direction is not part of the public
 * surface yet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MirageScreen(onBackClick: () -> Unit) {
  // The droplet map is a one-shot offline bake; remember it so it survives recompositions and is
  // uploaded once. It is deterministic (fixed seed), so the field is stable across runs.
  val dropletMap = remember { DropletMap.generate() }
  val picks = remember(dropletMap) { BASE_PICKS + MiragePick.RainyWindow(dropletMap) }

  var pick: MiragePick by remember { mutableStateOf(MiragePick.Specular) }
  var gyroEnabled by remember { mutableStateOf(true) }
  var chained by remember { mutableStateOf(false) }
  var fullBleed by remember { mutableStateOf(false) }
  var strength by remember { mutableStateOf(0.6f) }
  var lensCenter by remember { mutableStateOf(Offset.Zero) }
  var paneSize by remember { mutableStateOf(Size.Zero) }

  // Keeps the sensor path exercised; the direction holder is not read into iLight (see the KDoc).
  rememberGyroLightSource(enabled = gyroEnabled)

  // Sets the shared lens framing (center + size + corner) into any lens-shaped shader's params. When
  // full-bleed is on, the lens covers the whole pane (center = pane center, size = pane size) with a
  // square corner, so the bevel/rim terms fall on the pane edges. Read the seeded values into locals
  // so the params-receiver's `lensCenter` handle does not shadow them.
  val center = lensCenter
  val pane = paneSize
  val bleed = fullBleed
  val lensFraming: MirageLensParams.() -> Unit = {
    if (bleed && pane != Size.Zero) {
      lensCenter(Offset(pane.width / 2f, pane.height / 2f))
      lensSize(pane)
      cornerRadius(0f)
    } else {
      lensCenter(center)
      lensSize(LENS)
      cornerRadius(CORNER)
    }
  }

  CollapsingAppBarScaffold(
    title = "Mirage (pipeline)",
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
          "One Modifier.mirage { } block applies an open shader pipeline to any content. Pick a " +
            "look, drag Strength, toggle full-bleed, or chain Foil over a refracting Oil Slick. " +
            "Android 13+ / Skia.",
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          textAlign = TextAlign.Center,
        )

        PaneLabel(
          if (chained) {
            "mirage { filter(OilSlick); overlay(Foil) } — overlay over refraction"
          } else {
            "mirage { filter(${pick.label}) }"
          },
        )

        PosterPane(
          onSized = { size ->
            paneSize = size
            lensCenter = Offset(size.width / 2f, size.height / 2f)
          },
        ) { paneModifier ->
          if (chained) {
            paneModifier.mirage {
              filter(MirageShaders.OilSlick) { lensFraming() }
              overlay(MirageShaders.Foil) { lensFraming() }
            }
          } else {
            paneModifier.mirage {
              with(pick) { declare(lensFraming, strength) }
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
              picks.forEach { entry ->
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

            Text(
              text = "Strength",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(value = strength, onValueChange = { strength = it }, valueRange = 0f..1f)

            ToggleRow(
              label = "Full-bleed lens",
              checked = fullBleed,
              onCheckedChange = { fullBleed = it },
            )
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
 * A poster pane that reports its size (for lens seeding and full-bleed framing) and applies the
 * caller-supplied effect modifier over a [CoilImage]. The effect modifier is built lazily from
 * [effect] so the lens read happens inside the caller's modifier chain.
 */
@Composable
private fun PosterPane(onSized: (Size) -> Unit, effect: @Composable (Modifier) -> Modifier) {
  val poster = remember { MockUtil.getMockPoster() }
  val base = Modifier
    .fillMaxWidth()
    .height(360.dp)
    .clip(RoundedCornerShape(Dimens.itemSpacing))
    .background(MaterialTheme.colorScheme.surfaceVariant)
    .onSizeChanged { onSized(Size(it.width.toFloat(), it.height.toFloat())) }

  Box(modifier = effect(base)) {
    CoilImage(
      modifier = Modifier.fillMaxSize(),
      imageModel = { poster.image },
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
    )
  }
}
