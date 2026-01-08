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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.liquidGlass
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens

/**
 * Liquid Glass demo screen that showcases the interactive glass lens effect.
 * Users can drag on the image to move the glass lens.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassDemoScreen(onBackClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }
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
                    onDragStart = { offset ->
                      mousePosition = offset
                    },
                    onDrag = { change, _ ->
                      mousePosition = change.position
                      change.consume()
                    },
                  )
                }
                .liquidGlass(mousePosition = mousePosition),
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

        Text(
          text = "The Liquid Glass effect creates a realistic frosted glass lens " +
            "with SDF-based crisp edges, normal-based refraction, blur, and " +
            "chromatic aberration. Requires Android 13+ or Skia platforms.",
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
