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
package com.skydoves.cloudy.internals

import androidx.compose.ui.graphics.Color
import com.skydoves.cloudy.CloudyProgressive
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for [SkySnapshot] creation and direction mapping.
 */
internal class SkySnapshotTest : FunSpec({

  context("fromProgressive") {
    test("should create snapshot with NONE direction for CloudyProgressive.None") {
      val snapshot = SkySnapshot.fromProgressive(
        radius = 20,
        offsetX = 10f,
        offsetY = 20f,
        childWidth = 100f,
        childHeight = 200f,
        progressive = CloudyProgressive.None,
        tintColor = Color.Transparent,
      )

      snapshot.direction.shouldBe(SkySnapshot.ProgressiveDirection.NONE)
      snapshot.radius.shouldBe(20)
      snapshot.offsetX.shouldBe(10f)
      snapshot.offsetY.shouldBe(20f)
      snapshot.childWidth.shouldBe(100f)
      snapshot.childHeight.shouldBe(200f)
    }

    test("should create snapshot with TOP_TO_BOTTOM direction") {
      val snapshot = SkySnapshot.fromProgressive(
        radius = 25,
        offsetX = 0f,
        offsetY = 0f,
        childWidth = 100f,
        childHeight = 100f,
        progressive = CloudyProgressive.TopToBottom(start = 0.1f, end = 0.9f),
        tintColor = Color.White,
      )

      snapshot.direction.shouldBe(SkySnapshot.ProgressiveDirection.TOP_TO_BOTTOM)
      snapshot.fadeStart.shouldBe(0.1f)
      snapshot.fadeEnd.shouldBe(0.9f)
      snapshot.tintColor.shouldBe(Color.White)
    }

    test("should create snapshot with BOTTOM_TO_TOP direction") {
      val snapshot = SkySnapshot.fromProgressive(
        radius = 30,
        offsetX = 0f,
        offsetY = 0f,
        childWidth = 100f,
        childHeight = 100f,
        progressive = CloudyProgressive.BottomToTop(start = 0.9f, end = 0.1f),
        tintColor = Color.Black,
      )

      snapshot.direction.shouldBe(SkySnapshot.ProgressiveDirection.BOTTOM_TO_TOP)
      snapshot.fadeStart.shouldBe(0.9f)
      snapshot.fadeEnd.shouldBe(0.1f)
      snapshot.tintColor.shouldBe(Color.Black)
    }

    test("should create snapshot with EDGES direction") {
      val snapshot = SkySnapshot.fromProgressive(
        radius = 15,
        offsetX = 50f,
        offsetY = 50f,
        childWidth = 200f,
        childHeight = 200f,
        progressive = CloudyProgressive.Edges(fadeDistance = 0.3f),
        tintColor = Color.Gray,
      )

      snapshot.direction.shouldBe(SkySnapshot.ProgressiveDirection.EDGES)
      snapshot.fadeStart.shouldBe(0.3f)
      snapshot.fadeEnd.shouldBe(0.7f) // 1 - 0.3
    }
  }

  context("ProgressiveDirection enum") {
    test("should have correct ordinal values") {
      SkySnapshot.ProgressiveDirection.NONE.ordinal.shouldBe(0)
      SkySnapshot.ProgressiveDirection.TOP_TO_BOTTOM.ordinal.shouldBe(1)
      SkySnapshot.ProgressiveDirection.BOTTOM_TO_TOP.ordinal.shouldBe(2)
      SkySnapshot.ProgressiveDirection.EDGES.ordinal.shouldBe(3)
    }

    test("should have all four directions") {
      SkySnapshot.ProgressiveDirection.entries.size.shouldBe(4)
    }
  }
})
