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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.liquidGlass
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens

/**
 * Liquid Glass demo screen that showcases the interactive glass lens effect.
 * Users can drag on the image to move the glass lens and adjust parameters with sliders.
 *
 * This demo shows how to combine Cloudy's blur with the Liquid Glass lens effect
 * as two independent, composable modifiers.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassDemoScreen(onBackClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }

  // Cloudy blur parameter (independent from liquid glass)
  var blurRadius by remember { mutableIntStateOf(8) }

  // Liquid Glass effect parameters
  var cornerRadius by remember { mutableFloatStateOf(50f) }
  var refraction by remember { mutableFloatStateOf(0.25f) }
  var curve by remember { mutableFloatStateOf(0.25f) }
  var edge by remember { mutableFloatStateOf(0.2f) }
  var saturation by remember { mutableFloatStateOf(1.0f) }
  var dispersion by remember { mutableFloatStateOf(0.0f) }

  // Lens position
  var mousePosition by remember { mutableStateOf(Offset.Zero) }

  CollapsingAppBarScaffold(
    title = "Liquid Glass",
    onBackClick = onBackClick,
  ) { paddingValues, _ ->
    MaxWidthContainer(modifier = Modifier.padding(paddingValues)) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(Dimens.contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Glass Preview Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation),
          shape = RoundedCornerShape(Dimens.cardCornerRadius),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(Dimens.contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "Drag to Move Glass Lens",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(Dimens.contentPadding))

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(Dimens.itemSpacing))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { size ->
                  // Initialize mouse position to center
                  if (mousePosition == Offset.Zero) {
                    mousePosition = Offset(size.width / 2f, size.height / 2f)
                  }
                }
                .pointerInput(Unit) {
                  detectDragGestures(
                    onDrag = { change, dragAmount ->
                      mousePosition += dragAmount
                      change.consume()
                    },
                  )
                }
                // Cloudy blur - independent modifier with full Cloudy API
                .cloudy(radius = blurRadius)
                // Liquid Glass lens effect - separate from blur
                .liquidGlass(
                  mousePosition = mousePosition,
                  lensSize = Size(350f, 350f),
                  cornerRadius = cornerRadius,
                  refraction = refraction,
                  curve = curve,
                  edge = edge,
                  saturation = saturation,
                  dispersion = dispersion,
                ),
            ) {
              CoilImage(
                modifier = Modifier.fillMaxSize(),
                imageModel = { poster.image },
                imageOptions = ImageOptions(
                  contentScale = ContentScale.Crop,
                ),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Blur Slider Card (Cloudy)
        Card(
          modifier = Modifier.fillMaxWidth(),
          elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation),
          shape = RoundedCornerShape(Dimens.cardCornerRadius),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(Dimens.contentPadding),
          ) {
            Text(
              text = "Cloudy Blur",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Independent blur using Modifier.cloudy()",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            ParameterSlider(
              label = "Radius",
              value = blurRadius.toFloat(),
              onValueChange = { blurRadius = it.toInt() },
              valueRange = 0f..25f,
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Liquid Glass Sliders Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation),
          shape = RoundedCornerShape(Dimens.cardCornerRadius),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(Dimens.contentPadding),
          ) {
            Text(
              text = "Liquid Glass Lens",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Lens distortion using Modifier.liquidGlass()",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            ParameterSlider(
              label = "Shape",
              value = cornerRadius,
              onValueChange = { cornerRadius = it },
              valueRange = 0f..175f,
            )

            ParameterSlider(
              label = "Refraction",
              value = refraction,
              onValueChange = { refraction = it },
              valueRange = 0f..1f,
            )

            ParameterSlider(
              label = "Curve",
              value = curve,
              onValueChange = { curve = it },
              valueRange = 0f..1f,
            )

            ParameterSlider(
              label = "Edge",
              value = edge,
              onValueChange = { edge = it },
              valueRange = 0f..1f,
            )

            ParameterSlider(
              label = "Saturation",
              value = saturation,
              onValueChange = { saturation = it },
              valueRange = 0f..2f,
            )

            ParameterSlider(
              label = "Dispersion",
              value = dispersion,
              onValueChange = { dispersion = it },
              valueRange = 0f..2f,
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = "This demo shows Cloudy blur and Liquid Glass as independent, " +
            "composable modifiers. Adjust blur radius separately from lens effects. " +
            "Requires Android 13+ or Skia platforms for full liquid glass effect.",
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun ParameterSlider(
  label: String,
  value: Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      fontSize = 14.sp,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.width(80.dp),
    )

    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      modifier = Modifier.weight(1f),
      colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
      ),
    )

    Text(
      text = ((value * 100).toInt() / 100f).toString(),
      fontSize = 12.sp,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
      modifier = Modifier.width(48.dp),
      textAlign = TextAlign.End,
    )
  }
}
