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

package com.skydoves.cloudy.edsl

import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.MirageParams
import com.skydoves.cloudy.MirageShader
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.MirageProgramCache
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

private const val RASTER = 32

private val WEIGHTS = floatArrayOf(0.4f, 0.3f, 0.2f, 0.1f)

private class ArrayWeightParams : MirageParams() {
  val weights by uniform(WEIGHTS)
}

/**
 * Constant-index reads of a `float[N]` uniform ([UFloatArray.get]). The subscript is a Kotlin `Int`
 * baked in at trace time, so [unroll] walks the whole array with every access constant — checked on
 * the emitted text, proven end-to-end by compiling the full assembled source (array declaration
 * included) through the real skiko [RuntimeEffect] against a hand-written equivalent, and guarded by
 * trace-time bounds / declared-length checks.
 */
internal class MirageArrayUniformTest :
  FunSpec({

    test("unroll reads each element with a constant subscript") {
      val kernel = MirageShader.composite("arrayRead", ::ArrayWeightParams) { xy ->
        var acc by local(half4(0f))
        unroll(4) { i ->
          acc = acc + sampleContent(xy + float2(i.toFloat() * 2f, 0f)) * half(weights[i])
        }
        acc
      }.agsl

      for (i in 0..3) kernel shouldContain "weights[$i]"
    }

    test("an out-of-bounds index fails at trace time") {
      shouldThrow<IllegalArgumentException> {
        MirageShader.composite("arrayOob", ::ArrayWeightParams) { xy ->
          sampleContent(xy) * half(weights[4])
        }
      }
    }

    test("assigning a value of the wrong length fails loudly") {
      shouldThrow<IllegalArgumentException> {
        ArrayWeightParams().weights(floatArrayOf(1f, 2f))
      }
      // The property setter enforces the same invariant, so a direct write cannot bypass it.
      shouldThrow<IllegalArgumentException> {
        ArrayWeightParams().weights.value = floatArrayOf(1f, 2f)
      }
    }

    test("the compiled program declares the array and rasterizes like a hand-written kernel") {
      val shader = MirageShader.composite("arrayRaster", ::ArrayWeightParams) { xy ->
        var acc by local(half4(0f))
        unroll(4) { i ->
          acc = acc + sampleContent(xy + float2(i.toFloat() * 2f, 0f)) * half(weights[i])
        }
        acc
      }
      val cached = MirageProgramCache.obtain(shader, Dialect.Sksl).shouldNotBeNull()
      cached.compiled.source shouldContain "uniform float[4] weights;"

      meanAbsDiff(
        rasterize(bindWeights(cached.compiled.source), RASTER),
        rasterize(bindWeights(handRolledSource()), RASTER),
      ).shouldBeLessThanOrEqual(0.5)
    }
  })

/** The same weighted 4-tap accumulation with the subscripts spelled by hand. */
private fun handRolledSource(): String = """
  uniform float[4] weights;
  uniform shader content;
  half4 main(float2 xy) {
    half4 acc = half4(0.0);
    acc = acc + content.eval(xy + float2(0.0, 0.0)) * half(weights[0]);
    acc = acc + content.eval(xy + float2(2.0, 0.0)) * half(weights[1]);
    acc = acc + content.eval(xy + float2(4.0, 0.0)) * half(weights[2]);
    acc = acc + content.eval(xy + float2(6.0, 0.0)) * half(weights[3]);
    return acc;
  }
""".trimIndent()

private fun bindWeights(source: String): Shader {
  val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(source))
  builder.uniform("weights", WEIGHTS)
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
