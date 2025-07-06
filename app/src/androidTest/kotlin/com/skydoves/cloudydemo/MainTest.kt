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
package com.skydoves.cloudydemo

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dropbox.dropshots.Dropshots
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudydemo.model.MockUtil
import com.skydoves.cloudydemo.theme.PosterTheme
import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule
  val dropshots = Dropshots()

  @Before
  fun setup() {
    // Verify animation is disabled and wait for initial setup
    composeTestRule.activity.runOnUiThread {
      // Wait for activity to be ready
      Thread.sleep(100)
    }
  }

  @Test
  fun testCloudyMainScreenApi27() {
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
      captureCloudyMainScreen("api27")
    }
  }

  @Test
  fun testCloudyMainScreenApi30() {
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
      captureCloudyMainScreen("api30")
    }
  }

  @Test
  fun testCloudyMainScreenApi33() {
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU) {
      captureCloudyMainScreen("api33")
    }
  }

  @Test
  fun testCloudyMainScreenGeneric() {
    // Generic test that runs regardless of API level
    captureCloudyMainScreen("api${Build.VERSION.SDK_INT}")
  }

  private fun captureCloudyMainScreen(apiSuffix: String) {
    composeTestRule.setContent {
      PosterTheme {
        // Test actual Cloudy library's Native RenderScript functionality
        CloudyMainTestContent()
      }
    }

    // Wait for Cloudy animation to complete
    composeTestRule.waitForIdle()

    // Additional wait for native blur processing to complete
    Thread.sleep(3000)

    // Capture and save screenshot
    dropshots.assertSnapshot(
      view = composeTestRule.activity.findViewById(android.R.id.content),
      name = "cloudy_main_screen_$apiSuffix"
    )
  }

  @Test
  fun testCloudyBlurStates() {
    val blurRadiuses = listOf(0, 15, 30, 45)

    blurRadiuses.forEach { radius ->
      composeTestRule.setContent {
        PosterTheme {
          CloudyBlurTestComponent(blurRadius = radius)
        }
      }

      composeTestRule.waitForIdle()
      Thread.sleep(2000) // Wait for blur processing to complete

      dropshots.assertSnapshot(
        view = composeTestRule.activity.findViewById(android.R.id.content),
        name = "cloudy_blur_radius_${radius}_api${Build.VERSION.SDK_INT}"
      )
    }
  }

  @Test
  fun testCloudyAnimationStates() {
    composeTestRule.setContent {
      PosterTheme {
        CloudyAnimationTestComponent()
      }
    }

    // Before animation starts (radius = 0)
    composeTestRule.waitForIdle()
    Thread.sleep(500)

    dropshots.assertSnapshot(
      view = composeTestRule.activity.findViewById(android.R.id.content),
      name = "cloudy_animation_start_api${Build.VERSION.SDK_INT}"
    )

    // After animation completes (radius = 45)
    Thread.sleep(3000) // Wait for animation to complete

    dropshots.assertSnapshot(
      view = composeTestRule.activity.findViewById(android.R.id.content),
      name = "cloudy_animation_end_api${Build.VERSION.SDK_INT}"
    )
  }
}

@Composable
private fun CloudyMainTestContent() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    var animationPlayed by remember { mutableStateOf(false) }
    val radius by animateIntAsState(
      targetValue = if (animationPlayed) 45 else 0,
      animationSpec = tween(
        durationMillis = 1000,
        delayMillis = 500,
        easing = FastOutLinearInEasing
      ),
      label = "Blur Animation"
    )

    LaunchedEffect(Unit) {
      delay(100) // Wait for initial rendering to complete
      animationPlayed = true
    }

    val poster = remember { MockUtil.getMockPoster() }

    // Test Cloudy modifier using Native RenderScript Toolkit
    Image(
      painter = painterResource(id = R.drawable.poster),
      contentDescription = "Test Image",
      modifier = Modifier
        .size(400.dp)
        .cloudy(radius = radius) // Using actual Native library
    )

    Column(modifier = Modifier.cloudy(radius = radius)) {
      Text(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
        text = poster.name,
        fontSize = 40.sp,
        color = MaterialTheme.colors.onBackground,
        textAlign = TextAlign.Center
      )

      Text(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
        text = poster.description,
        color = MaterialTheme.colors.onBackground,
        textAlign = TextAlign.Center
      )
    }

    // Display API information
    Text(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      text = "API Level: ${Build.VERSION.SDK_INT}\nBlur Radius: $radius",
      color = MaterialTheme.colors.onBackground,
      textAlign = TextAlign.Center,
      fontSize = 14.sp
    )
  }
}

@Composable
private fun CloudyBlurTestComponent(blurRadius: Int) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF1976D2)),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      val poster = remember { MockUtil.getMockPoster() }

      Image(
        painter = painterResource(id = R.drawable.poster),
        contentDescription = "Blur Test Image",
        modifier = Modifier
          .size(300.dp)
          .cloudy(radius = blurRadius) // Use Native RenderScript
      )

      Text(
        text = "Blur Radius: $blurRadius\nAPI: ${Build.VERSION.SDK_INT}",
        style = MaterialTheme.typography.h6,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(16.dp)
      )
    }
  }
}

@Composable
private fun CloudyAnimationTestComponent() {
  var animationPlayed by remember { mutableStateOf(false) }
  val radius by animateIntAsState(
    targetValue = if (animationPlayed) 45 else 0,
    animationSpec = tween(
      durationMillis = 2000,
      delayMillis = 1000,
      easing = FastOutLinearInEasing
    ),
    label = "Animation Test"
  )

  LaunchedEffect(Unit) {
    delay(500) // Wait for initial state capture
    animationPlayed = true
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF4CAF50)),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Image(
        painter = painterResource(id = R.drawable.poster),
        contentDescription = "Animation Test Image",
        modifier = Modifier
          .size(350.dp)
          .cloudy(radius = radius)
      )

      Text(
        text = "Animation Test\nRadius: $radius\nAPI: ${Build.VERSION.SDK_INT}",
        style = MaterialTheme.typography.h5,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(24.dp)
      )
    }
  }
}
