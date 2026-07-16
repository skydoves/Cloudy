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

package com.skydoves.cloudy.edsl

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

  fun hasHelper(name: String): Boolean = helpers.any { it.name == name }

  /**
   * Traces a multi-statement helper body once, returning the statements it recorded and its returned
   * expression. Runs [block] against *this* trace (so intrinsics inside the helper — including calls to
   * *other* helpers — find the open trace and register on it), then splices out the statements [block]
   * appended so they land in the helper's own [HelperFunction.body], not the main kernel body. Mirrors
   * [ifBlock]'s splice, but yields the captured statements to the caller instead of re-wrapping them.
   *
   * A helper calling another helper registers that callee on this trace *before* the caller finishes
   * tracing (its intrinsic runs mid-[block]), so the shared [helpers] list ends up dependency-first —
   * every helper is appended after its callees, which is exactly the GLSL "declared before use" order.
   */
  fun <R> traceHelper(block: () -> R): Pair<List<Statement>, R> {
    val start = statements.size
    val result = block()
    val body = statements.subList(start, statements.size).toList()
    statements.subList(start, statements.size).clear()
    return body to result
  }

  /**
   * Runs [block], then folds every statement it appended into one [IfBlock] guarded by [condition] —
   * see the class KDoc for why this in-place splice, not a child scope, is what keeps a nested
   * reassignment landing inside the emitted `if`.
   */
  fun ifBlock(condition: Expression, block: () -> Unit) {
    statements += IfBlock(condition, splice(block))
  }

  /**
   * Records a bounded [LoopStatement]: runs [block] **once** at trace time (the surface [loop] passes it
   * a `float(_i)` index expression, not a live counter — the body is emitted, not unrolled) and folds the
   * statements it appended into the loop body, mirroring [ifBlock]'s in-place splice.
   */
  fun loop(count: Expression, maxIterations: Int, indexName: String, block: () -> Unit) {
    statements += LoopStatement(count, maxIterations, indexName, splice(block))
  }

  /** Runs [block], then removes and returns the statements it appended (the [ifBlock]/[loop] splice). */
  private fun splice(block: () -> Unit): List<Statement> {
    val start = statements.size
    block()
    val body = statements.subList(start, statements.size).toList()
    statements.subList(start, statements.size).clear()
    return body
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
  if (!currentTrace.compareAndSet(null, ctx)) {
    throw MirageDiagnosticException(
      MirageDiagnosticCode.NESTED_TRACE,
      "a mirage shader body started while another was still tracing",
      "build one shader at a time; do not construct a MirageShader from inside another's body lambda",
    )
  }
  try {
    return params.body() to ctx
  } finally {
    currentTrace.store(null)
  }
}

/** The open trace, or a loud failure when an intrinsic that records a statement runs outside a body. */
internal fun activeTrace(): TraceContext = currentTrace.load() ?: throw MirageDiagnosticException(
  MirageDiagnosticCode.INTRINSIC_OUTSIDE_BODY,
  "a mirage intrinsic was used outside a shader body",
  "call it only inside a colorize/composite/generate body lambda",
)

/**
 * `mirageTime` — the standard, name-gated animation clock (MirageCompiler.kt's STD_TIME). A top-level
 * value, not a trace member: it only mints a [StandardUniform] node (referencing it is what makes the
 * compiler emit the declaration), so it needs no open trace.
 */
@ExperimentalMirage
public val mirageTime: Float1 get() = Float1(StandardUniform("mirageTime", ShaderType.Float1))

/**
 * `mirageResolution` — the standard, name-gated render resolution (MirageCompiler.kt's STD_RESOLUTION),
 * a `float2` of the target size in px. Like [mirageTime]: a top-level value that only mints a
 * [StandardUniform] node, so referencing it is what makes the compiler emit the `uniform float2`
 * declaration; needs no open trace.
 */
@ExperimentalMirage
public val mirageResolution: Float2 get() =
  Float2(StandardUniform("mirageResolution", ShaderType.Float2))

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

/**
 * The delegate backing `var x by local(...)`; see [local]. The getter returns a plain [Half4] (so a
 * whole-value reassignment `pixel = half4(...)` stays valid — the property type is [Half4], not a
 * subtype); *channel* writes (`pixel.rgb = ...`) go through the [rgb]/[a] write-swizzle setters on
 * [Half4], which resolve the backing var name from the value's own `VarRef` node.
 */
@ExperimentalMirage
public class LocalHalf4 internal constructor(private val name: String) {
  public operator fun getValue(thisRef: Any?, property: KProperty<*>): Half4 =
    Half4(VarRef(name, ShaderType.Half4))

  public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Half4) {
    activeTrace().statements += Reassign(name, value.e)
  }
}

/**
 * `var uv by local(initial)` for a `float2` — the mutable-local form the RainyWindow helpers need for
 * their sequential in-place updates (`uv.y += t`, `x *= 0.7`, ... rewritten as full reassignment). Same
 * shape as [local] over [Half4]; the getter always reads the one source variable, a set never renames.
 */
@ExperimentalMirage
public fun local(initial: Float2): LocalFloat2 {
  val name = activeTrace().freshName()
  activeTrace().statements += Assign(name, initial.e)
  return LocalFloat2(name)
}

/** The delegate backing `var v by local(float2)`; see [local]. */
@ExperimentalMirage
public class LocalFloat2 internal constructor(private val name: String) {
  public operator fun getValue(thisRef: Any?, property: KProperty<*>): Float2 =
    Float2(VarRef(name, ShaderType.Float2))

  public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Float2) {
    activeTrace().statements += Reassign(name, value.e)
  }
}

/** `var x by local(initial)` for a `float` scalar — see [local] over [Float2]. */
@ExperimentalMirage
public fun local(initial: Float1): LocalFloat1 {
  val name = activeTrace().freshName()
  activeTrace().statements += Assign(name, initial.e)
  return LocalFloat1(name)
}

/** The delegate backing `var x by local(float1)`; see [local]. */
@ExperimentalMirage
public class LocalFloat1 internal constructor(private val name: String) {
  public operator fun getValue(thisRef: Any?, property: KProperty<*>): Float1 =
    Float1(VarRef(name, ShaderType.Float1))

  public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Float1) {
    activeTrace().statements += Reassign(name, value.e)
  }
}

/**
 * Registers an N-param, statement-body helper function once (deduped by [name]) and returns the
 * [Expression] a call to it produces. The library-facing counterpart to [foilHash]'s single-expression
 * registration: [traceBody] runs against fresh [Argument] handles the caller builds for [params],
 * declaring mutable [local]s and calling other helpers as needed, and returns the helper's final
 * expression; everything it records under [TraceContext.traceHelper] becomes the helper's own body.
 *
 * The caller passes the [args] the call site supplies and the [returnType]; a `internal fun N13(...)`
 * wrapper in the kernel file wraps the returned node in its value type.
 */
@ExperimentalMirage
internal fun defineHelper(
  name: String,
  params: List<Pair<String, ShaderType>>,
  returnType: ShaderType,
  args: List<Expression>,
  traceBody: () -> Expression,
): Expression {
  val trace = activeTrace()
  if (!trace.hasHelper(name)) {
    val (statements, returnExpr) = trace.traceHelper(traceBody)
    trace.addHelper(HelperFunction(name, params, returnType, statements, returnExpr))
  }
  return Call(name, args, returnType)
}

/** `sampleContent(coord)` — the `content.eval(coord)` sample point, Composite-only. */
@ExperimentalMirage
public fun sampleContent(coord: Float2): Half4 = Half4(SampleContent(coord.e))

/**
 * Static loop unroll: runs [body] [times] times *at trace time* with a Kotlin `Int` index, so each pass
 * records another copy of the body's statements — there is no GLSL `for`, the loop is fully unrolled into
 * inline statements. The index is a compile-time constant, so `tapOffsets[i]` and `xy + float2(i, 0)`
 * work as ordinary Kotlin. IR and emitter are untouched: this only replays the body.
 */
@ExperimentalMirage
public fun unroll(times: Int, body: (Int) -> Unit) {
  activeTrace() // fail loudly outside a body, before the (possibly empty) loop runs nothing
  for (i in 0 until times) body(i)
}

/**
 * ES2-safe dynamic-bound loop: emits a real GLSL `for` whose header bound is the Kotlin `Int`
 * [maxIterations] (constant, so ES2's loop rules hold), with an inner `if (index >= count) break` for the
 * dynamic [count]. [body] runs **once** at trace time against a `float(_i)` index expression — its
 * recorded statements become the loop body (not unrolled). Use for a variable sample count with a fixed
 * ceiling (SDF marching, adaptive blur); use [unroll] when the count is a compile-time constant.
 */
@ExperimentalMirage
public fun loop(count: Float1, maxIterations: Int, body: (index: Float1) -> Unit) {
  val trace = activeTrace()
  val indexName = trace.freshName()
  trace.loop(count.e, maxIterations, indexName) {
    body(Float1(VarRef(indexName, ShaderType.Float1)))
  }
}
