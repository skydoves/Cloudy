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
package com.skydoves.cloudy.internal.edsl

/**
 * A shader value's dialect-neutral type. Carries precision as an attribute (not a separate node kind)
 * so `RuntimeEffectEmitter` prints `half`/`float` while a future GLSL ES 3.0 emitter prints
 * `mediump`/`highp` from the same [Expression] tree.
 */
internal enum class ShaderType(val components: Int, val isHalf: Boolean) {
  Float1(1, false),
  Float2(2, false),
  Float3(3, false),
  Float4(4, false),
  Half1(1, true),
  Half3(3, true),
  Half4(4, true),
  Bool(1, false),
}

/**
 * A node in the traced expression tree for a Colorize/Generate/Composite kernel body. Plain classes,
 * not value classes: a polymorphic AST boxes on every use as the [Expression] supertype regardless, so
 * a value-class wrapper buys nothing here (see mirage-edsl-design.md §5.1).
 */
internal sealed interface Expression {
  val type: ShaderType
}

/** A literal scalar constant, e.g. `0.5`, `SMOOTH_EDGE_PX`'s value inlined at each use. */
internal data class Literal(val value: Float, override val type: ShaderType = ShaderType.Float1) : Expression

/** A reference to a [com.skydoves.cloudy.MirageParams] uniform handle, by declaration slot. */
internal data class UniformRef(val slot: Int, override val type: ShaderType) : Expression

/**
 * A reference to a standard, name-gated uniform the compiler declares on demand (`mirageTime` /
 * `mirageResolution` / `mirageDensity`) — see MirageCompiler.kt's STD_* constants. Not a [UniformRef]
 * because it has no [com.skydoves.cloudy.MirageParams] slot; referencing this node is itself what
 * makes the compiler emit the declaration, exactly as it already does for the string kernels' textual
 * reference scan.
 */
internal data class StandardUniform(val name: String, override val type: ShaderType) : Expression

/** A kernel argument: `xy` (Composite/Generate) or `src` (Colorize). */
internal data class Argument(val name: String, override val type: ShaderType) : Expression

/** A binary operator node (`+ - * /`), GLSL scalar-broadcast semantics. */
internal data class Binary(
  val operator: String,
  val left: Expression,
  val right: Expression,
  override val type: ShaderType,
) : Expression

/** A unary operator node (`-x`). */
internal data class Unary(val operator: String, val operand: Expression, override val type: ShaderType) : Expression

/** A comparison node (`> < >= <= == !=`), always [ShaderType.Bool]. */
internal data class Comparison(val operator: String, val left: Expression, val right: Expression) : Expression {
  override val type: ShaderType get() = ShaderType.Bool
}

/** An intrinsic, type-constructor, or user-defined-function call, e.g. `mix(a, b, t)`, `half4(r, g, b, a)`. */
internal data class Call(val functionName: String, val args: List<Expression>, override val type: ShaderType) : Expression

/** A swizzle read, e.g. `.rgb`, `.a`, `.x`. */
internal data class Swizzle(val base: Expression, val components: String, override val type: ShaderType) : Expression

/**
 * A ternary select, e.g. `p.x >= 0.0 ? 1.0 : -1.0`. Kotlin has no ternary operator to overload, so the
 * eDSL surface is the [select] function; this node is what it builds.
 */
internal data class Select(val condition: Expression, val ifTrue: Expression, val ifFalse: Expression, override val type: ShaderType) : Expression

/**
 * A `content.eval(coord)` sample. A dedicated node rather than a [Call] because it is the one construct
 * whose spelling actually differs across dialects — AGSL/SkSL both write `.eval()`, but a future GLSL
 * ES 3.0 emitter would lower this to a `sampleContent()` helper over a `sampler2D`/`texture()` (see
 * mirage-edsl-design.md §7.2). AGSL/SkSL emit the same text either way, so [RuntimeEffectEmitter] does
 * not yet need to branch on it — the node exists so a GLSL emitter can, without touching callers.
 */
internal data class SampleContent(val coord: Expression) : Expression {
  override val type: ShaderType get() = ShaderType.Half4
}

/** A reference to a local variable bound by a preceding [Assign]/[Reassign] statement. */
internal data class VarRef(val name: String, override val type: ShaderType) : Expression

/** A statement in a traced kernel body: a binding, a reassignment, an early exit, or a guarded block. */
internal sealed interface Statement

/** `val <name> = <value>` — a named intermediate, emitted as a local declaration before it is read. */
internal data class Assign(val name: String, val value: Expression) : Statement

/**
 * `<name> = <value>;` — updates a var previously introduced by [Assign] (e.g. Specular's mutable
 * `pixel`, which the `content.eval` fallback and the highlight block both write). The [VarRef] used to
 * build [value] and the reassigned [name] refer to the same local; the emitter does not re-declare it.
 */
internal data class Reassign(val name: String, val value: Expression) : Statement

/**
 * `if (<condition>) return <value>;` — the one control-flow shape Foil's early-out needs. Not a general
 * if/else (see mirage-edsl-design.md §10, "not now": general control flow is P3+ if a kernel needs it).
 */
internal data class EarlyReturn(val condition: Expression, val value: Expression) : Statement

/**
 * `if (<condition>) { <body> }` with no `return` — Specular's highlight block, which conditionally
 * computes and folds into `pixel` via a [Reassign] inside [body]. Distinct from [EarlyReturn]: this one
 * doesn't exit the kernel, its statements just don't run when the guard is false.
 */
internal data class IfBlock(val condition: Expression, val body: List<Statement>) : Statement

/**
 * A user-defined helper function traced alongside the kernel body (e.g. Foil's `foilHash`), spliced
 * into the emitted source ahead of the kernel/main it's used from. Single-expression body only — the
 * one shape every current kernel's helper functions need.
 */
internal class HelperFunction(val name: String, val paramName: String, val paramType: ShaderType, val body: Expression)

/**
 * The completed trace of one kernel: the ordered [statements] leading up to [returnExpr], plus any
 * [helpers] the body called. A Colorize trace typically has no statements (MVP is expression-only); a
 * Composite/Generate trace with an early-return has one [EarlyReturn] statement before the final value.
 */
internal class ShaderModule(
  val statements: List<Statement>,
  val returnExpr: Expression,
  val helpers: List<HelperFunction> = emptyList(),
)
