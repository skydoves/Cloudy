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
import com.skydoves.cloudy.FilterShader
import com.skydoves.cloudy.GeneratorShader
import com.skydoves.cloudy.MirageParams
import com.skydoves.cloudy.MirageShader

/**
 * Thrown when [MirageCompiler.lint] finds a kernel token that cannot be compiled or contradicts the
 * kernel's [ShaderCategory]. Carries the offending token and the reason so the failure points at the
 * exact source problem.
 */
internal class MirageLintException(message: String) : IllegalArgumentException(message)

/**
 * Lowers an authored [MirageShader] into a per-dialect [CompiledProgram]. Pure and side-effect-free: it
 * touches no GPU, Compose-UI, or Node type, only strings and the schema captured from a probe params
 * instance. That purity is the point of this layer - every codegen decision is unit-testable off any
 * device.
 *
 * The emitted source is assembled from four parts, gated by what the kernel actually references so an
 * unused input never reaches the shader:
 *   1. preamble (lens helpers) - Composite / Generate only; Colorize is point-wise and needs none;
 *   2. standard uniforms - each of `mirageResolution` / `mirageTime` / `mirageDensity` only if the
 *      kernel text names it;
 *   3. schema uniforms - one declaration per [UniformSchema] entry, in declaration order;
 *   4. content sampler + kernel body - Colorize wraps `kernel(...)` in a content-sampling `main`;
 *      Composite / Generate splice the author's `main` directly.
 *
 * A raw shader ([FilterShader.skipLint]) bypasses all four: its source is authored complete, so it is
 * emitted verbatim.
 */
internal object MirageCompiler {

  /**
   * Builds the [UniformSchema] for an shader by minting one probe params instance from [paramsFactory]
   * and reading the slots its `by uniform(...)` delegates registered (declaration order = bind order).
   * The probe is discarded; the engine mints its own per-node instance for actual draws.
   */
  fun schemaOf(paramsFactory: () -> MirageParams): UniformSchema =
    UniformSchema(paramsFactory().schemaEntries)

  /** Lowers [shader] into a ready-to-run program for [dialect]. */
  fun compile(shader: MirageShader<*>, dialect: Dialect): CompiledProgram {
    val schema = schemaOf(shader.paramsFactory)
    val category = categoryOf(shader)
    val kernel = kernelOf(shader, dialect)

    // Every reference/token scan runs against comment-stripped code, never the raw text: a token that
    // only appears in a comment (e.g. a "// ...fwidth..." note, or a "#" in prose) must not trip lint
    // or force a spurious standard-uniform declaration. The emitted source keeps the original kernel
    // (comments intact) - only the analysis is comment-blind.
    val code = stripComments(kernel)

    // Standard-uniform references are static scans over comment-stripped code in every path (raw
    // included). Each gates two things in lockstep: whether the uniform is declared in the emitted
    // source, and whether the node binds it. Android's RuntimeShader rejects a write to an undeclared
    // uniform, so these must agree exactly. (usesTime additionally drives redraw scheduling.)
    val usesResolution = code.contains(STD_RESOLUTION)
    val usesTime = code.contains(STD_TIME)
    val usesDensity = code.contains(STD_DENSITY)

    // A raw shader owns its full source (uniform declarations included) and is treated as Composite,
    // so it always samples content. Emit it verbatim: no preamble, no generated declarations, no wrap.
    if (isRaw(shader)) {
      return CompiledProgram(
        source = kernel,
        schema = schema,
        usesContent = true,
        usesResolution = usesResolution,
        usesTime = usesTime,
        usesDensity = usesDensity,
        category = ShaderCategory.Composite,
        isRaw = true,
      )
    }

    lintCode(code, category)

    val usesContent = category != ShaderCategory.Generate
    val source = assemble(
      kernel,
      schema,
      category,
      dialect,
      usesResolution,
      usesTime,
      usesDensity,
      usesContent,
    )

    return CompiledProgram(
      source = source,
      schema = schema,
      usesContent = usesContent,
      usesResolution = usesResolution,
      usesTime = usesTime,
      usesDensity = usesDensity,
      category = category,
    )
  }

  /**
   * Rejects kernel tokens that fail to compile as a runtime shader or contradict [category].
   *
   * Compile-breakers (any category): derivative functions (AGSL fixes GLSL ES 1.0, which has none),
   * any preprocessor directive, and the raw fragment-coord builtin (the runtime provides coords via
   * `main`'s argument). Category contradictions: a Colorize kernel must reach content only through
   * its `src` argument, and a Generate overlay must not reach content at all - a `content` token in
   * either means the author actually wanted a Composite.
   *
   * Comments are stripped before the scan so a forbidden token that only appears in a comment (a
   * "no fwidth here" note, a "#" in prose) is not a false positive.
   */
  fun lint(kernelSource: String, category: ShaderCategory): Unit =
    lintCode(stripComments(kernelSource), category)

  private fun lintCode(code: String, category: ShaderCategory) {
    for (token in FORBIDDEN_TOKENS) {
      if (code.contains(token)) {
        throw MirageLintException(
          "mirage kernel uses forbidden token '$token': it does not compile as a runtime shader " +
            "(no derivatives / preprocessor / raw frag-coord are available).",
        )
      }
    }
    when (category) {
      ShaderCategory.Colorize ->
        if (code.contains(CONTENT_TOKEN)) {
          throw MirageLintException(
            "Colorize kernel references '$CONTENT_TOKEN': a point-wise kernel must read content " +
              "only through its `src` argument. Use composite() to sample content.",
          )
        }

      ShaderCategory.Generate ->
        if (code.contains(CONTENT_TOKEN)) {
          throw MirageLintException(
            "Generate overlay kernel references '$CONTENT_TOKEN': an overlay has no content " +
              "sampler. Use composite() to sample content.",
          )
        }

      ShaderCategory.Composite -> Unit // free content access is exactly what Composite is for.
    }
  }

  /**
   * Strips `//` line comments and `/* */` block comments from shader source so the analysis scans
   * only live code. A minimal single-pass state machine - shader source has no string literals, so
   * there is no in-string false comment to guard against.
   */
  private fun stripComments(source: String): String = buildString(source.length) {
    var i = 0
    while (i < source.length) {
      val c = source[i]
      val next = if (i + 1 < source.length) source[i + 1] else '\u0000'
      when {
        c == '/' && next == '/' -> {
          i += 2
          while (i < source.length && source[i] != '\n') i++
        }

        c == '/' && next == '*' -> {
          i += 2
          while (i + 1 < source.length && !(source[i] == '*' && source[i + 1] == '/')) i++
          i += 2 // skip the closing */
        }

        else -> {
          append(c)
          i++
        }
      }
    }
  }

  private fun assemble(
    kernel: String,
    schema: UniformSchema,
    category: ShaderCategory,
    dialect: Dialect,
    usesResolution: Boolean,
    usesTime: Boolean,
    usesDensity: Boolean,
    usesContent: Boolean,
  ): String = buildString {
    // Colorize is point-wise and does not read the lens field, so it gets no preamble.
    if (category != ShaderCategory.Colorize) {
      append(miragePreambleHelpers(dialect))
      append('\n')
    }

    // Standard uniforms are referenced-only, and the same flags gate the node's binds, so declaration
    // and binding stay in lockstep (Android rejects a write to an undeclared uniform).
    if (usesResolution) appendLine("uniform float2 $STD_RESOLUTION;")
    if (usesTime) appendLine("uniform float $STD_TIME;")
    if (usesDensity) appendLine("uniform float $STD_DENSITY;")

    // Schema uniforms, one per entry, in declaration (= bind) order.
    for (entry in schema.entries) {
      appendLine(declarationOf(entry))
    }

    if (usesContent) appendLine("uniform shader content;")

    append('\n')

    // Colorize authors only `kernel(p, src)`; codegen adds the content-sampling main. Composite /
    // Generate author the full `main`, spliced as-is.
    append(kernel)
    if (category == ShaderCategory.Colorize) {
      append('\n')
      append(COLORIZE_MAIN_WRAPPER)
      append('\n')
    }
  }

  /**
   * Maps a schema entry to its shader declaration. isColor takes precedence (a color is a float4 with
   * a layout qualifier); isTexture is a child sampler; otherwise the glslType is already the token.
   */
  private fun declarationOf(entry: UniformEntry): String = when {
    entry.isColor -> "layout(color) uniform float4 ${entry.name};"
    entry.isTexture -> "uniform shader ${entry.name};"
    else -> "uniform ${entry.glslType} ${entry.name};"
  }

  // MirageShader is sealed to FilterShader / GeneratorShader, so these accessor branches are exhaustive.

  private fun categoryOf(shader: MirageShader<*>): ShaderCategory = when (shader) {
    is FilterShader<*> -> shader.category
    is GeneratorShader<*> -> ShaderCategory.Generate
  }

  // GlslEs consumes the AGSL body (its GLSL ES feature surface is the base the translator lowers from);
  // Sksl uses the SKSL body; Agsl uses the AGSL body.
  private fun kernelOf(shader: MirageShader<*>, dialect: Dialect): String = when (shader) {
    is FilterShader<*> -> if (dialect == Dialect.Sksl) shader.sksl else shader.agsl
    is GeneratorShader<*> -> if (dialect == Dialect.Sksl) shader.sksl else shader.agsl
  }

  private fun isRaw(shader: MirageShader<*>): Boolean = shader is FilterShader<*> && shader.skipLint

  /** Standard uniform names the compiler emits on demand. The kernel references these directly. */
  private const val STD_RESOLUTION = "mirageResolution"
  private const val STD_TIME = "mirageTime"
  private const val STD_DENSITY = "mirageDensity"

  /** The content child sampler name (both the generated declaration and the wrapper reference it). */
  private const val CONTENT_TOKEN = "content"

  /** Colorize wrapper: sample content once at the fragment coord and hand the pixel to the kernel. */
  private const val COLORIZE_MAIN_WRAPPER =
    "half4 main(float2 xy) { return kernel(xy, content.eval(xy)); }"

  /**
   * Tokens that never compile in a runtime shader (any category). fwidth/dFdx/dFdy are derivative
   * functions AGSL lacks; '#version' / '#' are preprocessor directives the runtime rejects;
   * sk_FragCoord is the raw builtin the wrapper's `xy` argument replaces.
   */
  private val FORBIDDEN_TOKENS = listOf(
    "fwidth",
    "dFdx",
    "dFdy",
    "#version",
    "#",
    "sk_FragCoord",
  )
}
