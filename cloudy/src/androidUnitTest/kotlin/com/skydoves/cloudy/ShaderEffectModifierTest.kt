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
@file:OptIn(ExperimentalShaderEffect::class)

package com.skydoves.cloudy

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [Modifier.shaderEffect] and [ShaderEffectDefaults].
 *
 * Mirrors [LiquidGlassModifierTest]: it verifies the defaults and the branching constants that are
 * checkable without a live Compose tree. The `enabled = false -> returns the original Modifier`
 * behavior and the actual uniform/RenderEffect wiring live in the platform `actual` (owned by the
 * graphics layer) and are staged below as TODOs, to be activated after that file is integrated.
 */
@RunWith(RobolectricTestRunner::class)
internal class ShaderEffectModifierTest {

  @Test
  fun `ShaderEffectDefaults mirror the liquid glass lens defaults`() {
    assertEquals(LiquidGlassDefaults.LENS_SIZE, ShaderEffectDefaults.LensSize)
    assertEquals(Size(350f, 350f), ShaderEffectDefaults.LensSize)
    assertEquals(LiquidGlassDefaults.CORNER_RADIUS, ShaderEffectDefaults.CornerRadius)
    assertEquals(50f, ShaderEffectDefaults.CornerRadius)
  }

  @Test
  fun `default lens center is the content origin`() {
    assertEquals(Offset.Zero, ShaderEffectDefaults.LensCenter)
  }

  // TODO(graphics integration): once ShaderEffect.android.kt provides the actual, assert that
  //   Modifier.shaderEffect(recipe, enabled = false) returns the same Modifier instance (no-op),
  //   and that API 33+ takes the RuntimeShader path while API < 33 is a no-op (no shader fallback),
  //   following the @Config(sdk = ...) pattern in LiquidGlassModifierTest. Requires the graphics
  //   actual + ShaderRecipes.kt; until then the composable cannot be referenced from a unit test.
}
