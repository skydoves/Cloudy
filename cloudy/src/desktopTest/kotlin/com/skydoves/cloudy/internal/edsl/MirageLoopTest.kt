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
import com.skydoves.cloudy.MirageShader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

private const val RASTER = 32

/**
 * F6 loops. [unroll] replays the body n times at trace time (no GLSL `for`), so the emitted source has n
 * inlined copies and no loop header. [loop] emits a real ES2-safe `for` with a constant bound and an
 * inner `if (index >= count) break` (KorGE's FOR_0_UNTIL_FIXED_BREAK pattern). Both are checked on the
 * emitted text, and the `unroll` box-blur kernel is also rasterized against a hand-written equivalent.
 */
internal class MirageLoopTest :
  FunSpec({

    test("unroll inlines the body n times with no for header") {
      val kernel = MirageShader.composite("unrollBlur", ::EmptyParams) { xy ->
        var acc by local(half4(0f))
        unroll(3) { i ->
          acc = acc + sampleContent(xy + float2(i.toFloat(), 0f))
        }
        acc * half(1f / 3f)
      }.agsl

      kernel.shouldNotContainFor()
      // Three content taps, one per unrolled iteration.
      Regex("content\\.eval").findAll(kernel).count() shouldBeExactly 3
    }

    test("loop emits an ES2-safe for with a constant bound and an inner break") {
      val kernel = MirageShader.composite("dynLoop", ::EmptyParams) { xy ->
        var acc by local(half4(0f))
        loop(count = float1(4f), maxIterations = 8) { index ->
          acc = acc + sampleContent(xy + float2(index, float1(0f)))
        }
        acc
      }.agsl

      kernel shouldContain "for (int _i = 0; _i < 8; _i++)"
      kernel shouldContain "= float(_i);"
      kernel shouldContain ">= 4.0) break;"
    }

    test("the unroll box-blur rasterizes like a hand-written 3-tap accumulation") {
      meanAbsDiff(
        rasterize(edslUnrollShader(), RASTER),
        rasterize(handRolledUnrollShader(), RASTER),
      ).shouldBeLessThanOrEqual(0.5)
    }
  })

private fun edslUnrollShader(): Shader {
  val kernel = MirageShader.composite("unrollBlurRaster", ::EmptyParams) { xy ->
    var acc by local(half4(0f))
    unroll(3) { i ->
      acc = acc + sampleContent(xy + float2(i.toFloat() * 2f, 0f))
    }
    acc * half(1f / 3f)
  }.agsl
  return bind(kernel)
}

private fun handRolledUnrollShader(): Shader = bind(
  """
  half4 main(float2 xy) {
    half4 acc = half4(0.0);
    acc = acc + content.eval(xy + float2(0.0, 0.0));
    acc = acc + content.eval(xy + float2(2.0, 0.0));
    acc = acc + content.eval(xy + float2(4.0, 0.0));
    return acc * half(1.0 / 3.0);
  }
  """.trimIndent(),
)

/**
 * Prepends the `uniform shader content;` declaration the compiler's preamble would supply (the emitted
 * `main` body assumes it in scope), then binds a content child — enough to rasterize the body standalone.
 */
private fun bind(mainBody: String): Shader {
  val source = "uniform shader content;\n$mainBody"
  val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(source))
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

private infix fun Int.shouldBeExactly(expected: Int) {
  if (this != expected) throw AssertionError("expected $expected but was $this")
}

private fun String.shouldNotContainFor() {
  if (contains("for (")) throw AssertionError("unroll must not emit a for loop:\n$this")
}
