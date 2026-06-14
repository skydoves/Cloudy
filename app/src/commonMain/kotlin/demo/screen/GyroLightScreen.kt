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
@file:OptIn(com.skydoves.cloudy.ExperimentalLiquidGlassMotion::class)

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.skydoves.cloudy.liquidGlassTuned
import com.skydoves.cloudy.rememberGyroLightSource
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens

/**
 * Focused test screen for the gyro-driven specular highlight. A single large, centered glass lens
 * over a poster, a "Gyro lighting" toggle, and a wide edge slider — tilt the device and the rim
 * glint should sweep around the lens. Kept separate from the full Liquid Glass demo so the motion
 * effect can be isolated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GyroLightScreen(onBackClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }

  var gyroEnabled by remember { mutableStateOf(true) }
  var edge by remember { mutableFloatStateOf(0.6f) }
  var cornerRadius by remember { mutableFloatStateOf(120f) }
  var lensCenter by remember { mutableStateOf(Offset.Zero) }

  // Glow tuning (the 4 specular knobs exposed via Modifier.liquidGlassTuned).
  var glowIntensity by remember { mutableFloatStateOf(LiquidGlassDefaults.GLOW_INTENSITY) }
  var glowSharpness by remember { mutableFloatStateOf(LiquidGlassDefaults.GLOW_SHARPNESS) }
  var glowRimMix by remember { mutableFloatStateOf(0.4f) }
  var glowWidthPx by remember { mutableFloatStateOf(12f) }

  // Gyro-input tuning (fed into rememberGyroLightSource).
  var hz by remember { mutableFloatStateOf(30f) }
  var tiltGain by remember { mutableFloatStateOf(1.2f) }

  // One sensor registration, shared with the lens below.
  val gyroLight = rememberGyroLightSource(
    enabled = gyroEnabled,
    hz = hz.toInt(),
    tiltGain = tiltGain,
  )

  CollapsingAppBarScaffold(
    title = "Gyro Lighting",
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
          text = "Tilt your device — the bright glint on the glass rim should sweep around. " +
            "Turn the toggle off and the glint stays fixed (bottom-left). Android 13+ / Skia.",
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          textAlign = TextAlign.Center,
        )

        // A single big lens centered on the image so the rim is large and the glint is easy to see.
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(Dimens.itemSpacing))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { size ->
              lensCenter = Offset(size.width / 2f, size.height / 2f)
            }
            .liquidGlassTuned(
              lensCenter = lensCenter,
              lensSize = Size(520f, 520f),
              cornerRadius = cornerRadius,
              refraction = 0.25f,
              curve = 0.25f,
              edge = edge,
              light = if (gyroEnabled) gyroLight else LiquidGlassDefaults.Light,
              glowIntensity = glowIntensity,
              glowSharpness = glowSharpness,
              glowRimMix = glowRimMix,
              glowWidthPx = glowWidthPx,
            ),
        ) {
          CoilImage(
            modifier = Modifier.fillMaxSize(),
            imageModel = { poster.image },
            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
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

            Spacer(modifier = Modifier.height(8.dp))

            LabeledSlider(
              label = "Edge (highlight gate/width)",
              value = edge,
              onValueChange = { edge = it },
              valueRange = 0f..1f,
            )
            LabeledSlider(
              label = "Corner radius",
              value = cornerRadius,
              onValueChange = { cornerRadius = it },
              valueRange = 0f..260f,
            )

            SectionLabel("Glow")
            LabeledSlider(
              label = "Intensity (how bright)",
              value = glowIntensity,
              onValueChange = { glowIntensity = it },
              valueRange = 0f..1f,
            )
            LabeledSlider(
              label = "Sharpness (how tight the glint)",
              value = glowSharpness,
              onValueChange = { glowSharpness = it },
              valueRange = 1f..40f,
            )
            LabeledSlider(
              label = "Body ↔ Rim (0 = moving focal pool, 1 = rim glint)",
              value = glowRimMix,
              onValueChange = { glowRimMix = it },
              valueRange = 0f..1f,
            )
            LabeledSlider(
              label = "Band width (px)",
              value = glowWidthPx,
              onValueChange = { glowWidthPx = it },
              valueRange = 0f..40f,
            )

            SectionLabel("Gyro")
            LabeledSlider(
              label = "Emit rate (Hz)",
              value = hz,
              onValueChange = { hz = it },
              valueRange = 15f..60f,
            )
            LabeledSlider(
              label = "Tilt gain (sweep strength)",
              value = tiltGain,
              onValueChange = { tiltGain = it },
              valueRange = 0.2f..3f,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Spacer(modifier = Modifier.height(8.dp))
  Text(
    text = text,
    fontSize = 14.sp,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurface,
  )
}

@Composable
private fun LabeledSlider(
  label: String,
  value: Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = label,
      fontSize = 13.sp,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    )
    Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
  }
}
