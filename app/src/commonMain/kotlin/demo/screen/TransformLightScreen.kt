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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.liquidGlassTuned
import com.skydoves.cloudy.rememberTransformLightSource
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens
import kotlin.math.abs

/**
 * Focused test screen for the transform-driven specular highlight. A single centered glass card is
 * tilted in 3D via `Modifier.graphicsLayer { rotationX; rotationY }`, and the **same** rotations feed
 * `rememberTransformLightSource`, so the rim glint tracks the card's own tilt — fully deterministic,
 * no sensor, runs on Desktop and Web. Drive it with the rotationX / rotationY sliders or by dragging
 * the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransformLightScreen(onBackClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }

  // The card's 3D rotation (degrees). These drive BOTH the graphicsLayer and the light source.
  var rotationX by remember { mutableFloatStateOf(0f) }
  var rotationY by remember { mutableFloatStateOf(0f) }
  var gain by remember { mutableFloatStateOf(1.2f) }
  var lensCenter by remember { mutableStateOf(Offset.Zero) }

  // Same rx/ry that tilt the card also drive the light. Deferred lambda reads keep the holder
  // identity stable: a rotation change invalidates the draw without recomposing this factory.
  val light = rememberTransformLightSource(
    rotationX = { rotationX },
    rotationY = { rotationY },
    gain = gain,
  )

  CollapsingAppBarScaffold(
    title = "Transform Lighting",
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
          "Tilt the glass card in 3D with the sliders — or drag it directly. The card's own " +
            "rotation drives the specular glint (no gyroscope), so it works the same on phone, " +
            "Desktop and Web.",
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          textAlign = TextAlign.Center,
        )

        // The card is rotated in 3D AND drag-rotatable; the same rx/ry feed the light above.
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .graphicsLayer {
              this.rotationX = rotationX
              this.rotationY = rotationY
              cameraDistance = 12f * density
            }
            .pointerInput(Unit) {
              detectDragGestures { _, dragAmount ->
                // Horizontal drag yaws (rotationY), vertical drag pitches (rotationX). Clamp to a
                // readable tilt range so the card never folds away from the viewer.
                rotationY = (rotationY + dragAmount.x * 0.3f).coerceIn(-60f, 60f)
                rotationX = (rotationX - dragAmount.y * 0.3f).coerceIn(-60f, 60f)
              }
            }
            .clip(RoundedCornerShape(Dimens.itemSpacing))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { size ->
              lensCenter = Offset(size.width / 2f, size.height / 2f)
            }
            .liquidGlassTuned(
              lensCenter = lensCenter,
              lensSize = Size(520f, 520f),
              cornerRadius = 120f,
              refraction = 0.25f,
              curve = 0.25f,
              edge = 0.6f,
              light = light,
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
            SectionLabel("Rotation (degrees)")
            LabeledSlider(
              label = "rotationX (pitch) — drives glint up/down",
              value = rotationX,
              onValueChange = { rotationX = it },
              valueRange = -60f..60f,
            )
            LabeledSlider(
              label = "rotationY (yaw) — drives glint left/right",
              value = rotationY,
              onValueChange = { rotationY = it },
              valueRange = -60f..60f,
            )

            SectionLabel("Light")
            LabeledSlider(
              label = "Gain (sweep strength)",
              value = gain,
              onValueChange = { gain = it },
              valueRange = 0.2f..3f,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Button(
              modifier = Modifier.fillMaxWidth(),
              enabled = abs(rotationX) > 0.001f || abs(rotationY) > 0.001f,
              onClick = {
                rotationX = 0f
                rotationY = 0f
              },
            ) {
              Text("Reset to flat")
            }
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
