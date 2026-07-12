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
@file:OptIn(ExperimentalMirage::class, ExperimentalAtomicApi::class)

package com.skydoves.cloudy.internal.edsl

import com.skydoves.cloudy.ExperimentalMirage
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KProperty

/**
 * Accumulates the statements a kernel body records as it runs — early-return [guard]s, [If] blocks,
 * and mutable-[local] reassignments — up to the final returned value. One flat [statements] list backs
 * the whole trace, including nested [If] bodies: [ifBlock] remembers the list length before running the
 * block and re-wraps whatever the block appended (in place, as one [IfBlock]) rather than routing to a
 * per-scope list. That splice is what lets a [local]'s reassignment (which always targets *this* list)
 * land correctly inside a nested `If` — a per-block child list would make the reassignment blind to
 * which block is currently open.
 */
internal class TraceContext {
  val statements: MutableList<Statement> = mutableListOf()

  /**
   * User-defined helper functions the body called (Foil's `foilHash`), de-duplicated by name so a
   * helper referenced more than once is still declared once. Spliced ahead of the kernel by the emitter.
   */
  val helpers: MutableList<HelperFunction> = mutableListOf()
  private var nextVarId = 0

  fun freshName(): String = "_v${nextVarId++}"

  fun addHelper(helper: HelperFunction) {
    if (helpers.none { it.name == helper.name }) helpers += helper
  }

  /**
   * Runs [block], then folds every statement it appended into one [IfBlock] guarded by [condition] —
   * see the class KDoc for why this in-place splice, not a child scope, is what keeps a nested
   * reassignment landing inside the emitted `if`.
   */
  fun ifBlock(condition: Expression, block: () -> Unit) {
    val start = statements.size
    block()
    val body = statements.subList(start, statements.size).toList()
    repeat(statements.size - start) { statements.removeAt(statements.size - 1) }
    statements += IfBlock(condition, body)
  }
}

/**
 * The one live trace, or `null` outside a shader body. A single global slot, not a parameter threaded
 * through every intrinsic, so a plain `val g = luma(src.rgb)` reads as ordinary Kotlin — the intrinsics
 * find the trace here. A trace runs exactly once per shader construction (then the source is cached
 * forever), so contention is a non-goal; the CAS only turns an accidental nested/concurrent trace into
 * a loud failure instead of silent statement interleaving. Mirrors MirageProgramCache.kt's stdlib
 * [AtomicReference] use (KMP `commonMain` has no `ThreadLocal`).
 */
private val currentTrace = AtomicReference<TraceContext?>(null)

/**
 * Installs a fresh [TraceContext], runs [body] with [params] as receiver so the body reads its uniform
 * handles bare, and returns the body's result paired with the recorded trace. The [check] fails loudly
 * if a trace is already open (a nested or concurrent shader construction), which would otherwise
 * interleave two bodies' statements into one list.
 */
internal fun <P, R> trace(params: P, body: P.() -> R): Pair<R, TraceContext> {
  val ctx = TraceContext()
  check(currentTrace.compareAndSet(null, ctx)) { "nested/concurrent mirage trace" }
  try {
    return params.body() to ctx
  } finally {
    currentTrace.store(null)
  }
}

/** The open trace, or a loud failure when an intrinsic that records a statement runs outside a body. */
internal fun activeTrace(): TraceContext =
  currentTrace.load() ?: error("mirage intrinsic used outside a shader body")

/**
 * `mirageTime` — the standard, name-gated animation clock (MirageCompiler.kt's STD_TIME). A top-level
 * value, not a trace member: it only mints a [StandardUniform] node (referencing it is what makes the
 * compiler emit the declaration), so it needs no open trace.
 */
@ExperimentalMirage
public val mirageTime: Float1 get() = Float1(StandardUniform("mirageTime", ShaderType.Float1))

/** `if (<condition>) return <value>;` — Foil's lens-bounds early-out is this shape exactly. */
@ExperimentalMirage
public fun guard(condition: UBool, value: () -> Half4) {
  activeTrace().statements += EarlyReturn(condition.e, value().e)
}

/** `if (<condition>) { <block> }` — Specular's highlight block; see [TraceContext.ifBlock]. */
@ExperimentalMirage
public fun If(condition: UBool, block: () -> Unit) {
  activeTrace().ifBlock(condition.e, block)
}

/**
 * `var pixel by local(initial)` — a mutable local the body reassigns with `pixel = ...` (Specular's
 * `pixel`, updated from a `content.eval` fallback and again inside the highlight `If`). The delegate's
 * getter always reads the same source variable — a set never renames it — so a read before a later set
 * and one after both refer to the same emitted local, exactly like a GLSL `pixel.rgb = ...` in place.
 */
@ExperimentalMirage
public fun local(initial: Half4): LocalHalf4 {
  val name = activeTrace().freshName()
  activeTrace().statements += Assign(name, initial.e)
  return LocalHalf4(name)
}

/** The delegate backing `var x by local(...)`; see [local]. */
@ExperimentalMirage
public class LocalHalf4 internal constructor(private val name: String) {
  public operator fun getValue(thisRef: Any?, property: KProperty<*>): Half4 =
    Half4(VarRef(name, ShaderType.Half4))

  public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Half4) {
    activeTrace().statements += Reassign(name, value.e)
  }
}

/** `sampleContent(coord)` — the `content.eval(coord)` sample point, Composite-only. */
@ExperimentalMirage
public fun sampleContent(coord: Float2): Half4 = Half4(SampleContent(coord.e))
