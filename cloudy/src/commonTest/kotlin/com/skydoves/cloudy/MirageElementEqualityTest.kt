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
@file:OptIn(ExperimentalMirage::class)

package com.skydoves.cloudy.internal

import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.MirageClock
import com.skydoves.cloudy.MirageOptics
import com.skydoves.cloudy.MirageParams
import com.skydoves.cloudy.MirageScope
import com.skydoves.cloudy.Sky
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [WeatherElement] equality must include the per-stage params-block identity, so a recomposition that
 * re-creates the block (e.g. to feed a freshly measured lens center or an animated uniform) produces
 * an *unequal* element and Compose calls `update()` to adopt it. If equality excluded the blocks, the
 * node would freeze on whatever block it first captured and never see a later-seeded lens center.
 *
 * This tests the equality contract directly (pure logic, no GraphicsContext); the "cheap update()
 * path when only blocks changed" lives in [WeatherNode.update] and is covered by the desktop raster /
 * cache tests plus the on-device screenshot specs.
 */
internal class MirageElementEqualityTest :
  FunSpec({

    fun element(block: (MirageParams.() -> Unit)?): WeatherElement = WeatherElement(
      weather = MirageWeather,
      skylight = Skylight.SelfLit,
      clock = MirageClock.Auto,
      enabled = true,
      plan = { filter(MirageOptics.OilSlick, block) },
    )

    test("same optic but different block instances are unequal (update runs on recomposition)") {
      // Two distinct lambda instances, as a recomposition would produce, over the same optic. The body
      // is irrelevant to equality — only the reference identity is compared.
      val first = element { }
      val second = element { }

      (first == second).shouldBe(false)
    }

    test("the same block instance yields equal elements (no needless update)") {
      val block: MirageParams.() -> Unit = { }
      val first = element(block)
      val second = element(block)

      (first == second).shouldBe(true)
      first.hashCode().shouldBe(second.hashCode())
    }

    test("a null block equals another null block (params-less plans reconcile)") {
      val first = element(null)
      val second = element(null)

      (first == second).shouldBe(true)
    }

    test("OilSlick and Pearl share one block instance and reconcile equal at the element level") {
      // OilSlick and Pearl are .equals-equal optics (same kernel), so with a shared block instance the
      // elements ARE equal here. The look difference between them lives in the schema defaults reached
      // at draw time, not in element identity.
      val block: MirageParams.() -> Unit = { }
      val oil = WeatherElement(
        MirageWeather,
        Skylight.SelfLit,
        MirageClock.Auto,
        true,
        plan = { filter(MirageOptics.OilSlick, block) },
      )
      val pearl = WeatherElement(
        MirageWeather,
        Skylight.SelfLit,
        MirageClock.Auto,
        true,
        plan = { filter(MirageOptics.Pearl, block) },
      )

      (oil == pearl).shouldBe(true)
    }

    test("a self-lit element is never equal to a backdrop element of the same plan") {
      // The merged node carries its stage-0 source in the equality key: a self-lit plan (null sky) must
      // reconcile distinctly from an otherwise-identical backdrop plan, or Compose would keep the wrong
      // source. Backdrop carries a Sky, self-lit carries none, so their keys differ.
      val block: MirageParams.() -> Unit = { }
      val plan: MirageScope.() -> Unit = { filter(MirageOptics.OilSlick, block) }
      val selfLit =
        WeatherElement(MirageWeather, Skylight.SelfLit, MirageClock.Auto, true, plan)
      val backdrop =
        WeatherElement(MirageWeather, Skylight.Backdrop(Sky()), MirageClock.Auto, true, plan)

      (selfLit == backdrop).shouldBe(false)
    }
  })
