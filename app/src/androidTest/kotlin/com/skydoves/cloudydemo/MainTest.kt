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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dropbox.dropshots.Dropshots
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val dropshots = Dropshots()

  @Before
  fun setup() {
    // Wait for activity to be ready using proper synchronization
    composeTestRule.waitForIdle()

    // Ensure animations are disabled for consistent screenshots
    composeTestRule.activity.runOnUiThread {
      // Disable animations if needed
      composeTestRule.mainClock.autoAdvance = false
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
    // Use timestamp to ensure unique filename
    val timestamp = System.currentTimeMillis()
    captureCloudyMainScreen("generic_$timestamp")
  }

  private fun captureCloudyMainScreen(apiSuffix: String) {
    // MainActivity already has content set, no need to call setContent
    // Just wait for the existing content to be ready

    // Wait for animations and blur to complete
    composeTestRule.waitForIdle()

    // Advance clock to ensure animations complete
    composeTestRule.mainClock.advanceTimeBy(1500) // Animation duration + buffer
    composeTestRule.waitForIdle()

    // For native blur processing, implement a callback or use IdlingResource
    composeTestRule.waitUntil(timeoutMillis = 5000) {
      // Check if blur rendering is complete
      // This requires exposing a state from the Cloudy library
      true // Placeholder - implement actual completion check
    }

    // Capture and save screenshot
    dropshots.assertSnapshot(
      view = composeTestRule.activity.findViewById(android.R.id.content),
      name = "cloudy_main_screen_$apiSuffix"
    )
  }

  @Test
  fun testCloudyBlurStates() {
    // Test different blur states by waiting for animation to complete
    // MainActivity already has blur animation, just capture at different times

    // Wait for initial state (no blur)
    composeTestRule.waitForIdle()
    dropshots.assertSnapshot(
      view = composeTestRule.activity.findViewById(android.R.id.content),
      name = "cloudy_blur_initial_api${Build.VERSION.SDK_INT}"
    )

    // Wait for animation to complete (full blur)
    composeTestRule.mainClock.advanceTimeBy(2000)
    composeTestRule.waitForIdle()
    dropshots.assertSnapshot(
      view = composeTestRule.activity.findViewById(android.R.id.content),
      name = "cloudy_blur_complete_api${Build.VERSION.SDK_INT}"
    )
  }

  @Test
  fun testCloudyAnimationStates() {
    // MainActivity already has animation, just capture different states

    // Initial state
    composeTestRule.waitForIdle()
    dropshots.assertSnapshot(
      view = composeTestRule.activity.findViewById(android.R.id.content),
      name = "cloudy_animation_start_api${Build.VERSION.SDK_INT}"
    )

    // Final state after animation
    composeTestRule.mainClock.advanceTimeBy(2000)
    composeTestRule.waitForIdle()
    dropshots.assertSnapshot(
      view = composeTestRule.activity.findViewById(android.R.id.content),
      name = "cloudy_animation_end_api${Build.VERSION.SDK_INT}"
    )
  }
}
