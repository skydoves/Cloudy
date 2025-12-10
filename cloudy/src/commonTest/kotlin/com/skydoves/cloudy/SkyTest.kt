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
package com.skydoves.cloudy

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Unit tests for [Sky] state holder.
 *
 * Tests verify:
 * - Initial state (backgroundLayer null, isDirty true, bounds zero)
 * - Invalidation behavior
 * - isDirty flag lifecycle
 */
internal class SkyTest : FunSpec({

  test("Sky should have null backgroundLayer initially") {
    val sky = Sky()
    sky.backgroundLayer.shouldBeNull()
  }

  test("Sky should have zero bounds initially") {
    val sky = Sky()
    sky.sourceBounds.left.shouldBe(0f)
    sky.sourceBounds.top.shouldBe(0f)
    sky.sourceBounds.right.shouldBe(0f)
    sky.sourceBounds.bottom.shouldBe(0f)
  }

  test("Sky should have isDirty true initially") {
    val sky = Sky()
    sky.isDirty.shouldBeTrue()
  }

  test("invalidate() should set isDirty to true") {
    val sky = Sky()
    sky.isDirty = false

    sky.invalidate()

    sky.isDirty.shouldBeTrue()
  }

  test("isDirty should remain false until invalidated") {
    val sky = Sky()
    sky.isDirty = false

    sky.isDirty.shouldBeFalse()

    sky.invalidate()

    sky.isDirty.shouldBeTrue()
  }

  test("contentVersion should be 0 initially") {
    val sky = Sky()
    sky.contentVersion.shouldBe(0L)
  }

  test("incrementContentVersion should increment version by 1") {
    val sky = Sky()
    sky.contentVersion.shouldBe(0L)

    sky.incrementContentVersion()
    sky.contentVersion.shouldBe(1L)

    sky.incrementContentVersion()
    sky.contentVersion.shouldBe(2L)

    sky.incrementContentVersion()
    sky.contentVersion.shouldBe(3L)
  }
})
