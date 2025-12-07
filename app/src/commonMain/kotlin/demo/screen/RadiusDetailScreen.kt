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

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.cloudy
import com.skydoves.landscapist.coil3.CoilImage
import demo.model.MockUtil
import demo.model.Poster

/**
 * Radius detail screen with animated blur effect from 0 to the selected radius.
 *
 * @param radius The blur radius to demonstrate.
 * @param onBackClick Callback when the back button is pressed.
 */
@Composable
fun RadiusDetailScreen(radius: Int, onBackClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }

  Scaffold(
    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    topBar = {
      TopAppBar(
        title = { Text("Radius: $radius") },
        backgroundColor = MaterialTheme.colors.primary,
        contentColor = MaterialTheme.colors.onPrimary,
        navigationIcon = {
          TextButton(onClick = onBackClick) {
            Text(
              text = "<-",
              fontSize = 20.sp,
              color = MaterialTheme.colors.onPrimary,
            )
          }
        },
      )
    },
    backgroundColor = MaterialTheme.colors.background,
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      RadiusDetailCard(
        title = "Static Blur",
        radius = radius,
        poster = poster,
        animated = false,
      )

      Spacer(modifier = Modifier.height(24.dp))

      RadiusDetailCard(
        title = "Animated Blur (0 -> $radius)",
        radius = radius,
        poster = poster,
        animated = true,
      )

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "The blur effect animates smoothly from 0 to the target radius over 1.5 seconds, " +
          "demonstrating Cloudy's animated blur capability.",
        fontSize = 14.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
      )
    }
  }
}

/**
 * A card displaying a poster with blur effect, optionally animated.
 */
@Composable
private fun RadiusDetailCard(title: String, radius: Int, poster: Poster, animated: Boolean) {
  var animationPlayed by remember { mutableStateOf(!animated) }
  val animatedRadius by animateIntAsState(
    targetValue = if (animationPlayed) radius else 0,
    animationSpec = tween(
      durationMillis = 1500,
      delayMillis = 300,
      easing = FastOutLinearInEasing,
    ),
    label = "Blur Animation",
  )

  LaunchedEffect(Unit) {
    if (animated) {
      animationPlayed = true
    }
  }

  val imageShape = RoundedCornerShape(8.dp)

  Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = 4.dp,
    shape = RoundedCornerShape(12.dp),
    backgroundColor = MaterialTheme.colors.surface,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onSurface,
      )

      Spacer(modifier = Modifier.height(12.dp))

      CoilImage(
        modifier = Modifier
          .size(300.dp)
          .clip(imageShape)
          .background(MaterialTheme.colors.surface, imageShape)
          .cloudy(radius = if (animated) animatedRadius else radius),
        imageModel = { poster.image },
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Current radius: ${if (animated) animatedRadius else radius}",
        fontSize = 14.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
      )
    }
  }
}
