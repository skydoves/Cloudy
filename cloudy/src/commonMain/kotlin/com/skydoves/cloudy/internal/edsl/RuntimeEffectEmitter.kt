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
 * Prints a [ShaderModule] to a Colorize `kernel(float2 p, half4 src)` body. One printer for both AGSL
 * and SkSL: AGSL is Skia's public (ES2-restricted) runtime-effect profile, so any expression this
 * emitter can produce compiles under both — there is no dialect branch to make here.
 *
 * [uniformNames] maps a declaration slot to its uniform identifier, so [UniformRef] prints the same
 * name the hand-written kernels read (`shadow`, `highlight`, `amount`, ...).
 */
internal fun emitColorizeKernel(module: ShaderModule, uniformNames: List<String>): String =
  hoist(module).let { hoisted ->
    emitHelpers(hoisted.helpers, uniformNames) +
      "half4 kernel(float2 p, half4 src) {\n" +
      emitBody(hoisted, uniformNames, indent = "    ") +
      "}"
  }

/**
 * Prints a [ShaderModule] to a Composite/Generate `main(float2 xy)` body — the shape both categories
 * splice into the preamble verbatim (MirageCompiler.kt's `assemble`), content sampling included in the
 * traced expressions rather than synthesized by a wrapper (unlike Colorize).
 */
internal fun emitCompositeOrGenerateMain(module: ShaderModule, uniformNames: List<String>): String =
  hoist(module).let { hoisted ->
    emitHelpers(hoisted.helpers, uniformNames) +
      "half4 main(float2 xy) {\n" +
      emitBody(hoisted, uniformNames, indent = "    ") +
      "}"
  }

private fun emitBody(module: ShaderModule, uniformNames: List<String>, indent: String): String {
  val statements = module.statements.joinToString("") { emitStatement(it, uniformNames, indent) }
  return statements + "$indent return ${emit(module.returnExpr, uniformNames)};\n"
}

/**
 * Common-subexpression hoisting. A fully-inlined trace repeats `lensSize * 0.5`, `max(halfDim.x, 1.0)`
 * and the like dozens of times, and the nesting depth alone overruns SkSL's parser (`exceeded max
 * parse depth`) — so this is a correctness pass, not a cosmetic one. It walks the whole forest
 * (return value + every statement, into nested `if` bodies), counts each **structurally equal**
 * subtree, and lifts every one that appears twice or more into a `_tN` local declared once at the top,
 * replacing its occurrences with a [VarRef].
 *
 * Deterministic by construction (same tree in → same names out), which the Chromatic five-look test
 * needs: it asserts every look emits byte-identical source, and each look re-runs this pass.
 *
 * A subtree is liftable only when it is position-independent — it must contain no [SampleContent] (a
 * `content.eval` tap stays exactly where the author wrote it; identical taps are never merged) and no
 * [VarRef] (a mutable `local`, which is only valid after its own assignment, not at the top). Trivial
 * leaves ([Literal] / [Argument] / [UniformRef] / [StandardUniform] / [VarRef]) are never worth a name.
 *
 * Lifting a subtree to the top can move it *above* an [EarlyReturn] guard it originally sat behind, so
 * it is now computed even for pixels the guard would have skipped. That is safe here because the
 * intrinsics are total (a bevel `normalize`/`pow` on an out-of-lens pixel just yields a discarded
 * value — the guard still returns its own result), and the raster-parity tests exercise the guarded
 * out-of-lens region. A future kernel whose guard protects a genuinely undefined operation would need
 * that guard kept as real control flow rather than folded into a lifted expression.
 */
private fun hoist(module: ShaderModule): ShaderModule {
  val roots = buildList {
    add(module.returnExpr)
    fun collect(statements: List<Statement>) {
      for (s in statements) when (s) {
        is Assign -> add(s.value)
        is Reassign -> add(s.value)
        is EarlyReturn -> { add(s.condition); add(s.value) }
        is IfBlock -> { add(s.condition); collect(s.body) }
      }
    }
    collect(module.statements)
  }

  val counts = mutableMapOf<Expression, Int>()
  for (root in roots) countSubtrees(root, counts)

  // Liftable subtrees, ordered smallest-first so a lifted subtree's own lifted children are declared
  // ahead of it (a smaller subtree strictly contained in a larger one has a lower node count).
  val order = mutableMapOf<Expression, Int>() // first-encounter index, the deterministic tie-break
  for (root in roots) recordOrder(root, order)
  val lifted = counts.entries
    .filter { it.value >= 2 && isLiftable(it.key) }
    .map { it.key }
    .sortedWith(compareBy({ nodeCount(it) }, { order[it] ?: Int.MAX_VALUE }))

  if (lifted.isEmpty()) return module

  val names = lifted.mapIndexed { i, expr -> expr to "_t$i" }.toMap()

  // Each lifted subtree's initializer substitutes its *nested* lifted subtrees (but not itself).
  val hoistAssigns = lifted.map { expr ->
    Assign(names.getValue(expr), substitute(expr, names, self = expr))
  }

  val newStatements = hoistAssigns + module.statements.map { substituteStatement(it, names) }
  return ShaderModule(newStatements, substitute(module.returnExpr, names, self = null), module.helpers)
}

/** Counts every structurally distinct liftable subtree; recurses through children regardless. */
private fun countSubtrees(node: Expression, counts: MutableMap<Expression, Int>) {
  if (isLiftable(node)) counts[node] = (counts[node] ?: 0) + 1
  for (child in children(node)) countSubtrees(child, counts)
}

private fun recordOrder(node: Expression, order: MutableMap<Expression, Int>) {
  if (isLiftable(node) && node !in order) order[node] = order.size
  for (child in children(node)) recordOrder(child, order)
}

/** Replaces each key subtree with its `_tN` [VarRef], except [self] (a lifted subtree's own body). */
private fun substitute(node: Expression, names: Map<Expression, String>, self: Expression?): Expression {
  if (node !== self) names[node]?.let { return VarRef(it, node.type) }
  return withChildren(node) { substitute(it, names, self) }
}

private fun substituteStatement(node: Statement, names: Map<Expression, String>): Statement = when (node) {
  is Assign -> Assign(node.name, substitute(node.value, names, self = null))
  is Reassign -> Reassign(node.name, substitute(node.value, names, self = null))
  is EarlyReturn -> EarlyReturn(substitute(node.condition, names, self = null), substitute(node.value, names, self = null))
  is IfBlock -> IfBlock(substitute(node.condition, names, self = null), node.body.map { substituteStatement(it, names) })
}

/** Trivial leaves and position-dependent nodes (`content.eval`, mutable locals) are never lifted. */
private fun isLiftable(node: Expression): Boolean = when (node) {
  is Literal, is Argument, is UniformRef, is StandardUniform, is VarRef -> false
  else -> !containsPositionDependent(node)
}

private fun containsPositionDependent(node: Expression): Boolean = when (node) {
  is SampleContent, is VarRef -> true
  else -> children(node).any { containsPositionDependent(it) }
}

private fun children(node: Expression): List<Expression> = when (node) {
  is Literal, is Argument, is UniformRef, is StandardUniform, is VarRef -> emptyList()
  is Unary -> listOf(node.operand)
  is Binary -> listOf(node.left, node.right)
  is Comparison -> listOf(node.left, node.right)
  is Swizzle -> listOf(node.base)
  is SampleContent -> listOf(node.coord)
  is Select -> listOf(node.condition, node.ifTrue, node.ifFalse)
  is Call -> node.args
}

private fun nodeCount(node: Expression): Int = 1 + children(node).sumOf { nodeCount(it) }

/** Rebuilds [node] with each child mapped through [transform], preserving its type. */
private fun withChildren(node: Expression, transform: (Expression) -> Expression): Expression = when (node) {
  is Literal, is Argument, is UniformRef, is StandardUniform, is VarRef -> node
  is Unary -> node.copy(operand = transform(node.operand))
  is Binary -> node.copy(left = transform(node.left), right = transform(node.right))
  is Comparison -> node.copy(left = transform(node.left), right = transform(node.right))
  is Swizzle -> node.copy(base = transform(node.base))
  is SampleContent -> node.copy(coord = transform(node.coord))
  is Select -> node.copy(condition = transform(node.condition), ifTrue = transform(node.ifTrue), ifFalse = transform(node.ifFalse))
  is Call -> node.copy(args = node.args.map(transform))
}

private fun emitHelpers(helpers: List<HelperFunction>, uniformNames: List<String>): String =
  helpers.joinToString("") { helper ->
    "${typeToken(helper.body.type)} ${helper.name}(${typeToken(helper.paramType)} ${helper.paramName}) " +
      "{ return ${emit(helper.body, uniformNames)}; }\n"
  }

private fun emitStatement(node: Statement, uniformNames: List<String>, indent: String): String = when (node) {
  is Assign -> "$indent${typeToken(node.value.type)} ${node.name} = ${emit(node.value, uniformNames)};\n"
  is Reassign -> "$indent${node.name} = ${emit(node.value, uniformNames)};\n"
  is EarlyReturn ->
    "$indent" + "if (${emit(node.condition, uniformNames)}) { return ${emit(node.value, uniformNames)}; }\n"
  is IfBlock -> {
    val innerIndent = "$indent    "
    val body = node.body.joinToString("") { emitStatement(it, uniformNames, innerIndent) }
    "$indent" + "if (${emit(node.condition, uniformNames)}) {\n" + body + "$indent}\n"
  }
}

private fun emit(node: Expression, uniformNames: List<String>): String = when (node) {
  is Literal -> formatLiteral(node.value)
  is Argument -> node.name
  is UniformRef -> uniformNames[node.slot]
  is StandardUniform -> node.name
  is VarRef -> node.name
  is SampleContent -> "content.eval(${emit(node.coord, uniformNames)})"
  is Select ->
    "(${emit(node.condition, uniformNames)} ? ${emit(node.ifTrue, uniformNames)} : ${emit(node.ifFalse, uniformNames)})"
  is Unary -> "${node.operator}${emit(node.operand, uniformNames)}"
  is Comparison -> "(${emit(node.left, uniformNames)} ${node.operator} ${emit(node.right, uniformNames)})"
  is Binary -> "(${emit(node.left, uniformNames)} ${node.operator} ${emit(node.right, uniformNames)})"
  is Swizzle -> "${emit(node.base, uniformNames)}.${node.components}"
  is Call -> emitCall(node, uniformNames)
}

/**
 * `mirage_luma` has no AGSL/SkSL preamble helper (adding one would touch the shared preamble for a
 * single kernel), so it lowers to the same `dot(rgb, half3(0.2126, 0.7152, 0.0722))` the hand-written
 * Duotone kernel spells out. Every other [Call] is a real AGSL/SkSL builtin, constructor, or a
 * user-defined [HelperFunction] name and prints verbatim.
 */
private fun emitCall(node: Call, uniformNames: List<String>): String {
  val args = node.args.joinToString(", ") { emit(it, uniformNames) }
  return if (node.functionName == "mirage_luma") {
    "dot($args, half3(0.2126, 0.7152, 0.0722))"
  } else {
    "${node.functionName}($args)"
  }
}

/** GLSL float literals always carry a decimal point; whole values print e.g. `1.0`, not `1`. */
private fun formatLiteral(v: Float): String = if (v == v.toLong().toFloat()) "${v.toLong()}.0" else v.toString()

private fun typeToken(type: ShaderType): String = when (type) {
  ShaderType.Float1 -> "float"
  ShaderType.Float2 -> "float2"
  ShaderType.Float3 -> "float3"
  ShaderType.Float4 -> "float4"
  ShaderType.Half1 -> "half"
  ShaderType.Half3 -> "half3"
  ShaderType.Half4 -> "half4"
  ShaderType.Bool -> "bool"
}
