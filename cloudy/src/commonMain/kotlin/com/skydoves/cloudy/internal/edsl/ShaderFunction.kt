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

package com.skydoves.cloudy.internal.edsl

import com.skydoves.cloudy.ExperimentalMirage
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty

/**
 * A shader value type paired with the [ShaderType] it maps to and the way to wrap a traced node back
 * into that value type. A property delegate ([shaderFunction]) has no reified access to its lambda's
 * parameter types at runtime, so a call passes these tokens explicitly (`shaderFunction(Float2, Float1)
 * { ... }`) — the token both names the emitted GLSL type (`float2`) and rebuilds the `p: Float2` argument
 * handle the body traces against. The token objects below are the sole instances; each is a value type's
 * companion in spirit, kept separate so the value interfaces stay pure node carriers.
 */
@ExperimentalMirage
public class ShaderValueType<T> internal constructor(
  internal val shaderType: ShaderType,
  internal val wrap: (Expression) -> T,
  internal val unwrap: (T) -> Expression,
)

@ExperimentalMirage public val Float1Type: ShaderValueType<Float1> =
  ShaderValueType(ShaderType.Float1, { Float1(it) }, { it.e })

@ExperimentalMirage public val Float2Type: ShaderValueType<Float2> =
  ShaderValueType(ShaderType.Float2, { Float2(it) }, { it.e })

@ExperimentalMirage public val Float3Type: ShaderValueType<Float3> =
  ShaderValueType(ShaderType.Float3, { Float3(it) }, { it.e })

@ExperimentalMirage public val Float4Type: ShaderValueType<Float4> =
  ShaderValueType(ShaderType.Float4, { Float4(it) }, { it.e })

@ExperimentalMirage public val Half1Type: ShaderValueType<Half1> =
  ShaderValueType(ShaderType.Half1, { Half1(it) }, { it.e })

@ExperimentalMirage public val Half3Type: ShaderValueType<Half3> =
  ShaderValueType(ShaderType.Half3, { Half3(it) }, { it.e })

@ExperimentalMirage public val Half4Type: ShaderValueType<Half4> =
  ShaderValueType(ShaderType.Half4, { Half4(it) }, { it.e })

/**
 * The result of `val sdCircle by shaderFunction(...) { ... }`: a callable the body invokes like a plain
 * Kotlin function (`sdCircle(xy)`), which registers the traced helper once and returns its [Call] node.
 * Read via the [getValue] operator so the `by` delegate hands back the function itself.
 */
@ExperimentalMirage
public class ShaderFunction1<A, R> internal constructor(private val call: (A) -> R) {
  public operator fun getValue(thisRef: Any?, property: KProperty<*>): (A) -> R = call
}

@ExperimentalMirage
public class ShaderFunction2<A, B, R> internal constructor(private val call: (A, B) -> R) {
  public operator fun getValue(thisRef: Any?, property: KProperty<*>): (A, B) -> R = call
}

@ExperimentalMirage
public class ShaderFunction3<A, B, C, R> internal constructor(private val call: (A, B, C) -> R) {
  public operator fun getValue(thisRef: Any?, property: KProperty<*>): (A, B, C) -> R = call
}

@ExperimentalMirage
public class ShaderFunction4<A, B, C, D, R> internal constructor(
  private val call: (A, B, C, D) -> R,
) {
  public operator fun getValue(thisRef: Any?, property: KProperty<*>): (A, B, C, D) -> R = call
}

@ExperimentalMirage
public class ShaderFunction5<A, B, C, D, E, R> internal constructor(
  private val call: (A, B, C, D, E) -> R,
) {
  public operator fun getValue(thisRef: Any?, property: KProperty<*>): (A, B, C, D, E) -> R = call
}

/**
 * Captures the property name of `val <name> by shaderFunction(...) { ... }` and, on first call, traces
 * the body into a user-defined [HelperFunction] named after the property (deduped by [defineHelper]).
 * Subsequent calls just emit another [Call] to the already-declared helper.
 *
 * The captured name is guarded against [MirageReservedNames.RESERVED] at declaration time — a
 * `val mix by shaderFunction` would shadow a builtin and silently miscompile, so it fails loudly with
 * [MirageDiagnosticCode.RESERVED_IDENTIFIER] instead. The declared return type ([returnType]) is
 * cross-checked against the type the traced body actually produced; a mismatch (which Kotlin's generics
 * usually reject at compile time, but this catches when a body's value type is widened) raises
 * [MirageDiagnosticCode.FUNCTION_RETURN_TYPE_MISMATCH].
 */
@ExperimentalMirage
public fun <A, R> shaderFunction(
  param: ShaderValueType<A>,
  returns: ShaderValueType<R>,
  body: (A) -> R,
): PropertyDelegateProvider<Any?, ShaderFunction1<A, R>> =
  PropertyDelegateProvider { _, property ->
    val name = reservedGuard(property.name)
    ShaderFunction1 { a ->
      val node = defineHelper(
        name = name,
        params = listOf("p0" to param.shaderType),
        returnType = returns.shaderType,
        args = listOf(param.unwrap(a)),
      ) {
        val result = body(param.wrap(Argument("p0", param.shaderType)))
        checkReturnType(name, returns.shaderType, returns.unwrap(result))
      }
      returns.wrap(node)
    }
  }

@ExperimentalMirage
public fun <A, B, R> shaderFunction(
  param0: ShaderValueType<A>,
  param1: ShaderValueType<B>,
  returns: ShaderValueType<R>,
  body: (A, B) -> R,
): PropertyDelegateProvider<Any?, ShaderFunction2<A, B, R>> =
  PropertyDelegateProvider { _, property ->
    val name = reservedGuard(property.name)
    ShaderFunction2 { a, b ->
      val node = defineHelper(
        name = name,
        params = listOf("p0" to param0.shaderType, "p1" to param1.shaderType),
        returnType = returns.shaderType,
        args = listOf(param0.unwrap(a), param1.unwrap(b)),
      ) {
        val result = body(
          param0.wrap(Argument("p0", param0.shaderType)),
          param1.wrap(Argument("p1", param1.shaderType)),
        )
        checkReturnType(name, returns.shaderType, returns.unwrap(result))
      }
      returns.wrap(node)
    }
  }

@ExperimentalMirage
public fun <A, B, C, R> shaderFunction(
  param0: ShaderValueType<A>,
  param1: ShaderValueType<B>,
  param2: ShaderValueType<C>,
  returns: ShaderValueType<R>,
  body: (A, B, C) -> R,
): PropertyDelegateProvider<Any?, ShaderFunction3<A, B, C, R>> =
  PropertyDelegateProvider { _, property ->
    val name = reservedGuard(property.name)
    ShaderFunction3 { a, b, c ->
      val node = defineHelper(
        name = name,
        params = listOf(
          "p0" to param0.shaderType,
          "p1" to param1.shaderType,
          "p2" to param2.shaderType,
        ),
        returnType = returns.shaderType,
        args = listOf(param0.unwrap(a), param1.unwrap(b), param2.unwrap(c)),
      ) {
        val result = body(
          param0.wrap(Argument("p0", param0.shaderType)),
          param1.wrap(Argument("p1", param1.shaderType)),
          param2.wrap(Argument("p2", param2.shaderType)),
        )
        checkReturnType(name, returns.shaderType, returns.unwrap(result))
      }
      returns.wrap(node)
    }
  }

@ExperimentalMirage
public fun <A, B, C, D, R> shaderFunction(
  param0: ShaderValueType<A>,
  param1: ShaderValueType<B>,
  param2: ShaderValueType<C>,
  param3: ShaderValueType<D>,
  returns: ShaderValueType<R>,
  body: (A, B, C, D) -> R,
): PropertyDelegateProvider<Any?, ShaderFunction4<A, B, C, D, R>> =
  PropertyDelegateProvider { _, property ->
    val name = reservedGuard(property.name)
    ShaderFunction4 { a, b, c, d ->
      val node = defineHelper(
        name = name,
        params = listOf(
          "p0" to param0.shaderType,
          "p1" to param1.shaderType,
          "p2" to param2.shaderType,
          "p3" to param3.shaderType,
        ),
        returnType = returns.shaderType,
        args = listOf(param0.unwrap(a), param1.unwrap(b), param2.unwrap(c), param3.unwrap(d)),
      ) {
        val result = body(
          param0.wrap(Argument("p0", param0.shaderType)),
          param1.wrap(Argument("p1", param1.shaderType)),
          param2.wrap(Argument("p2", param2.shaderType)),
          param3.wrap(Argument("p3", param3.shaderType)),
        )
        checkReturnType(name, returns.shaderType, returns.unwrap(result))
      }
      returns.wrap(node)
    }
  }

@ExperimentalMirage
public fun <A, B, C, D, E, R> shaderFunction(
  param0: ShaderValueType<A>,
  param1: ShaderValueType<B>,
  param2: ShaderValueType<C>,
  param3: ShaderValueType<D>,
  param4: ShaderValueType<E>,
  returns: ShaderValueType<R>,
  body: (A, B, C, D, E) -> R,
): PropertyDelegateProvider<Any?, ShaderFunction5<A, B, C, D, E, R>> =
  PropertyDelegateProvider { _, property ->
    val name = reservedGuard(property.name)
    ShaderFunction5 { a, b, c, d, e ->
      val node = defineHelper(
        name = name,
        params = listOf(
          "p0" to param0.shaderType,
          "p1" to param1.shaderType,
          "p2" to param2.shaderType,
          "p3" to param3.shaderType,
          "p4" to param4.shaderType,
        ),
        returnType = returns.shaderType,
        args = listOf(
          param0.unwrap(a),
          param1.unwrap(b),
          param2.unwrap(c),
          param3.unwrap(d),
          param4.unwrap(e),
        ),
      ) {
        val result = body(
          param0.wrap(Argument("p0", param0.shaderType)),
          param1.wrap(Argument("p1", param1.shaderType)),
          param2.wrap(Argument("p2", param2.shaderType)),
          param3.wrap(Argument("p3", param3.shaderType)),
          param4.wrap(Argument("p4", param4.shaderType)),
        )
        checkReturnType(name, returns.shaderType, returns.unwrap(result))
      }
      returns.wrap(node)
    }
  }

/** Fails with [MirageDiagnosticCode.RESERVED_IDENTIFIER] if [name] shadows a builtin/preamble/keyword. */
private fun reservedGuard(name: String): String {
  if (name in MirageReservedNames.RESERVED) {
    throw MirageDiagnosticException(
      MirageDiagnosticCode.RESERVED_IDENTIFIER,
      "shader function name '$name' shadows a builtin",
      "rename it, e.g. '${name}Fn'",
    )
  }
  return name
}

/**
 * Verifies the type the traced body produced matches the declared return type, then returns the node.
 * Kotlin's generics reject most mismatches at compile time; this defends the case where a value type is
 * widened (e.g. a body inferred as a supertype) so the emitted `float2 f(...) { return <half3>; }` never
 * ships a type error to the GPU compiler.
 */
private fun checkReturnType(name: String, declared: ShaderType, produced: Expression): Expression {
  if (produced.type != declared) {
    throw MirageDiagnosticException(
      MirageDiagnosticCode.FUNCTION_RETURN_TYPE_MISMATCH,
      "shader function '$name' declares ${declared.name} but its body produced ${produced.type.name}",
      "make the body's final expression a ${declared.name}, or change the declared return type",
    )
  }
  return produced
}
