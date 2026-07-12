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

package com.skydoves.cloudy

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.skydoves.cloudy.internal.MirageFilterChain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Host-side unit tests for [MirageFilterChain]'s source-injection contract — the seam that makes the
 * chain reusable across stage-0 sources (the self content and the backdrop region), which
 * [com.skydoves.cloudy.internal.MirageEffect] supplies as its `recordSource`.
 *
 * The empty-applicable path is the cleanly testable slice: it draws the caller's `recordSource`
 * straight to the screen and never touches a real GPU layer, so it runs on the Robolectric host with a
 * `ContentDrawScope` that delegates to a bare [CanvasDrawScope] (no canvas is needed — the path invokes
 * only the injected `recordSource` lambda, not any draw primitive). The populated-chain draw records
 * into real [GraphicsLayer]s and is covered by the platform screenshot/instrumented specs.
 */
@RunWith(RobolectricTestRunner::class)
internal class MirageFilterChainTest {

  @Test
  fun `empty applicable runs recordSource and never binds`() {
    val chain = MirageFilterChain()
    var recorded = false
    var bound = false

    with(chain) {
      fakeContentDrawScope().draw(
        context = throwingGraphicsContext(),
        applicable = emptyList(),
        bind = { _, _ -> bound = true },
        recordSource = { recorded = true },
      )
    }

    assertTrue("empty applicable must draw the source directly", recorded)
    assertFalse("empty applicable must not bind any stage program", bound)
  }

  @Test
  fun `empty applicable draws the source without allocating a layer`() {
    // The empty path must not reach the graphics context (no pooled layer to create), so a context
    // that throws on any use proves the fallback is allocation-free — the API < 33 "raw region" case.
    val chain = MirageFilterChain()

    with(chain) {
      fakeContentDrawScope().draw(
        context = throwingGraphicsContext(),
        applicable = emptyList(),
        bind = { _, _ -> error("bind must not run for an empty pipeline") },
        recordSource = { /* no-op source */ },
      )
    }
    // Reaching here (no throw from the context stub) is the assertion.
  }

  @Test
  fun `release on an untouched chain is a no-op`() {
    // A chain that never drew has no pooled layers, so release must not call into the context.
    MirageFilterChain().release(throwingGraphicsContext())
  }

  /**
   * A [ContentDrawScope] for the empty-applicable path: delegates the [androidx.compose.ui.graphics.
   * drawscope.DrawScope] surface to a bare [CanvasDrawScope] and stubs [drawContent]. The tested path
   * invokes only the injected `recordSource` lambda, so no delegated draw primitive is exercised.
   */
  private fun fakeContentDrawScope(): ContentDrawScope =
    object : ContentDrawScope, DrawScope by CanvasDrawScope() {
      override fun drawContent() = Unit
    }

  /** A [GraphicsContext] that fails if the empty path ever tries to create or release a layer. */
  private fun throwingGraphicsContext(): GraphicsContext = object : GraphicsContext {
    override fun createGraphicsLayer(): GraphicsLayer =
      error("empty-applicable draw must not create a graphics layer")

    override fun releaseGraphicsLayer(layer: GraphicsLayer) =
      error("empty-applicable draw must not release a graphics layer")
  }
}
