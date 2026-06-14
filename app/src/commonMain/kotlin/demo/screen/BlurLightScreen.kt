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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.rememberTransformLightSource
import com.skydoves.cloudy.sky
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Demo screen for the **blur lighting** effect: a moving liquid-glass specular pool that rides the
 * *blurred backdrop* of a `Modifier.cloudy(sky = …)` surface. The pool's position is driven by a
 * deterministic [rememberTransformLightSource] (no gyroscope) — the same `rotationX` / `rotationY`
 * that you set with the sliders or by dragging the glass card sweep the highlight across the blur.
 *
 * Layout mirrors the sibling test screens: a hero `Box` stacks a `sky(sky)`-captured background image
 * behind a centered glass card carrying `cloudy(sky = …, light = …)`, so the card has real blurred
 * content to light. Lowering `radius` to 0 removes the blurred backdrop, and the highlight disappears
 * with it — the documented behavior.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlurLightScreen(onBackClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }
  val sky = rememberSky()

  // Drives BOTH the specular light direction and the card drag. Read only through deferred lambdas
  // below — never at composable scope — so a rotation change invalidates the draw without
  // recomposing this screen.
  var rotationX by remember { mutableFloatStateOf(0f) }
  var rotationY by remember { mutableFloatStateOf(0f) }
  var gain by remember { mutableFloatStateOf(1.2f) }
  var radius by remember { mutableIntStateOf(25) }

  // Deferred reads keep the holder identity stable: per-frame rotation updates re-emit through
  // snapshotFlow inside the factory without recomposing here.
  val light = rememberTransformLightSource(
    rotationX = { rotationX },
    rotationY = { rotationY },
    gain = gain,
  )

  CollapsingAppBarScaffold(
    title = "Blur Lighting",
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
          text = "Drag the glass card — or use the sliders. A liquid-glass specular pool rides the " +
            "blurred backdrop and sweeps with the light direction (no gyroscope). Lower the radius " +
            "to 0 and the highlight fades with the blur, since there is no blurred backdrop to light.",
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          textAlign = TextAlign.Center,
        )

        // Hero: the sky-captured background sits BEHIND the cloudy child within the same Box, so the
        // child has real content to blur. The child is drag-rotatable; the same rx/ry feed the light.
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(Dimens.itemSpacing)),
          contentAlignment = Alignment.Center,
        ) {
          // Background image captured by sky — the content the glass card blurs.
          CoilImage(
            modifier = Modifier
              .matchParentSize()
              .sky(sky),
            imageModel = { poster.image },
            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
          )

          // Centered glass card: blurred backdrop + moving specular highlight appear inside this.
          Box(
            modifier = Modifier
              .align(Alignment.Center)
              .fillMaxWidth(0.7f)
              .aspectRatio(1f)
              .clip(RoundedCornerShape(Dimens.itemSpacing))
              .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                  // Horizontal drag yaws (rotationY), vertical drag pitches (rotationX). Clamp to a
                  // readable range so the highlight stays on the card.
                  rotationY = (rotationY + dragAmount.x * 0.3f).coerceIn(-60f, 60f)
                  rotationX = (rotationX - dragAmount.y * 0.3f).coerceIn(-60f, 60f)
                }
              }
              .cloudy(
                sky = sky,
                radius = radius,
                light = light,
              ),
          )
        }

        Card(
          modifier = Modifier.fillMaxWidth(),
          elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation),
          shape = RoundedCornerShape(Dimens.cardCornerRadius),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.padding(Dimens.contentPadding)) {
            SectionLabel("Light direction (degrees)")
            LabeledSlider(
              label = "rotationX (pitch) — sweeps the pool up/down",
              value = rotationX,
              onValueChange = { rotationX = it },
              valueRange = -60f..60f,
            )
            LabeledSlider(
              label = "rotationY (yaw) — sweeps the pool left/right",
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

            SectionLabel("Backdrop blur")
            IntSlider(
              label = "radius (0 = no blurred backdrop, no highlight)",
              value = radius,
              onValueChange = { radius = it },
              valueRange = 0f..25f,
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

@Composable
private fun IntSlider(
  label: String,
  value: Int,
  onValueChange: (Int) -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = "$label — $value",
      fontSize = 13.sp,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    )
    Slider(
      value = value.toFloat(),
      onValueChange = { onValueChange(it.roundToInt()) },
      valueRange = valueRange,
    )
  }
}
