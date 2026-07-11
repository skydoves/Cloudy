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
 * emitter can produce compiles under both (see mirage-edsl-design.md §7.1) — there is no dialect
 * branch to make here.
 *
 * [uniformNames] maps a declaration slot to its uniform identifier, so [UniformRef] prints the same
 * name the hand-written kernels read (`shadow`, `highlight`, `amount`, ...).
 */
internal fun emitColorizeKernel(module: ShaderModule, uniformNames: List<String>): String =
  emitHelpers(module.helpers, uniformNames) +
    "half4 kernel(float2 p, half4 src) {\n" +
    emitBody(module, uniformNames, indent = "    ") +
    "}"

/**
 * Prints a [ShaderModule] to a Composite/Generate `main(float2 xy)` body — the shape both categories
 * splice into the preamble verbatim (MirageCompiler.kt's `assemble`), content sampling included in the
 * traced expressions rather than synthesized by a wrapper (unlike Colorize).
 */
internal fun emitCompositeOrGenerateMain(module: ShaderModule, uniformNames: List<String>): String =
  emitHelpers(module.helpers, uniformNames) +
    "half4 main(float2 xy) {\n" +
    emitBody(module, uniformNames, indent = "    ") +
    "}"

private fun emitBody(module: ShaderModule, uniformNames: List<String>, indent: String): String {
  val statements = module.statements.joinToString("") { emitStatement(it, uniformNames, indent) }
  return statements + "$indent return ${emit(module.returnExpr, uniformNames)};\n"
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
