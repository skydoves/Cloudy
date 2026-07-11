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
package com.skydoves.cloudy.internal

/**
 * Translates an assembled AGSL mirage program into a `#version 300 es` GLSL ES fragment shader for the
 * API 29-32 GLES backend. AGSL is a restricted skewer of SkSL that is *very* close to GLSL ES 3.0
 * already; the divergences are mechanical:
 *
 * - `half`/`halfN` are not GLSL types -> `float`/`vecN` (AGSL runs everything at fp16 for the shader's
 *   convenience; GLES gets highp float. Precision divergence vs 33+ fp16 is spike #3, measured later).
 * - `floatN` -> `vecN`, `float2/3/4` etc. (AGSL spells vectors `floatN`; GLSL spells them `vecN`).
 * - `uniform shader content;` -> `uniform sampler2D content;` plus a `sampleContent()` helper, because
 *   AGSL's `content.eval(px)` samples in *pixel* space while a GLSL `texture()` samples in 0..1 UV.
 * - `layout(color) uniform float4 x;` -> `uniform vec4 x;` (GLES has no color-layout; the uniform sink
 *   already writes sRGB float4 for GLES, matching skiko).
 * - the AGSL entry `half4 main(float2 xy) { ... return C; }` -> GLSL `void main() { ... _fragColor = C; }`
 *   with a `fragCoord` local and an `out vec4 _fragColor;`.
 *
 * ## The one load-bearing trap: Y-flip
 * `gl_FragCoord`'s origin is bottom-left; every mirage kernel assumes a top-left origin (Compose /
 * Skia / AGSL convention). So `fragCoord.y` is flipped to `uResolution.y - gl_FragCoord.y`, and the
 * content sampler's V is derived from that same flipped fragCoord, keeping content and geometry in one
 * consistent top-left frame. Get this wrong and the whole effect renders upside down.
 *
 * A raw optic ([FilterOptic.skipLint]) owns its full source and cannot be mechanically translated, so
 * it is declined upstream (its GlslEs program is never built).
 */
internal object MirageGlslEs {

  private const val HEADER = "#version 300 es\nprecision highp float;\n"

  /** The content sampler + pixel->UV helper. `uResolution` is the standard mirage resolution uniform. */
  private const val CONTENT_HELPER =
    "uniform sampler2D content;\n" +
      "uniform vec2 uResolution;\n" +
      // AGSL content.eval(px) takes pixel coords in the kernel's top-left frame; convert to 0..1 UV.
      // `px` is already the Y-flipped fragCoord (top-left), and GLUtils.texImage2D uploads the bitmap
      // so texture V grows top->bottom, so a direct uv (no extra V flip) keeps content aligned with the
      // kernel frame and the readback. (Verified against the ColorMatrix reference by GlProgramMatchTest.)
      "vec4 sampleContent(vec2 px) {\n" +
      "  vec2 uv = px / uResolution;\n" +
      "  return texture(content, uv);\n" +
      "}\n"

  /**
   * Translates [agslSource] (the compiler's assembled AGSL for a non-raw content-filtering optic) to a
   * GLSL ES 3.0 fragment shader. Only Colorize / Composite reach here — a Generate overlay is declined
   * for the GLES band (it has no content sampler and composites via a ShaderBrush, not the FBO path) —
   * so the content sampler is always emitted.
   */
  fun translate(agslSource: String): String {
    var s = agslSource

    // 1. Drop AGSL's own uniform declarations that GLES expresses differently; we re-emit them.
    //    `uniform shader content;` -> handled by CONTENT_HELPER; strip the AGSL line.
    s = s.replace("uniform shader content;", "")
    //    `layout(color) uniform float4 NAME;` -> `uniform vec4 NAME;`
    s = LAYOUT_COLOR_RE.replace(s) { "uniform vec4 ${it.groupValues[1]};" }
    //    The standard resolution uniform is provided by CONTENT_HELPER's `uResolution`; the kernel
    //    names it `mirageResolution`, so alias rather than double-declare.
    s = s.replace("uniform float2 mirageResolution;", "")

    // 2. content.eval(EXPR) -> sampleContent(EXPR). Do this before the type-token pass so the rename is
    //    on the AGSL spelling.
    s = s.replace("content.eval(", "sampleContent(")

    // 3. Mechanical type-token rewrites: half*/float2..4 -> float/vec*. Word-boundary matched so a
    //    substring like `halfDim` (an identifier) is never touched.
    s = rewriteTypeTokens(s)

    // 4. Entry point: `vec4 main(vec2 xy) { BODY }` (after step 3 half4->vec4, float2->vec2) becomes
    //    `void main(){ vec2 xy = <flipped fragCoord>; BODY-with-returns-as-_fragColor }`.
    s = rewriteEntryPoint(s)

    // 5. Alias mirageResolution to the helper's uResolution (kernels read `mirageResolution`).
    val resolutionAlias = if (s.contains("mirageResolution")) "#define mirageResolution uResolution\n" else ""

    return buildString {
      append(HEADER)
      append(CONTENT_HELPER)
      append(resolutionAlias)
      append("out vec4 _fragColor;\n")
      append(s)
    }
  }

  // half4->vec4, half3->vec3, half2->vec2, half->float ; float2->vec2, float3->vec3, float4->vec4.
  // Ordered longest-first within each family so `half4` is not first split by a `half` rule. Each is a
  // word-boundary regex so identifiers containing the token (halfDim, floatArray) are untouched.
  private fun rewriteTypeTokens(src: String): String {
    var s = src
    for ((from, to) in TYPE_TOKENS) {
      s = Regex("\\b$from\\b").replace(s, to)
    }
    return s
  }

  private val TYPE_TOKENS = listOf(
    "half4" to "vec4",
    "half3" to "vec3",
    "half2" to "vec2",
    "half" to "float",
    "float4" to "vec4",
    "float3" to "vec3",
    "float2" to "vec2",
  )

  private val LAYOUT_COLOR_RE = Regex("""layout\(color\)\s+uniform\s+float4\s+(\w+)\s*;""")

  // Matches `vec4 main(vec2 IDENT) {` at the point step 3 has already turned half4->vec4 / float2->vec2.
  private val ENTRY_RE = Regex("""vec4\s+main\s*\(\s*vec2\s+(\w+)\s*\)\s*\{""")

  /**
   * Rewrites the AGSL entry point into a GLSL `void main()`. AGSL's `main(float2 xy)` receives the
   * fragment coord as an argument and *returns* the color; GLSL's `main` takes no args and writes
   * `gl_FragColor` (here `_fragColor`). So:
   *   - bind `xy` to the Y-flipped `gl_FragCoord` (top-left origin, matching kernel geometry),
   *   - turn the function body's `return EXPR;` into `_fragColor = EXPR; return;`.
   *
   * Only the entry function's returns are rewritten — helper functions above it keep their `return`s.
   * The entry is the last function in the assembled source (the wrapper / author main is appended
   * last), so everything from the entry brace onward is its body.
   */
  private fun rewriteEntryPoint(src: String): String {
    val match = ENTRY_RE.find(src) ?: return src // no entry (should not happen for a compiled program)
    val argName = match.groupValues[1]
    val bodyStart = match.range.last + 1 // just after the '{'

    val head = src.substring(0, match.range.first)
    val body = src.substring(bodyStart)

    val newEntry = buildString {
      append("void main() {\n")
      // Y-flip: gl_FragCoord is bottom-left; kernels assume top-left.
      append("  vec2 $argName = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);\n")
      append(rewriteReturns(body))
    }
    return head + newEntry
  }

  /**
   * Replaces every `return EXPR;` in the entry body with `{ _fragColor = EXPR; return; }`. A tiny
   * scanner that finds `return`, captures up to the matching `;` (respecting nested parens/braces so a
   * `return foo(a; ...)`-like case—which shader syntax never produces—still would not misfire), and
   * rewrites it. Comments were already stripped from the analysis copy upstream, but the emitted source
   * keeps comments; a `return` inside a comment is not expected in these kernels and the kernels here
   * have none, so a scan over live text is sufficient (spike-scoped, not a general C parser).
   */
  private fun rewriteReturns(body: String): String = buildString {
    var i = 0
    while (i < body.length) {
      val idx = body.indexOf("return", i)
      if (idx < 0) {
        append(body.substring(i))
        break
      }
      // Ensure it's the keyword, not a substring like `returned` (none here, but be safe).
      val before = if (idx > 0) body[idx - 1] else ' '
      val afterIdx = idx + "return".length
      val after = if (afterIdx < body.length) body[afterIdx] else ' '
      val isKeyword = !before.isLetterOrDigit() && before != '_' &&
        !after.isLetterOrDigit() && after != '_'
      if (!isKeyword) {
        append(body.substring(i, afterIdx))
        i = afterIdx
        continue
      }
      append(body.substring(i, idx))
      // Find the terminating ';' for this return statement.
      val semi = body.indexOf(';', afterIdx)
      if (semi < 0) {
        append(body.substring(idx))
        break
      }
      val expr = body.substring(afterIdx, semi).trim()
      append("{ _fragColor = $expr; return; }")
      i = semi + 1
    }
  }
}
