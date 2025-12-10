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

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

/**
 * Unit tests for [CloudyProgressive] parameter validation.
 *
 * Tests verify:
 * - TopToBottom: start < end validation, range 0..1 validation
 * - BottomToTop: start > end validation, range 0..1 validation
 * - Edges: fadeDistance range 0..0.5 validation
 * - Default values for all progressive types
 */
internal class CloudyProgressiveTest : FunSpec({

  context("TopToBottom") {
    test("should throw when start >= end") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.TopToBottom(start = 0.6f, end = 0.4f)
      }
    }

    test("should throw when start equals end") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.TopToBottom(start = 0.5f, end = 0.5f)
      }
    }

    test("should throw when start < 0") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.TopToBottom(start = -0.1f, end = 0.5f)
      }
    }

    test("should throw when start > 1") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.TopToBottom(start = 1.1f, end = 1.5f)
      }
    }

    test("should throw when end > 1") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.TopToBottom(start = 0f, end = 1.1f)
      }
    }

    test("should throw when end < 0") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.TopToBottom(start = -0.5f, end = -0.1f)
      }
    }

    test("should accept valid parameters") {
      shouldNotThrow<IllegalArgumentException> {
        CloudyProgressive.TopToBottom(start = 0f, end = 0.5f)
      }
    }

    test("should accept boundary values") {
      shouldNotThrow<IllegalArgumentException> {
        CloudyProgressive.TopToBottom(start = 0f, end = 1f)
      }
    }

    test("should have correct default values") {
      val progressive = CloudyProgressive.TopToBottom()
      progressive.start.shouldBe(0f)
      progressive.end.shouldBe(CloudyDefaults.ProgressiveFadeEnd)
    }
  }

  context("BottomToTop") {
    test("should throw when start <= end") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.BottomToTop(start = 0.4f, end = 0.6f)
      }
    }

    test("should throw when start equals end") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.BottomToTop(start = 0.5f, end = 0.5f)
      }
    }

    test("should throw when start < 0") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.BottomToTop(start = -0.1f, end = -0.5f)
      }
    }

    test("should throw when end > 1") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.BottomToTop(start = 1.5f, end = 1.1f)
      }
    }

    test("should accept valid parameters") {
      shouldNotThrow<IllegalArgumentException> {
        CloudyProgressive.BottomToTop(start = 1f, end = 0.5f)
      }
    }

    test("should accept boundary values") {
      shouldNotThrow<IllegalArgumentException> {
        CloudyProgressive.BottomToTop(start = 1f, end = 0f)
      }
    }

    test("should have correct default values") {
      val progressive = CloudyProgressive.BottomToTop()
      progressive.start.shouldBe(1f)
      progressive.end.shouldBe(1f - CloudyDefaults.ProgressiveFadeEnd)
    }
  }

  context("Edges") {
    test("should throw when fadeDistance > 0.5") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.Edges(fadeDistance = 0.6f)
      }
    }

    test("should throw when fadeDistance < 0") {
      shouldThrow<IllegalArgumentException> {
        CloudyProgressive.Edges(fadeDistance = -0.1f)
      }
    }

    test("should accept valid parameters") {
      shouldNotThrow<IllegalArgumentException> {
        CloudyProgressive.Edges(fadeDistance = 0.3f)
      }
    }

    test("should accept boundary values") {
      shouldNotThrow<IllegalArgumentException> {
        CloudyProgressive.Edges(fadeDistance = 0f)
      }
      shouldNotThrow<IllegalArgumentException> {
        CloudyProgressive.Edges(fadeDistance = 0.5f)
      }
    }

    test("should have correct default values") {
      val progressive = CloudyProgressive.Edges()
      progressive.fadeDistance.shouldBe(CloudyDefaults.EdgesFadeDistance)
    }
  }

  context("None") {
    test("should be a singleton object") {
      val none1 = CloudyProgressive.None
      val none2 = CloudyProgressive.None
      none1.shouldBeSameInstanceAs(none2)
    }
  }
})
