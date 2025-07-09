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
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.dropbox.dropshots.Dropshots
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

/**
 * Custom Dropshots rule that uses internal storage instead of external storage
 * to avoid permission requirements.
 */
class DropshostsTestRule : TestRule {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val dropshots: Dropshots

  init {
    // Create screenshots directory in internal cache
    val screenshotDir = File(context.cacheDir, "dropshots")
    if (!screenshotDir.exists()) {
      screenshotDir.mkdirs()
    }

    // Initialize Dropshots with custom directory
    System.setProperty("dropshots.storage", screenshotDir.absolutePath)
    System.setProperty("dropshots.threshold", "0.15")

    dropshots = Dropshots()
  }

  override fun apply(base: Statement, description: Description): Statement {
    return dropshots.apply(base, description)
  }

  fun assertSnapshot(view: View, name: String) {
    dropshots.assertSnapshot(view, name)
  }
}
