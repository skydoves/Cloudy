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
  com.skydoves.cloudy.ExperimentalShaderEffect::class,
  com.skydoves.cloudy.ExperimentalLiquidGlassMotion::class,
)

package demo.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.skydoves.cloudy.LiquidGlassDefaults
import com.skydoves.cloudy.ShaderRecipe
import com.skydoves.cloudy.ShaderRecipes
import com.skydoves.cloudy.liquidGlass
import com.skydoves.cloudy.rememberGyroLightSource
import com.skydoves.cloudy.shaderEffect
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens

/**
 * Demonstrates the open [shaderEffect] recipe mechanism: an arbitrary poster gets an effect with a
 * single modifier line and no bespoke component.
 *
 * Two verification goals are visible here:
 * - **G-4 (no visual regression):** the top pane renders the built-in [liquidGlass] specular glint
 *   and the bottom pane renders [ShaderRecipes.Specular] with the same lens/light — they should look
 *   the same (the specular recipe is the built-in glint repackaged), proving the open path matches.
 * - **G-1 (the recipe compiles + renders):** switching the recipe chip to Chromatic swaps in
 *   [ShaderRecipes.Chromatic]; the thin-film sheen appearing proves the preamble + a brand-new recipe
 *   body compile on the GPU with no core changes.
 *
 * The lens center is seeded to the pane center via `onSizeChanged` (an `Offset.Zero` center pins the
 * lens to the corner).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaderEffectScreen(onBackClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }

  var gyroEnabled by remember { mutableStateOf(true) }
  var useChromatic by remember { mutableStateOf(false) }
  var topCenter by remember { mutableStateOf(Offset.Zero) }
  var bottomCenter by remember { mutableStateOf(Offset.Zero) }

  // One sensor registration shared by both panes so the glint sweeps in lockstep when tilting.
  val gyroLight = rememberGyroLightSource(enabled = gyroEnabled)
  val light = if (gyroEnabled) gyroLight else LiquidGlassDefaults.Light

  val recipe: ShaderRecipe = if (useChromatic) ShaderRecipes.Chromatic else ShaderRecipes.Specular

  CollapsingAppBarScaffold(
    title = "Shader Effect (recipe)",
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
          text = "One modifier line applies an open ShaderRecipe to any content. Top = built-in " +
            "liquidGlass specular; bottom = shaderEffect(Specular) — they should match. " +
            "Switch the recipe to Chromatic for a fresh thin-film effect. Android 13+ / Skia.",
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          textAlign = TextAlign.Center,
        )

        PaneLabel("Built-in Modifier.liquidGlass (specular)")
        PosterPane(
          onCentered = { topCenter = it },
        ) { paneModifier ->
          paneModifier.liquidGlass(
            lensCenter = topCenter,
            lensSize = LENS,
            cornerRadius = CORNER,
            edge = 0.6f,
            light = light,
          )
        }

        PaneLabel(
          if (useChromatic) {
            "Modifier.shaderEffect(ShaderRecipes.Chromatic)"
          } else {
            "Modifier.shaderEffect(ShaderRecipes.Specular)"
          },
        )
        PosterPane(
          onCentered = { bottomCenter = it },
        ) { paneModifier ->
          // The open recipe path — the whole point: one line, any content, no component.
          paneModifier.shaderEffect(
            recipe = recipe,
            lensCenter = bottomCenter,
            lensSize = LENS,
            cornerRadius = CORNER,
            light = light,
          )
        }

        Card(
          modifier = Modifier.fillMaxWidth(),
          elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation),
          shape = RoundedCornerShape(Dimens.cardCornerRadius),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.padding(Dimens.contentPadding)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Gyro lighting",
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Switch(checked = gyroEnabled, onCheckedChange = { gyroEnabled = it })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "Recipe",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = !useChromatic,
                onClick = { useChromatic = false },
                label = { Text("Specular") },
              )
              FilterChip(
                selected = useChromatic,
                onClick = { useChromatic = true },
                label = { Text("Chromatic") },
              )
            }
          }
        }
      }
    }
  }
}

private val LENS = Size(520f, 520f)
private const val CORNER = 120f

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
    .height(300.dp)
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
