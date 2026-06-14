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
  com.skydoves.cloudy.ExperimentalLiquidGlassMaterial::class,
  com.skydoves.cloudy.ExperimentalLiquidGlassMotion::class,
)

package demo.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.ChromaticMode
import com.skydoves.cloudy.ChromaticOverlay
import com.skydoves.cloudy.LiquidGlassDefaults
import com.skydoves.cloudy.liquidGlass
import com.skydoves.cloudy.rememberGyroLightSource
import com.skydoves.cloudy.rememberTransformLightSource
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.theme.Dimens

/**
 * Which preset feeds the chromatic overlay. `Custom` is driven by the intensity slider and the
 * Iridescent/Foil switch below; the other two are fixed library presets.
 */
private enum class MaterialPreset { PureWhite, Holographic, Custom }

/**
 * Which surface sits under the chromatic material. The base changes how the holographic sheen
 * reads: a white card multiplies (the sheen stays faint), while opaque dark/metal cards take the
 * screen-blend path where the thin-film foil turns dramatic and metallic.
 */
private enum class BaseSurface { White, Dark, Metal }

/**
 * Focused demo for the chromatic (iridescent) overlay — a holographic thin-film sheen: Newton's
 * rings that shift hue with the light/angle. Tilt the device (or drag the card) to sweep the light.
 * Material chips switch the overlay preset; surface chips switch the base under it, so you can see
 * the same material read faint on white and dramatic on a dark/metal surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChromaticCardScreen(onBackClick: () -> Unit) {
  var preset by remember { mutableStateOf(MaterialPreset.Holographic) }
  var customIntensity by remember { mutableFloatStateOf(LiquidGlassDefaults.CHROMATIC_INTENSITY) }
  var customFoil by remember { mutableStateOf(true) }
  var gyroEnabled by remember { mutableStateOf(true) }
  var lensCenter by remember { mutableStateOf(Offset.Zero) }
  // Default to a dark base so the new thin-film holographic preset reads strongly on first entry
  // (the screen-blend path); the White chip restores the faint, minimal look.
  var base by remember { mutableStateOf(BaseSurface.Dark) }

  // The base only paints the card background — it is independent of the chromatic build above, so
  // changing it never reallocates the lens modifier. Remember the gradient brush per base so the
  // holder identity is stable and Brush.linearGradient is not rebuilt every recomposition.
  val baseBrush = remember(base) {
    when (base) {
      BaseSurface.White -> Brush.linearGradient(listOf(Color.White, Color.White))
      BaseSurface.Dark -> Brush.linearGradient(listOf(Color(0xFF1A1F2E), Color(0xFF2A3142)))
      BaseSurface.Metal -> Brush.linearGradient(listOf(Color(0xFFB0B4BC), Color(0xFF40444C)))
    }
  }
  // Keep the card title legible on every base: dark ink on white, light ink on the dark/metal.
  val onBase = if (base == BaseSurface.White) Color.Black else Color.White

  // Drag fallback: accumulate a virtual tilt and feed it to the deterministic transform light, so
  // dragging sweeps the sheen the same way the gyro does. Deferred lambda reads keep the holder
  // identity stable (a drag updates the draw, not this composition).
  var rotationX by remember { mutableFloatStateOf(0f) }
  var rotationY by remember { mutableFloatStateOf(0f) }
  val gyroLight = rememberGyroLightSource(enabled = gyroEnabled)
  val dragLight = rememberTransformLightSource(
    rotationX = { rotationX },
    rotationY = { rotationY },
  )
  val light = if (gyroEnabled) gyroLight else dragLight

  // Rebuild the overlay only when an input actually changes — not every recomposition — so the
  // ChromaticOverlay holder identity stays stable and the lens modifier is not reallocated.
  val chromatic = remember(preset, customIntensity, customFoil) {
    when (preset) {
      MaterialPreset.PureWhite -> LiquidGlassDefaults.NoChromatic
      MaterialPreset.Holographic -> LiquidGlassDefaults.Holographic
      MaterialPreset.Custom -> ChromaticOverlay(
        intensity = customIntensity,
        mode = if (customFoil) ChromaticMode.Foil else ChromaticMode.Iridescent,
      )
    }
  }

  CollapsingAppBarScaffold(
    title = "Chromatic Card",
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
          text = "A holographic thin-film sheen — Newton's-ring interference that shifts color " +
            "with the light/angle. Tilt the device (or drag the card) to sweep it, and switch " +
            "the base below to see it read faint on white or dramatic on dark/metal. " +
            "Android 13+ / Skia.",
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          textAlign = TextAlign.Center,
        )

        // A solid card so only the chromatic material shows (no photo underneath). The base brush
        // switches white / dark / metal to demonstrate how the holographic sheen reads on each.
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(Dimens.itemSpacing))
            .background(baseBrush)
            .pointerInput(Unit) {
              detectDragGestures { _, dragAmount ->
                // Drag accumulates a virtual tilt (clamped to a readable range) that drives the
                // fallback transform light; horizontal = yaw, vertical = pitch.
                rotationY = (rotationY + dragAmount.x * 0.3f).coerceIn(-60f, 60f)
                rotationX = (rotationX - dragAmount.y * 0.3f).coerceIn(-60f, 60f)
              }
            }
            .onSizeChanged { size ->
              lensCenter = Offset(size.width / 2f, size.height / 2f)
            }
            .liquidGlass(
              lensCenter = lensCenter,
              lensSize = Size(520f, 520f),
              cornerRadius = 120f,
              refraction = 0.25f,
              curve = 0.25f,
              edge = 0.6f,
              light = light,
              chromatic = chromatic,
            ),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = when (base) {
                BaseSurface.White -> "Pure White"
                BaseSurface.Dark -> "Dark"
                BaseSurface.Metal -> "Metal"
              },
              fontSize = 28.sp,
              fontWeight = FontWeight.Bold,
              color = onBase.copy(alpha = 0.85f),
            )
            Text(
              text = "Holographic.",
              fontSize = 16.sp,
              color = onBase.copy(alpha = 0.55f),
            )
          }
        }

        Card(
          modifier = Modifier.fillMaxWidth(),
          elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation),
          shape = RoundedCornerShape(Dimens.cardCornerRadius),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.padding(Dimens.contentPadding)) {
            SectionLabel("Material")
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilterChip(
                selected = preset == MaterialPreset.PureWhite,
                onClick = { preset = MaterialPreset.PureWhite },
                label = { Text("Pure White") },
              )
              FilterChip(
                selected = preset == MaterialPreset.Holographic,
                onClick = { preset = MaterialPreset.Holographic },
                label = { Text("Holographic") },
              )
              FilterChip(
                selected = preset == MaterialPreset.Custom,
                onClick = { preset = MaterialPreset.Custom },
                label = { Text("Custom") },
              )
            }

            SectionLabel("Surface")
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilterChip(
                selected = base == BaseSurface.White,
                onClick = { base = BaseSurface.White },
                label = { Text("White") },
              )
              FilterChip(
                selected = base == BaseSurface.Dark,
                onClick = { base = BaseSurface.Dark },
                label = { Text("Dark") },
              )
              FilterChip(
                selected = base == BaseSurface.Metal,
                onClick = { base = BaseSurface.Metal },
                label = { Text("Metal") },
              )
            }

            SectionLabel("Custom overlay")
            LabeledSlider(
              label = "Intensity (sheen strength)",
              value = customIntensity,
              onValueChange = {
                customIntensity = it
                preset = MaterialPreset.Custom
              },
              valueRange = 0f..1f,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = if (customFoil) "Mode: Foil (flowing bands)" else "Mode: Iridescent",
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
              )
              Switch(
                checked = customFoil,
                onCheckedChange = {
                  customFoil = it
                  preset = MaterialPreset.Custom
                },
              )
            }

            SectionLabel("Light")
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
            Text(
              text = "Off: drag the card to sweep the light by hand.",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(8.dp))
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
