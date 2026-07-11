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

import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.MirageCompiler
import com.skydoves.cloudy.internal.MirageGlslEs
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Structural checks on the AGSL -> GLSL ES 3.0 translation. A device compile is what ultimately
 * validates it (the GLES roundtrip test), but these catch the mechanical transforms off-device: no
 * device can tell you *why* a shader failed to compile as fast as a token assertion here.
 */
internal class MirageGlslEsTest :
  FunSpec({

    // Translate the real assembled Duotone (Colorize) program the way the GLES backend will.
    val duotone = MirageCompiler.compile(MirageOptics.Duotone, Dialect.GlslEs)
    val glslDuotone = MirageGlslEs.translate(duotone.source)

    // And the Chromatic (Composite, lens preamble + free content sampling) program.
    val chromatic = MirageCompiler.compile(MirageOptics.Chromatic, Dialect.GlslEs)
    val glslChromatic = MirageGlslEs.translate(chromatic.source)

    test("emits the GLSL ES 3.0 header and highp precision") {
      glslDuotone shouldContain "#version 300 es"
      glslDuotone shouldContain "precision highp float;"
    }

    test("removes every AGSL-only type token") {
      for (glsl in listOf(glslDuotone, glslChromatic)) {
        // No bare `half`, `half3`, `float2`, ... survive; identifiers that merely contain them are kept.
        Regex("\\bhalf\\d?\\b").containsMatchIn(glsl) shouldBe false
        Regex("\\bfloat[234]\\b").containsMatchIn(glsl) shouldBe false
      }
    }

    test("keeps identifiers that merely contain a type token") {
      // halfDim / floatArray-style names must not be mangled by the word-boundary rewrite.
      glslChromatic shouldContain "halfDim"
    }

    test("wires the content sampler with a pixel->UV helper") {
      glslDuotone shouldContain "uniform sampler2D content;"
      glslDuotone shouldContain "vec4 sampleContent(vec2 px)"
      glslDuotone shouldNotContain "content.eval("
      // The Y-flip lives in the entry's fragCoord (top-left frame), not the sampler — verified against
      // the ColorMatrix reference by GlProgramMatchTest (device). A helper V-flip here would double it.
    }

    test("rewrites the entry point to void main with a flipped fragCoord and out color") {
      glslChromatic shouldContain "out vec4 _fragColor;"
      glslChromatic shouldContain "void main() {"
      // Y-flip of the fragment coord into the kernel's top-left frame.
      glslChromatic shouldContain "uResolution.y - gl_FragCoord.y"
      // returns became assignments to the out color.
      glslChromatic shouldContain "_fragColor ="
    }

    test("layout(color) uniforms become plain vec4 (sink writes sRGB float4 for GLES)") {
      // Duotone declares shadow / highlight as layout(color) uniforms.
      glslDuotone shouldContain "uniform vec4 shadow;"
      glslDuotone shouldContain "uniform vec4 highlight;"
      glslDuotone shouldNotContain "layout(color)"
    }
  })
