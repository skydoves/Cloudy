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

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.node.ModifierNodeElement
import com.skydoves.cloudy.internal.EffectElement
import com.skydoves.cloudy.internal.MiragePipelineBuilder
import com.skydoves.cloudy.internal.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Smoke tests for the pipeline-based [Modifier.mirage] and the node it attaches.
 *
 * These exercise the parts checkable without a live Compose tree: that the factory attaches a
 * [EffectElement] (the node instantiates), that the pipeline block builds the right ordered stage list,
 * and that the element's reconciliation key (sky / clock / enabled / stages) behaves. The per-draw uniform
 * binding and the RenderEffect / ShaderBrush application run on a real graphics layer and are covered
 * by the platform screenshot/instrumented tests.
 */
@RunWith(RobolectricTestRunner::class)
internal class MiragePipelineModifierTest {

  /** A trivial colorize filter and a trivial generator overlay, enough to populate a pipeline. */
  private val tintFilter: ColorizeShader<MirageParams> = MirageShader.colorize(
    name = "test-tint",
    paramsFactory = { EmptyParams() },
    agsl = "half4 kernel(float2 p, half4 src) { return src; }",
    sksl = "half4 kernel(float2 p, half4 src) { return src; }",
  )

  private val glowOverlay: GeneratorShader<MirageParams> = MirageShader.generate(
    name = "test-glow",
    paramsFactory = { EmptyParams() },
    agsl = "half4 main(float2 xy) { return half4(1.0); }",
    sksl = "half4 main(float2 xy) { return half4(1.0); }",
  )

  private class EmptyParams : MirageParams()

  @Test
  fun `mirage attaches a EffectElement`() {
    val modifier = Modifier.mirage { filter(tintFilter) }
    assertTrue(
      "Modifier.mirage should attach a EffectElement",
      modifier.firstElementOrNull() is EffectElement,
    )
  }

  @Test
  fun `an empty pipeline still attaches a node`() {
    // enabled = false is a node-level no-op (the program cache stays warm), not a modifier
    // short-circuit, so the element is still present.
    val modifier = Modifier.mirage(enabled = false) {}
    assertTrue(modifier.firstElementOrNull() is EffectElement)
  }

  @Test
  fun `pipeline builds stages in declared order`() {
    val stages = MiragePipelineBuilder().apply {
      filter(tintFilter)
      overlay(glowOverlay, blendMode = BlendMode.Plus)
    }.stages

    assertEquals(2, stages.size)
    assertTrue(stages[0] is Stage.ProgramFilter)
    assertTrue(stages[1] is Stage.Overlay)
    assertEquals(BlendMode.Plus, (stages[1] as Stage.Overlay).blendMode)
    assertEquals("test-tint", (stages[0] as Stage.ProgramFilter).shader.name)
  }

  @Test
  fun `each stage mints its own params instance`() {
    val stages = MiragePipelineBuilder().apply {
      filter(tintFilter)
      filter(tintFilter)
    }.stages
    assertNotEquals(
      "Two filter stages of the same shader must not share one params instance",
      (stages[0] as Stage.ProgramFilter).params,
      (stages[1] as Stage.ProgramFilter).params,
    )
  }

  @Test
  fun `elements with the same pipeline and clock are equal`() {
    val a = elementOf(Modifier.mirage(clock = MirageClock.Auto) { filter(tintFilter) })
    val b = elementOf(Modifier.mirage(clock = MirageClock.Auto) { filter(tintFilter) })
    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
  }

  @Test
  fun `toggling enabled changes the reconciliation key`() {
    val on = elementOf(Modifier.mirage(enabled = true) { filter(tintFilter) })
    val off = elementOf(Modifier.mirage(enabled = false) { filter(tintFilter) })
    assertNotEquals(on, off)
  }

  @Test
  fun `a different clock changes the reconciliation key`() {
    val auto = elementOf(Modifier.mirage(clock = MirageClock.Auto) { filter(tintFilter) })
    val fixed = elementOf(Modifier.mirage(clock = MirageClock.Fixed(1f)) { filter(tintFilter) })
    assertNotEquals(auto, fixed)
  }

  @Test
  fun `mirage with sky attaches a EffectElement`() {
    val sky = Sky()
    val modifier = Modifier.mirage(sky = sky) { filter(tintFilter) }
    assertTrue(
      "Modifier.mirage(sky) should attach a EffectElement",
      modifier.firstElementOrNull() is EffectElement,
    )
  }

  @Test
  fun `backdrop elements with the same sky and pipeline and shared block are equal`() {
    val sky = Sky()
    val block: MirageParams.() -> Unit = { }
    val a = backdropElementOf(Modifier.mirage(sky = sky) { filter(tintFilter, block) })
    val b = backdropElementOf(Modifier.mirage(sky = sky) { filter(tintFilter, block) })
    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
  }

  @Test
  fun `backdrop elements with different sky instances are unequal`() {
    // Sky is part of the reconciliation key: a different backdrop must re-create the node's plumbing.
    val block: MirageParams.() -> Unit = { }
    val a = backdropElementOf(Modifier.mirage(sky = Sky()) { filter(tintFilter, block) })
    val b = backdropElementOf(Modifier.mirage(sky = Sky()) { filter(tintFilter, block) })
    assertNotEquals(a, b)
  }

  @Test
  fun `re-creating the params block makes an otherwise-identical backdrop element unequal`() {
    // Two distinct lambda instances (as a recomposition produces) over the same sky + shader: unequal,
    // so Compose runs update() and the node adopts the fresh block (no frozen uniforms).
    val sky = Sky()
    val a = backdropElementOf(Modifier.mirage(sky = sky) { filter(tintFilter) { } })
    val b = backdropElementOf(Modifier.mirage(sky = sky) { filter(tintFilter) { } })
    assertNotEquals(a, b)
  }

  @Test
  fun `a shared params block keeps its identity through the backdrop pipeline`() {
    // The pipeline captures the exact block instance the caller passed (=== preserved), so an unchanged
    // block reconciles equal rather than forcing a needless update.
    val sky = Sky()
    val block: MirageParams.() -> Unit = { }
    val stages = MiragePipelineBuilder().apply { filter(tintFilter, block) }.stages
    assertTrue(
      "the pipeline must keep the caller's exact block instance",
      (stages[0] as Stage.ProgramFilter).paramsBlock === block,
    )
  }

  private fun elementOf(modifier: Modifier): EffectElement =
    modifier.firstElementOrNull() as EffectElement

  private fun backdropElementOf(modifier: Modifier): EffectElement =
    modifier.firstElementOrNull() as EffectElement

  /** foldIn returns the first ModifierNodeElement in the chain (there is exactly one here). */
  private fun Modifier.firstElementOrNull(): ModifierNodeElement<*>? =
    foldIn<ModifierNodeElement<*>?>(null) { acc, element ->
      acc ?: element as? ModifierNodeElement<*>
    }
}
