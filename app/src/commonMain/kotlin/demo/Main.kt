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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.skydoves.cloudy.cloudy
import com.skydoves.landscapist.coil3.CoilImage
import demo.model.MockUtil
import demo.model.Poster
import demo.theme.PosterTheme
import androidx.compose.material.MaterialTheme as M2MaterialTheme

/**
 * Hosts the Cloudy demo UI with menu-driven navigation to multiple blur demo scenarios.
 *
 * Uses Navigation3's NavDisplay for type-safe navigation between screens.
 * The content is wrapped in PosterTheme and transitions between views use animated enter/exit transitions.
 */
@Composable
fun CloudyDemoApp() {
  PosterTheme {
    val backStack = remember { mutableStateListOf<Route>(Route.MenuHome) }

    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      entryProvider = { route ->
        when (route) {
          is Route.MenuHome -> NavEntry(route) {
            MenuHomeScreen(
              onGridListClick = { backStack.add(Route.GridList) },
              onRadiusItemsClick = { backStack.add(Route.RadiusItems) },
              onBlurAppBarGridClick = { backStack.add(Route.BlurAppBarGrid) },
            )
          }

          is Route.GridList -> NavEntry(route) {
            GridListScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.RadiusItems -> NavEntry(route) {
            RadiusItemsScreen(
              onRadiusSelected = { radius -> backStack.add(Route.RadiusDetail(radius)) },
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.RadiusDetail -> NavEntry(route) {
            RadiusDetailScreen(
              radius = route.radius,
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.BlurAppBarGrid -> NavEntry(route) {
            BlurAppBarGridScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }
        }
      },
      transitionSpec = {
        slideInHorizontally { it } + fadeIn() togetherWith
          slideOutHorizontally { -it } + fadeOut()
      },
      popTransitionSpec = {
        slideInHorizontally { -it } + fadeIn() togetherWith
          slideOutHorizontally { it } + fadeOut()
      },
    )
  }
}

private val testRadiusList = listOf(0, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100)

// region Menu Home Screen

/**
 * Menu home screen displaying a list of blur demo scenarios.
 *
 * @param onGridListClick Callback when the Grid List demo is selected.
 * @param onRadiusItemsClick Callback when the Radius Items demo is selected.
 * @param onBlurAppBarGridClick Callback when the Blur AppBar Grid demo is selected.
 */
@Composable
private fun MenuHomeScreen(
  onGridListClick: () -> Unit,
  onRadiusItemsClick: () -> Unit,
  onBlurAppBarGridClick: () -> Unit,
) {
  val posters = remember { MockUtil.getMockPosters() }

  Scaffold(
    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    topBar = {
      TopAppBar(
        title = { Text("Cloudy Demos") },
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
      verticalArrangement = Arrangement.spacedBy(16.dp),
      contentPadding = PaddingValues(16.dp),
    ) {
      item {
        MenuCard(
          title = "Grid List",
          description = "Disney posters in a 2-column grid with static blur",
          poster = posters[0],
          onClick = onGridListClick,
        )
      }
      item {
        MenuCard(
          title = "Radius Items",
          description = "Explore different blur radii with animated detail view",
          poster = posters[1],
          onClick = onRadiusItemsClick,
        )
      }
      item {
        MenuCard(
          title = "Blur AppBar",
          description = "Grid with frosted glass app bar overlay",
          poster = posters[2],
          onClick = onBlurAppBarGridClick,
        )
      }
    }
  }
}

/**
 * A clickable menu card with a poster background and title/description overlay.
 */
@Composable
private fun MenuCard(
  title: String,
  description: String,
  poster: Poster,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .height(180.dp)
      .clickable(onClick = onClick),
    elevation = 4.dp,
    shape = RoundedCornerShape(12.dp),
  ) {
    Box {
      CoilImage(
        modifier = Modifier
          .fillMaxSize()
          .cloudy(radius = 10),
        imageModel = { poster.image },
      )

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(M2MaterialTheme.colors.surface.copy(alpha = 0.7f)),
      )

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = title,
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          color = M2MaterialTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = description,
          fontSize = 14.sp,
          color = M2MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
        )
      }

      Text(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(end = 16.dp),
        text = "->",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = M2MaterialTheme.colors.primary,
      )
    }
  }
}

// endregion

// region Grid List Screen

/**
 * Grid list screen showing Disney posters in a 2-column grid with static blur.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@Composable
private fun GridListScreen(onBackClick: () -> Unit) {
  val posters = remember { MockUtil.getMockPosters() }

  Scaffold(
    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    topBar = {
      TopAppBar(
        title = { Text("Grid List") },
        backgroundColor = M2MaterialTheme.colors.primary,
        contentColor = M2MaterialTheme.colors.onPrimary,
        navigationIcon = {
          TextButton(onClick = onBackClick) {
            Text(
              text = "<-",
              fontSize = 20.sp,
              color = M2MaterialTheme.colors.onPrimary,
            )
          }
        },
      )
    },
    backgroundColor = M2MaterialTheme.colors.background,
  ) { paddingValues ->
    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(posters) { poster ->
        GridPosterItem(poster = poster, blurRadius = 15)
      }
    }
  }
}

/**
 * A grid item displaying a poster with applied blur effect.
 */
@Composable
private fun GridPosterItem(poster: Poster, blurRadius: Int) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(0.7f),
    elevation = 4.dp,
    shape = RoundedCornerShape(8.dp),
  ) {
    Box {
      CoilImage(
        modifier = Modifier
          .fillMaxSize()
          .cloudy(radius = blurRadius),
        imageModel = { poster.image },
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .background(M2MaterialTheme.colors.surface.copy(alpha = 0.8f))
          .padding(8.dp),
      ) {
        Text(
          text = poster.name,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = M2MaterialTheme.colors.onSurface,
          maxLines = 1,
        )
      }
    }
  }
}

// endregion

// region Radius Items Screen

/**
 * Radius items screen showing a list of blur radius options with Disney thumbnail previews.
 *
 * @param onRadiusSelected Callback with the selected radius when a list item is tapped.
 * @param onBackClick Callback when the back button is pressed.
 */
@Composable
private fun RadiusItemsScreen(
  onRadiusSelected: (Int) -> Unit,
  onBackClick: () -> Unit,
) {
  Scaffold(
    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    topBar = {
      TopAppBar(
        title = { Text("Radius Items") },
        backgroundColor = M2MaterialTheme.colors.primary,
        contentColor = M2MaterialTheme.colors.onPrimary,
        navigationIcon = {
          TextButton(onClick = onBackClick) {
            Text(
              text = "<-",
              fontSize = 20.sp,
              color = M2MaterialTheme.colors.onPrimary,
            )
          }
        },
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
        RadiusItem(
          radius = radius,
          onClick = { onRadiusSelected(radius) },
        )
      }
    }
  }
}

/**
 * A clickable card representing a blur radius with a preview image and descriptive text.
 */
@Composable
private fun RadiusItem(radius: Int, onClick: () -> Unit) {
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
        text = "->",
        fontSize = 24.sp,
        color = M2MaterialTheme.colors.primary,
      )
    }
  }
}

// endregion

// region Radius Detail Screen

/**
 * Radius detail screen with animated blur effect from 0 to the selected radius.
 *
 * @param radius The blur radius to demonstrate.
 * @param onBackClick Callback when the back button is pressed.
 */
@Composable
private fun RadiusDetailScreen(radius: Int, onBackClick: () -> Unit) {
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
              text = "<-",
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
        color = M2MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
      )
    }
  }
}

/**
 * A card displaying a poster with blur effect, optionally animated.
 */
@Composable
private fun RadiusDetailCard(
  title: String,
  radius: Int,
  poster: Poster,
  animated: Boolean,
) {
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

// endregion

// region Blur AppBar Grid Screen

/**
 * Grid screen with a blurred app bar overlay demonstrating real-time background blur.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@Composable
private fun BlurAppBarGridScreen(onBackClick: () -> Unit) {
  val posters = remember { MockUtil.getMockPosters() }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(top = 72.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(posters + posters) { poster ->
        GridPosterItem(poster = poster, blurRadius = 0)
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .cloudy(radius = 15)
        .background(M2MaterialTheme.colors.surface.copy(alpha = 0.3f)),
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onBackClick) {
          Text(
            text = "<-",
            fontSize = 20.sp,
            color = M2MaterialTheme.colors.onSurface,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Blur AppBar",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = M2MaterialTheme.colors.onSurface,
        )
      }
    }
  }
}

// endregion
