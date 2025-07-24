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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.io.FileOutputStream

/**
 * Custom screenshot test rule that doesn't require any permissions.
 * Uses internal app storage to save screenshots for CI collection.
 */
class ScreenshotTestRule : TestRule {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val screenshotDir: File

  init {
    // Create screenshots directory in internal cache (no permissions needed)
    screenshotDir = File(context.cacheDir, "screenshots")
    if (!screenshotDir.exists()) {
      screenshotDir.mkdirs()
    }
    println("Screenshot directory created at: ${screenshotDir.absolutePath}")
  }

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        try {
          base.evaluate()
        } finally {
          // Cleanup if needed
        }
      }
    }
  }

  fun takeScreenshot(view: View, name: String) {
    try {
      // Create bitmap from view
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      view.draw(canvas)

      // Save to internal cache directory
      val file = File(screenshotDir, "$name.png")
      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      }

      println("Screenshot saved: ${file.absolutePath}")
      println("Screenshot size: ${file.length()} bytes")

      // Also try to save to external accessible location for CI collection
      try {
        val externalFile = File("/data/data/${context.packageName}/cache/screenshots", "$name.png")
        externalFile.parentFile?.mkdirs()
        FileOutputStream(externalFile).use { out ->
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        println("External screenshot saved: ${externalFile.absolutePath}")
      } catch (e: Exception) {
        println("External save failed (expected): ${e.message}")
      }
    } catch (e: Exception) {
      println("Screenshot capture failed: ${e.message}")
      throw AssertionError("Failed to capture screenshot: ${e.message}")
    }
  }
}
