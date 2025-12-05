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
package demo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
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
import demo.theme.PosterTheme
import androidx.compose.material.MaterialTheme as M2MaterialTheme

/**
 * Hosts the Cloudy demo UI and navigates between the radius list and blur detail screens.
 *
 * Displays either a list of blur radii or, when a radius is selected, a detail screen for that radius.
 * The content is wrapped in PosterTheme and transitions between list and detail views use animated enter/exit transitions.
 */
@Composable
fun CloudyDemoApp() {
  PosterTheme {
    var selectedRadius: Int? by remember { mutableStateOf(null) }

    AnimatedContent(
      targetState = selectedRadius,
      transitionSpec = {
        if (targetState != null) {
          slideInHorizontally { it } + fadeIn() togetherWith
            slideOutHorizontally { -it } + fadeOut()
        } else {
          slideInHorizontally { -it } + fadeIn() togetherWith
            slideOutHorizontally { it } + fadeOut()
        }
      },
    ) { radius ->
      if (radius != null) {
        BlurDetailScreen(
          radius = radius,
          onBackClick = { selectedRadius = null },
        )
      } else {
        RadiusListScreen(
          onRadiusSelected = { selectedRadius = it },
        )
      }
    }
  }
}

private val testRadiusList = listOf(0, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100)

/**
 * Shows a scrollable list of predefined blur radii and allows selecting one.
 *
 * @param onRadiusSelected Callback invoked with the selected radius when a list item is tapped.
 */
@Composable
private fun RadiusListScreen(onRadiusSelected: (Int) -> Unit) {
  Scaffold(
    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    topBar = {
      TopAppBar(
        title = { Text("Cloudy Demo - Blur Radius Test") },
        backgroundColor = M2MaterialTheme.colors.primary,
        contentColor = M2MaterialTheme.colors.onPrimary,
      )
    },
    backgroundColor = M2MaterialTheme.colors.background,
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(16.dp),
    ) {
      items(testRadiusList) { radius ->
        RadiusListItem(
          radius = radius,
          onClick = { onRadiusSelected(radius) },
        )
      }
    }
  }
}

/**
 * Displays a clickable card representing a blur radius with a preview image and descriptive text.
 *
 * @param radius Blur radius in pixels used to render the preview and shown in the label.
 * @param onClick Callback invoked when the card is clicked.
 */
@Composable
private fun RadiusListItem(radius: Int, onClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    elevation = 4.dp,
    shape = RoundedCornerShape(12.dp),
    backgroundColor = M2MaterialTheme.colors.surface,
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(80.dp)
          .clip(RoundedCornerShape(8.dp)),
      ) {
        CoilImage(
          modifier = Modifier
            .fillMaxSize()
            .cloudy(radius = radius),
          imageModel = { poster.image },
        )
      }

      Spacer(modifier = Modifier.width(16.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Radius: $radius",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = M2MaterialTheme.colors.onSurface,
        )
        Text(
          text = if (radius == 0) "No blur" else "Sigma: ${radius / 2.0f}",
          fontSize = 14.sp,
          color = M2MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
      }

      Text(
        text = "→",
        fontSize = 24.sp,
        color = M2MaterialTheme.colors.primary,
      )
    }
  }
}

/**
 * Shows a detail screen demonstrating static and animated blur effects for the provided radius and supplies back navigation.
 *
 * The screen includes a top app bar displaying the radius, a static blur preview, an animated blur preview, and a text blur example in a scrollable column.
 *
 * @param radius The blur radius to preview.
 * @param onBackClick Callback invoked when the user requests to navigate back (top app bar navigation or system back).
 */
@Composable
private fun BlurDetailScreen(radius: Int, onBackClick: () -> Unit) {
  PlatformBackHandler { onBackClick() }

  val poster = remember { MockUtil.getMockPoster() }

  Scaffold(
    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    topBar = {
      TopAppBar(
        title = { Text("Radius: $radius") },
        backgroundColor = M2MaterialTheme.colors.primary,
        contentColor = M2MaterialTheme.colors.onPrimary,
        navigationIcon = {
          TextButton(onClick = onBackClick) {
            Text(
              text = "←",
              fontSize = 20.sp,
              color = M2MaterialTheme.colors.onPrimary,
            )
          }
        },
      )
    },
    backgroundColor = M2MaterialTheme.colors.background,
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      BlurTestCard(
        title = "Static Blur",
        radius = radius,
        poster = poster,
        animated = false,
      )

      Spacer(modifier = Modifier.height(24.dp))

      BlurTestCard(
        title = "Animated Blur (0 → $radius)",
        radius = radius,
        poster = poster,
        animated = true,
      )

      Spacer(modifier = Modifier.height(24.dp))

      TextBlurTest(radius = radius, poster = poster)
    }
  }
}

/**
 * Displays a card with a title and a poster image that applies a blur preview, either static or animated from 0 to the target radius.
 *
 * @param title The heading text shown at the top of the card.
 * @param radius The target blur radius applied to the poster image and shown as the current radius.
 * @param poster The Poster whose image and metadata are displayed inside the card.
 * @param animated If `true`, the blur animates from 0 up to `radius`; if `false`, the blur is applied at the fixed `radius`.
 */
@Composable
private fun BlurTestCard(title: String, radius: Int, poster: Poster, animated: Boolean) {
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
    backgroundColor = M2MaterialTheme.colors.surface,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = M2MaterialTheme.colors.onSurface,
      )

      Spacer(modifier = Modifier.height(12.dp))

      CoilImage(
        modifier = Modifier
          .size(300.dp)
          .clip(imageShape)
          .background(M2MaterialTheme.colors.surface, imageShape)
          .cloudy(radius = if (animated) animatedRadius else radius),
        imageModel = { poster.image },
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Current radius: ${if (animated) animatedRadius else radius}",
        fontSize = 14.sp,
        color = M2MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
      )
    }
  }
}

/**
 * Displays a titled card that demonstrates text rendered with a configurable blur effect.
 *
 * Renders a "Text Blur Test" card showing the poster's name and a truncated description inside
 * a rounded, blurred content area.
 *
 * @param radius The blur radius applied to the inner content area.
 * @param poster The poster whose `name` and truncated `description` are displayed.
 */
@Composable
private fun TextBlurTest(radius: Int, poster: Poster) {
  val textBlurShape = RoundedCornerShape(12.dp)

  Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = 4.dp,
    shape = RoundedCornerShape(12.dp),
    backgroundColor = M2MaterialTheme.colors.surface,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "Text Blur Test",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = M2MaterialTheme.colors.onSurface,
      )

      Spacer(modifier = Modifier.height(12.dp))

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(textBlurShape)
          .background(M2MaterialTheme.colors.surface, textBlurShape)
          .cloudy(radius = radius),
      ) {
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
          text = poster.name,
          fontSize = 32.sp,
          color = M2MaterialTheme.colors.onSurface,
          textAlign = TextAlign.Center,
        )

        Text(
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
          text = poster.description.take(100) + "...",
          color = M2MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
