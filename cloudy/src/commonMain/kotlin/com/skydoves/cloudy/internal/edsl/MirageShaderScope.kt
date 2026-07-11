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
 * The trace-time receiver for a Composite/Generate body lambda. Runs once, at shader-construction time
 * (cached forever by `MirageProgramCache`, keyed on the emitted source — see mirage-edsl-design.md
 * §2), and accumulates statements in call order: [let] records a named local, [guard] records an
 * early-return, [ifBlock] records a conditional block, and a [MutableLocal] from [letMutable] records
 * its updates via [MutableLocal.set]. The final `return`ed [Half4] closes the trace via [build].
 *
 * One flat [statements] list backs the whole trace, including nested [ifBlock] bodies: [ifBlock]
 * remembers the list's length before running [block] and re-wraps whatever [block] appended (in place,
 * as one [IfBlock]) rather than routing to a per-scope list. This is what lets a [MutableLocal] (whose
 * [MutableLocal.set] always targets *this* list) correctly land its reassignment inside a nested
 * `ifBlock` — a per-block child list would make `set` blind to which block is currently open.
 */
internal class MirageShaderScope {
  private val statements = mutableListOf<Statement>()
  private var nextVarId = 0

  /** `mirageTime` — the standard, name-gated animation clock (see MirageCompiler.kt's STD_TIME). */
  val mirageTime: Float1 get() = Float1(StandardUniform("mirageTime", ShaderType.Float1))

  /**
   * Binds [value] to a fresh local and returns a reference to it. Emitting a real local (rather than
   * inlining every use) keeps the traced output legible and matches how the hand-written kernels name
   * their intermediates (`along`, `glare`, `rainbow`, ...) — [readableName] is cosmetic only, not an
   * identifier the compiler or emitter depends on.
   */
  fun let(readableName: String, value: Float1): Float1 = bind(readableName, value.e, ShaderType.Float1) { Float1(it) }
  fun let(readableName: String, value: Float2): Float2 = bind(readableName, value.e, ShaderType.Float2) { Float2(it) }
  fun let(readableName: String, value: Float3): Float3 = bind(readableName, value.e, ShaderType.Float3) { Float3(it) }
  fun let(readableName: String, value: Half1): Half1 =
    bind(readableName, value.e, ShaderType.Half1) { Half1(it) }
  fun let(readableName: String, value: Half3): Half3 = bind(readableName, value.e, ShaderType.Half3) { Half3(it) }
  fun let(readableName: String, value: Half4): Half4 = bind(readableName, value.e, ShaderType.Half4) { Half4(it) }

  private inline fun <T> bind(readableName: String, value: Expression, type: ShaderType, wrap: (Expression) -> T): T {
    val name = freshName(readableName)
    statements += Assign(name, value)
    return wrap(VarRef(name, type))
  }

  private fun freshName(readableName: String): String {
    val name = "${readableName}_$nextVarId"
    nextVarId++
    return name
  }

  /**
   * Binds [initial] to a fresh local the caller can later update with [MutableLocal.set] (Specular's
   * `pixel`, updated once from a `content.eval` fallback and again from the highlight block — possibly
   * from inside an [ifBlock]).
   */
  fun letMutable(readableName: String, initial: Half4): MutableLocal {
    val name = freshName(readableName)
    statements += Assign(name, initial.e)
    return MutableLocal(name, statements)
  }

  /** `if (<condition>) return <value>;` — Foil's lens-bounds early-out is this shape exactly. */
  fun guard(condition: Expression, value: Half4) {
    statements += EarlyReturn(condition, value.e)
  }

  /**
   * `if (<condition>) { <block> }` — Specular's highlight block. Everything [block] appends to this
   * trace (`let`s, a `MutableLocal.set`, a nested `ifBlock`) is spliced back out and wrapped as one
   * [IfBlock] — see the class KDoc for why this splice, not a child scope, is what keeps a
   * [MutableLocal.set] inside [block] landing inside the emitted `if`.
   */
  fun ifBlock(condition: Expression, block: MirageShaderScope.() -> Unit) {
    val start = statements.size
    block()
    val body = statements.subList(start, statements.size).toList()
    repeat(statements.size - start) { statements.removeAt(statements.size - 1) }
    statements += IfBlock(condition, body)
  }

  fun build(returnExpr: Half4, helpers: List<HelperFunction> = emptyList()): ShaderModule =
    ShaderModule(statements.toList(), returnExpr.e, helpers)
}

/**
 * A mutable local introduced by [MirageShaderScope.letMutable]. [get] always reads the same source
 * variable name — [set] never renames it — so a read taken before a later [set] and one taken after
 * both refer to the same emitted local, exactly like a GLSL `pixel.rgb = ...` in place.
 */
internal class MutableLocal(private val name: String, private val statements: MutableList<Statement>) {
  fun get(): Half4 = Half4(VarRef(name, ShaderType.Half4))

  fun set(value: Half4) {
    statements += Reassign(name, value.e)
  }
}
