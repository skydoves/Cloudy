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

/**
 * A value-producing branch, the DSL's if/else-*expression* (Kotlin has none to overload, and the
 * statement-form [If] can't return a value). Each arm is a block that may record several statements and
 * ends in a value; both arms must produce the same type. It lowers to a temp declared once and written by
 * every arm, wired through [IfBlock] + [IfBlock.elseBody]:
 *
 * ```glsl
 * <type> _vN;
 * if (cond) { <ifTrue stmts>; _vN = <ifTrue value>; }
 * else      { <ifFalse stmts>; _vN = <ifFalse value>; }
 * ```
 *
 * `branch(...) { ... }` alone is a [BranchBuilder], **not** the value type — you cannot use it as a value
 * until you close it with [Else]. That is what makes the else mandatory: a branch with no else has no
 * defined value on the false path.
 */
@ExperimentalMirage
public fun <T : ShaderValue> branch(condition: UBool, ifTrue: () -> T): BranchBuilder<T> =
  BranchBuilder(condition.e, ifTrue)

/** The half-open [branch]; only [Else] turns it into the arms' value type. */
@ExperimentalMirage
public class BranchBuilder<T : ShaderValue> internal constructor(
  internal val condition: Expression,
  internal val ifTrue: () -> T,
)

/**
 * Closes a [branch] with its false arm and yields the arms' common value type. Traces both arms, declares
 * the temp at the arms' [ShaderType], and emits the `if/else` that writes it on both paths. A type
 * mismatch between the arms fails with [MirageDiagnosticCode.BRANCH_TYPE_MISMATCH].
 */
@Suppress("UNCHECKED_CAST")
@ExperimentalMirage
public infix fun <T : ShaderValue> BranchBuilder<T>.Else(ifFalse: () -> T): T {
  val trace = activeTrace()
  val temp = trace.freshName()

  val (trueStmts, trueValue) = trace.traceHelper(ifTrue)
  val (falseStmts, falseValue) = trace.traceHelper(ifFalse)
  val type = armType(trueValue.e, falseValue.e)

  trace.statements += DeclareLocal(temp, type)
  trace.statements += IfBlock(
    condition,
    trueStmts + Reassign(temp, trueValue.e),
    falseStmts + Reassign(temp, falseValue.e),
  )
  return wrapNode(type, VarRef(temp, type)) as T
}

/** A DSL scope for [When], so a nested `When`'s [WhenScope.then] can't leak into an outer scope. */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@ExperimentalMirage
public annotation class MirageWhenDsl

/**
 * A value-producing multi-way branch — the DSL's when-*expression*. Each `<cond> then { ... }` case is a
 * value-producing block; the first case whose condition is true wins (top to bottom), and a **mandatory**
 * [WhenScope.otherwise] supplies the fallthrough value. It lowers to a first-match chain of nested
 * [IfBlock]s (each next case in the previous [IfBlock.elseBody]), so it emits the same `if / else if /
 * else` shape a hand-written when would:
 *
 * ```glsl
 * <type> _vN;
 * if (c1) { _vN = <case1>; }
 * else { if (c2) { _vN = <case2>; } else { _vN = <otherwise>; } }
 * ```
 *
 * The `otherwise` is required at **runtime** (trace time), not compile time: forgetting it returns a value
 * not produced by [WhenScope.otherwise], detected by an identity check and reported as
 * [MirageDiagnosticCode.WHEN_WITHOUT_OTHERWISE]. Arm type mismatches raise
 * [MirageDiagnosticCode.BRANCH_TYPE_MISMATCH]. There is deliberately no `elseIf` chain (a formatter that
 * wraps its infix `Else` onto a new line breaks the parse) and no implicit-else form (its result type
 * would collapse to `Any`) — the two shapes are [branch]…[Else] (2-way) and this `When` (n-way).
 */
@ExperimentalMirage
public fun <T : ShaderValue> When(block: (@MirageWhenDsl WhenScope<T>).() -> T): T {
  val scope = WhenScope<T>()
  val result = scope.block()
  // The block's final expression must be the object otherwise() returned. A block that forgets otherwise
  // ends in some other value (a stray arm), so identity differs — caught here as a trace diagnostic.
  if (result !== scope.otherwiseSentinel) {
    throw MirageDiagnosticException(
      MirageDiagnosticCode.WHEN_WITHOUT_OTHERWISE,
      "a When block must end in otherwise { ... }",
      "add a final otherwise { ... } arm supplying the fallthrough value",
    )
  }
  return scope.lower()
}

/** The receiver of a [When] block: records `then` cases and closes them with the mandatory `otherwise`. */
@MirageWhenDsl
@ExperimentalMirage
public class WhenScope<T : ShaderValue> internal constructor() {
  private val cases =
    mutableListOf<Pair<Expression, Triple<List<Statement>, Expression, ShaderType>>>()
  private var otherwiseCase: Triple<List<Statement>, Expression, ShaderType>? = null

  /**
   * The object [otherwise] returns and [When] identity-checks the block's result against. Built lazily as
   * the fallthrough arm is traced (its [ShaderType] is only known then), so it is a genuine [T] — no
   * unchecked cast of a wrong-typed sentinel.
   */
  internal var otherwiseSentinel: T? = null
    private set

  /** Records a `<cond> then { value }` case; first true case wins in declaration order. Traced eagerly. */
  public infix fun UBool.then(value: () -> T) {
    cases += e to traceArm(value)
  }

  /** The mandatory fallthrough arm; its return object is the sentinel [When] checks for. */
  @Suppress("UNCHECKED_CAST")
  public fun otherwise(value: () -> T): T {
    val arm = traceArm(value)
    otherwiseCase = arm
    val sentinel = wrapNode(arm.third, Argument("__when_otherwise__", arm.third)) as T
    otherwiseSentinel = sentinel
    return sentinel
  }

  /** Traces one arm's block into (its statements, its value node, its type), the [BranchBuilder.Else] split. */
  private fun traceArm(arm: () -> T): Triple<List<Statement>, Expression, ShaderType> {
    val (stmts, value) = activeTrace().traceHelper(arm)
    return Triple(stmts, value.e, value.e.type)
  }

  /** Lowers the recorded cases + otherwise into the nested-[IfBlock] first-match chain; see [When]. */
  @Suppress("UNCHECKED_CAST")
  internal fun lower(): T {
    val trace = activeTrace()
    val temp = trace.freshName()
    val (otherStmts, otherValue, type) = otherwiseCase
      ?: error("When lowered without an otherwise arm")

    for ((_, arm) in cases) {
      if (arm.third != type) {
        throw MirageDiagnosticException(
          MirageDiagnosticCode.BRANCH_TYPE_MISMATCH,
          "branch/When arms produced different types: ${arm.third.name} vs ${type.name}",
          "make every arm return the same value type",
        )
      }
    }

    // Fold bottom-up: the innermost else is the otherwise arm, each case wraps the chain built so far.
    var elseBody: List<Statement> = otherStmts + Reassign(temp, otherValue)
    for ((cond, arm) in cases.asReversed()) {
      elseBody = listOf(IfBlock(cond, arm.first + Reassign(temp, arm.second), elseBody))
    }

    trace.statements += DeclareLocal(temp, type)
    // The outermost IfBlock, or (with no cases) the bare otherwise write.
    trace.statements += elseBody
    return wrapNode(type, VarRef(temp, type)) as T
  }
}

/** The common [ShaderType] of two arm values, or [MirageDiagnosticCode.BRANCH_TYPE_MISMATCH] if they differ. */
private fun armType(a: Expression, b: Expression): ShaderType {
  if (a.type != b.type) {
    throw MirageDiagnosticException(
      MirageDiagnosticCode.BRANCH_TYPE_MISMATCH,
      "branch/When arms produced different types: ${a.type.name} vs ${b.type.name}",
      "make every arm return the same value type",
    )
  }
  return a.type
}
