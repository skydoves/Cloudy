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

package com.skydoves.cloudy.internal.edsl

import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.MirageShaders
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.MirageProgramCache
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

private const val RASTER = 32

/**
 * MVP proof that [MirageShaders.Duotone] — now traced through the eDSL (see [traceDuotone] /
 * [emitColorizeKernel]) instead of authored as two hand-written AGSL/SkSL strings — still compiles
 * through the real skiko [RuntimeEffect] and rasterizes byte-for-byte identically to a build of the
 * old hand-written kernel text. This is the design's raster-parity gate (mirage-edsl-design.md §9.2,
 * §11): a text diff would catch a wording change, but only a pixel diff proves the *emitted GLSL*
 * still means the same thing. [handRolledDuotoneShader] is a real color blend (not identity), so this
 * one byte-identity assertion also rules out a regression to a passthrough no-op.
 */
internal class MirageEdslDuotoneTest :
  FunSpec({

    test("MirageShaders.Duotone compiles and rasterizes through the real skiko RuntimeEffect") {
      meanAbsDiff(
        rasterize(buildEdslDuotoneShader(), RASTER),
        rasterize(handRolledDuotoneShader(), RASTER),
      ) shouldBe 0.0
    }
  })

/** Binds [MirageShaders.Duotone]'s eDSL-traced, program-cache-compiled source to a live shader. */
private fun buildEdslDuotoneShader(): Shader {
  val cached = MirageProgramCache.obtain(MirageShaders.Duotone, Dialect.Sksl).shouldNotBeNull()
  return bindDuotoneUniforms(RuntimeShaderBuilder(RuntimeEffect.makeForShader(cached.compiled.source)))
}

/**
 * The pre-eDSL kernel text (what `DUOTONE_KERNEL_SKSL` used to read before this MVP), kept here only
 * as the golden reference for the raster-parity test above — not reachable from production code.
 */
private fun handRolledDuotoneShader(): Shader {
  // layout(color) uniforms and the kernel/main split match MirageCompiler.assemble's actual output
  // shape byte-for-byte (see cached.compiled.source in buildEdslDuotoneShader) — only the luma+mix
  // expression is spelled with the old intermediate half/half3 locals instead of the eDSL's
  // fully-inlined form, which is exactly the thing this test is proving compiles to the same pixels.
  val source = """
    layout(color) uniform float4 shadow;
    layout(color) uniform float4 highlight;
    uniform float amount;
    uniform shader content;

    half4 kernel(float2 p, half4 src) {
        half g = half(dot(src.rgb, half3(0.2126, 0.7152, 0.0722)));
        half3 dz = mix(half3(shadow.rgb), half3(highlight.rgb), g);
        return half4(mix(src.rgb, dz, half(amount)), src.a);
    }
    half4 main(float2 xy) { return kernel(xy, content.eval(xy)); }
  """.trimIndent()
  return bindDuotoneUniforms(RuntimeShaderBuilder(RuntimeEffect.makeForShader(source)))
}

/** The one non-default Duotone look both shaders under test are compared at. */
private fun bindDuotoneUniforms(builder: RuntimeShaderBuilder): Shader {
  builder.uniform("shadow", 0.106f, 0.106f, 0.227f, 1f)
  builder.uniform("highlight", 1f, 0.910f, 0.780f, 1f)
  builder.uniform("amount", 1f)
  builder.child("content", contentShader())
  return builder.makeShader()
}

private fun contentShader(): Shader = RuntimeEffect.makeForShader(
  """
  half4 main(float2 xy) {
    float2 uv = xy / float2($RASTER.0, $RASTER.0);
    return half4(half(uv.x), half(uv.y), half(1.0 - uv.x), 1.0);
  }
  """.trimIndent(),
).let { RuntimeShaderBuilder(it).makeShader() }
