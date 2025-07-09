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

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTest {

  @get:Rule
  val dropshots = DropshostsTestRule()

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
    // Launch MainActivity with proper error handling
    val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)

    try {
      ActivityScenario.launch<MainActivity>(intent).use { scenario: ActivityScenario<MainActivity> ->
        scenario.onActivity { activity: MainActivity ->
          // Wait for Compose to render and native libraries to load
          // Using a single optimized wait instead of multiple loops
          Thread.sleep(1000) // Reduced from 1400ms total

          // Ensure UI is settled
          activity.runOnUiThread { // Force a layout pass
            activity.window.decorView.requestLayout()
          }

          // Capture screenshot with error handling
          try {
            dropshots.assertSnapshot(
              view = activity.findViewById(android.R.id.content),
              name = "cloudy_main_screen_$apiSuffix"
            )
            println("Successfully captured screenshot: cloudy_main_screen_$apiSuffix")
          } catch (e: Exception) {
            println("Error capturing screenshot: ${e.message}")
            // Try one more time after reduced wait
            Thread.sleep(1000)
            try {
              dropshots.assertSnapshot(
                view = activity.findViewById(android.R.id.content),
                name = "cloudy_main_screen_$apiSuffix"
              )
              println("Successfully captured screenshot on retry: cloudy_main_screen_$apiSuffix")
            } catch (retryException: Exception) {
              println("Error on retry: ${retryException.message}")
              throw retryException
            }
          }
        }
      }
    } catch (e: Exception) {
      println("Error in captureCloudyMainScreen: ${e.message}")
      throw e
    }
  }

  @Test
  fun testCloudyBlurStates() {
    // Test different blur states by waiting for animation to complete
    val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)

    try {
      ActivityScenario.launch<MainActivity>(intent).use { scenario: ActivityScenario<MainActivity> ->
        scenario.onActivity { activity: MainActivity ->
          // Wait for initial state (no blur)
          Thread.sleep(400) // Optimized single wait

          try {
            dropshots.assertSnapshot(
              view = activity.findViewById(android.R.id.content),
              name = "cloudy_blur_initial_api${Build.VERSION.SDK_INT}"
            )
            println("Captured initial blur state for API ${Build.VERSION.SDK_INT}")
          } catch (e: Exception) {
            println("Error capturing initial blur state: ${e.message}")
            throw e
          }

          // Wait for blur animation to complete
          Thread.sleep(1000) // Animation duration

          try {
            dropshots.assertSnapshot(
              view = activity.findViewById(android.R.id.content),
              name = "cloudy_blur_complete_api${Build.VERSION.SDK_INT}"
            )
            println("Captured complete blur state for API ${Build.VERSION.SDK_INT}")
          } catch (e: Exception) {
            println("Error capturing complete blur state: ${e.message}")
            throw e
          }
        }
      }
    } catch (e: Exception) {
      println("Error in testCloudyBlurStates: ${e.message}")
      throw e
    }
  }

  @Test
  fun testCloudyAnimationStates() {
    // Launch MainActivity and capture different animation states
    val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)

    try {
      ActivityScenario.launch<MainActivity>(intent).use { scenario: ActivityScenario<MainActivity> ->
        scenario.onActivity { activity: MainActivity ->
          // Wait for animation start
          Thread.sleep(400) // Initial state

          try {
            dropshots.assertSnapshot(
              view = activity.findViewById(android.R.id.content),
              name = "cloudy_animation_start_api${Build.VERSION.SDK_INT}"
            )
            println("Captured animation start for API ${Build.VERSION.SDK_INT}")
          } catch (e: Exception) {
            println("Error capturing animation start: ${e.message}")
            throw e
          }

          // Wait for animation to complete
          Thread.sleep(1000) // Full animation duration

          try {
            dropshots.assertSnapshot(
              view = activity.findViewById(android.R.id.content),
              name = "cloudy_animation_end_api${Build.VERSION.SDK_INT}"
            )
            println("Captured animation end for API ${Build.VERSION.SDK_INT}")
          } catch (e: Exception) {
            println("Error capturing animation end: ${e.message}")
            throw e
          }
        }
      }
    } catch (e: Exception) {
      println("Error in testCloudyAnimationStates: ${e.message}")
      throw e
    }
  }
}
