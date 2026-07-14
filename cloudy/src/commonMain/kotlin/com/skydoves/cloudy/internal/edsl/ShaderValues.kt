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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.UTexture

/**
 * The typed values a mirage kernel body works with — a `float2` position, a `half4` color, etc. Public
 * (behind `@ExperimentalMirage`, so out of the committed ABI dump) because the body lambda handed to
 * [com.skydoves.cloudy.MirageShader.colorize] / `composite` / `generate` operates on them directly.
 *
 * Each is an interface, not a class, for one reason: a [com.skydoves.cloudy.MirageParams] uniform
 * handle (`UFloat`, `UColor`, ...) implements the matching value interface, so a handle *is* an
 * expression the body can use bare — `mix(shadow.rgb, highlight.rgb, g)` with no `.lift()` step. It is
 * **not** `sealed`: a handle lives in a different package (`com.skydoves.cloudy`), and Kotlin's sealed
 * rule confines implementors to the declaring package. The backing node is carried through [e]; every
 * producer either wraps a node via the companion `invoke` factory ([Float1] etc.) or, for a handle,
 * returns a [UniformRef] from its own `e`.
 *
 * [e] is public because interface members cannot be `internal` in Kotlin — but the only concrete
 * [Expression] a caller can obtain is opaque (all node types are `internal`), so exposing it does not
 * widen the authoring surface, it only lets a handle satisfy the interface.
 *
 * The one member every mirage value type carries — its backing [Expression] node. A shared supertype so
 * a generic consumer ([branch]/[When]/[otherwise], which take an arbitrary value type `T`) can reach the
 * node without a per-type cast. Value-neutral: it adds no authoring surface (the node is opaque), it only
 * unifies the `.e` the eight leaves already declare identically.
 */
@ExperimentalMirage
public interface ShaderValue {
  public val e: Expression
}

/**
 * Wraps [node] in the leaf value type matching [type] — the inverse of reading `.e`, keyed on the node's
 * own [ShaderType]. Lets a generic consumer ([branch]/[When]) hand back a `VarRef(_temp)` as a value of
 * the type the arms produced without knowing which leaf it is; the returned leaf implements the arms'
 * common interface `T`, so the consumer's `as T` cast holds. [ShaderType.Bool] has no value leaf (a
 * [UBool] is only ever built by a comparison), so it is not a valid branch-arm result type.
 */
internal fun wrapNode(type: ShaderType, node: Expression): ShaderValue = when (type) {
  ShaderType.Float1 -> Float1(node)
  ShaderType.Float2 -> Float2(node)
  ShaderType.Float3 -> Float3(node)
  ShaderType.Float4 -> Float4(node)
  ShaderType.Half1 -> Half1(node)
  ShaderType.Half3 -> Half3(node)
  ShaderType.Half4 -> Half4(node)
  ShaderType.Bool -> error("a branch/When arm cannot produce a bool value")
}

@ExperimentalMirage
public interface Float1 : ShaderValue {
  override val e: Expression

  @ExperimentalMirage
  public companion object {
    internal operator fun invoke(e: Expression): Float1 = Float1Node(e)
  }
}

@ExperimentalMirage
public interface Float2 : ShaderValue {
  override val e: Expression

  @ExperimentalMirage
  public companion object {
    internal operator fun invoke(e: Expression): Float2 = Float2Node(e)
  }
}

@ExperimentalMirage
public interface Float3 : ShaderValue {
  override val e: Expression

  @ExperimentalMirage
  public companion object {
    internal operator fun invoke(e: Expression): Float3 = Float3Node(e)
  }
}

@ExperimentalMirage
public interface Float4 : ShaderValue {
  override val e: Expression

  @ExperimentalMirage
  public companion object {
    internal operator fun invoke(e: Expression): Float4 = Float4Node(e)
  }
}

@ExperimentalMirage
public interface Half1 : ShaderValue {
  override val e: Expression

  @ExperimentalMirage
  public companion object {
    internal operator fun invoke(e: Expression): Half1 = Half1Node(e)
  }
}

@ExperimentalMirage
public interface Half3 : ShaderValue {
  override val e: Expression

  @ExperimentalMirage
  public companion object {
    internal operator fun invoke(e: Expression): Half3 = Half3Node(e)
  }
}

@ExperimentalMirage
public interface Half4 : ShaderValue {
  override val e: Expression

  @ExperimentalMirage
  public companion object {
    internal operator fun invoke(e: Expression): Half4 = Half4Node(e)
  }
}

/** A boolean value — the result of a comparison, consumed only by [guard] / [If]. */
@ExperimentalMirage
public interface UBool : ShaderValue {
  override val e: Expression

  @ExperimentalMirage
  public companion object {
    internal operator fun invoke(e: Expression): UBool = UBoolNode(e)
  }
}

// Concrete leaves. `internal`, so a value interface can only ever be a handle or one of these — the
// companion `invoke`s above are the sole way to wrap a traced node.
internal class Float1Node(override val e: Expression) : Float1
internal class Float2Node(override val e: Expression) : Float2
internal class Float3Node(override val e: Expression) : Float3
internal class Float4Node(override val e: Expression) : Float4
internal class Half1Node(override val e: Expression) : Half1
internal class Half3Node(override val e: Expression) : Half3
internal class Half4Node(override val e: Expression) : Half4
internal class UBoolNode(override val e: Expression) : UBool

/**
 * The comparisons Foil/Specular's guards need (`sdf > SMOOTH_EDGE_PX`, `pixel.a <= 0.0`). The names
 * reuse GLSL's vector relational builtins (`greaterThan`/`lessThanEqual`/...), but note the semantics
 * differ: our infix compares two **scalars** and yields a [UBool], where GLSL's builtins compare vector
 * components element-wise and yield a `bvec` — same spelling, scalar meaning.
 */
@ExperimentalMirage
public infix fun Float1.greaterThan(o: Float1): UBool = UBool(Comparison(">", e, o.e))

@ExperimentalMirage
public infix fun Float1.greaterThan(o: Float): UBool = UBool(Comparison(">", e, Literal(o)))

@ExperimentalMirage
public infix fun Half1.lessThanEqual(o: Float): UBool = UBool(Comparison("<=", e, Literal(o)))

/** `&&` — Specular's highlight gate (`edge > 0.0 && specStrength > 0.0`). */
@ExperimentalMirage
public infix fun UBool.and(o: UBool): UBool = UBool(Comparison("&&", e, o.e))

@ExperimentalMirage
public operator fun Float1.plus(o: Float1): Float1 = Float1(Binary("+", e, o.e, ShaderType.Float1))

@ExperimentalMirage
public operator fun Float1.minus(o: Float1): Float1 = Float1(Binary("-", e, o.e, ShaderType.Float1))

@ExperimentalMirage
public operator fun Float1.times(o: Float1): Float1 = Float1(Binary("*", e, o.e, ShaderType.Float1))

@ExperimentalMirage
public operator fun Float1.div(o: Float1): Float1 = Float1(Binary("/", e, o.e, ShaderType.Float1))

@ExperimentalMirage
public operator fun Float1.unaryMinus(): Float1 = Float1(Unary("-", e, ShaderType.Float1))

// Float literals directly on the left/right of a scalar op, so a body can write `0.5f * along` and
// `t * 0.25f` without a `float1(...)` wrapper. Distinct parameter types from the built-in
// `Float.times(Float)`, so there is no ambiguity.
@ExperimentalMirage
public operator fun Float1.plus(o: Float): Float1 =
  Float1(Binary("+", e, Literal(o), ShaderType.Float1))

@ExperimentalMirage
public operator fun Float.plus(o: Float1): Float1 =
  Float1(Binary("+", Literal(this), o.e, ShaderType.Float1))

@ExperimentalMirage
public operator fun Float1.minus(o: Float): Float1 =
  Float1(Binary("-", e, Literal(o), ShaderType.Float1))

@ExperimentalMirage
public operator fun Float.minus(o: Float1): Float1 =
  Float1(Binary("-", Literal(this), o.e, ShaderType.Float1))

@ExperimentalMirage
public operator fun Float1.times(o: Float): Float1 =
  Float1(Binary("*", e, Literal(o), ShaderType.Float1))

@ExperimentalMirage
public operator fun Float.times(o: Float1): Float1 =
  Float1(Binary("*", Literal(this), o.e, ShaderType.Float1))

@ExperimentalMirage
public operator fun Float1.div(o: Float): Float1 =
  Float1(Binary("/", e, Literal(o), ShaderType.Float1))

@ExperimentalMirage
public operator fun Float.div(o: Float1): Float1 =
  Float1(Binary("/", Literal(this), o.e, ShaderType.Float1))

@ExperimentalMirage
public operator fun Float2.plus(o: Float2): Float2 = Float2(Binary("+", e, o.e, ShaderType.Float2))

@ExperimentalMirage
public operator fun Float2.plus(o: Float1): Float2 = Float2(Binary("+", e, o.e, ShaderType.Float2))

@ExperimentalMirage
public operator fun Float2.plus(o: Float): Float2 =
  Float2(Binary("+", e, Literal(o), ShaderType.Float2))

@ExperimentalMirage
public operator fun Float2.minus(o: Float2): Float2 = Float2(Binary("-", e, o.e, ShaderType.Float2))

@ExperimentalMirage
public operator fun Float2.times(o: Float1): Float2 = Float2(Binary("*", e, o.e, ShaderType.Float2))

@ExperimentalMirage
public operator fun Float2.times(o: Float): Float2 =
  Float2(Binary("*", e, Literal(o), ShaderType.Float2))

@ExperimentalMirage
public operator fun Float2.times(o: Float2): Float2 = Float2(Binary("*", e, o.e, ShaderType.Float2))

@ExperimentalMirage
public operator fun Float2.div(o: Float1): Float2 = Float2(Binary("/", e, o.e, ShaderType.Float2))

@ExperimentalMirage
public operator fun Float2.div(o: Float2): Float2 = Float2(Binary("/", e, o.e, ShaderType.Float2))

@ExperimentalMirage
public operator fun Float2.unaryMinus(): Float2 = Float2(Unary("-", e, ShaderType.Float2))

@ExperimentalMirage
public operator fun Float3.plus(o: Float3): Float3 = Float3(Binary("+", e, o.e, ShaderType.Float3))

@ExperimentalMirage
public operator fun Float3.minus(o: Float1): Float3 = Float3(Binary("-", e, o.e, ShaderType.Float3))

@ExperimentalMirage
public operator fun Float3.minus(o: Float): Float3 =
  Float3(Binary("-", e, Literal(o), ShaderType.Float3))

@ExperimentalMirage
public operator fun Float3.times(o: Float1): Float3 = Float3(Binary("*", e, o.e, ShaderType.Float3))

@ExperimentalMirage
public operator fun Float3.times(o: Float): Float3 =
  Float3(Binary("*", e, Literal(o), ShaderType.Float3))

@ExperimentalMirage
public operator fun Float1.times(o: Float3): Float3 = Float3(Binary("*", e, o.e, ShaderType.Float3))

@ExperimentalMirage
public operator fun Float3.times(o: Float3): Float3 = Float3(Binary("*", e, o.e, ShaderType.Float3))

@ExperimentalMirage
public operator fun Float3.div(o: Float1): Float3 = Float3(Binary("/", e, o.e, ShaderType.Float3))

@ExperimentalMirage
public operator fun Half3.plus(o: Half3): Half3 = Half3(Binary("+", e, o.e, ShaderType.Half3))

@ExperimentalMirage
public operator fun Half3.minus(o: Half3): Half3 = Half3(Binary("-", e, o.e, ShaderType.Half3))

@ExperimentalMirage
public operator fun Half3.times(o: Half3): Half3 = Half3(Binary("*", e, o.e, ShaderType.Half3))

@ExperimentalMirage
public operator fun Half3.times(o: Half1): Half3 = Half3(Binary("*", e, o.e, ShaderType.Half3))

@ExperimentalMirage
public operator fun Half1.times(o: Half3): Half3 = Half3(Binary("*", e, o.e, ShaderType.Half3))

/**
 * `half3 * float` — GLSL's implicit scalar-widening lets a `float` multiply a `half3` directly (as
 * `SPECULAR_KERNEL_AGSL`'s `(1.0 - pixel.rgb) * clamp(highlight, 0.0, 1.0)` does); the eDSL keeps the
 * operand a plain [Float1] here rather than forcing an explicit `half()` cast the source doesn't have.
 */
@ExperimentalMirage
public operator fun Half3.times(o: Float1): Half3 = Half3(Binary("*", e, o.e, ShaderType.Half3))

/** `half3(1.0)` — a broadcast constant, e.g. the `(1.0 - pixel.rgb)` screen-blend complement. */
@ExperimentalMirage
public fun half3(scalar: Float): Half3 =
  Half3(Call("half3", listOf(Literal(scalar)), ShaderType.Half3))

/** `.x` / `.y` on a `float2`. */
@ExperimentalMirage
public val Float2.x: Float1 get() = Float1(Swizzle(e, "x", ShaderType.Float1))

@ExperimentalMirage
public val Float2.y: Float1 get() = Float1(Swizzle(e, "y", ShaderType.Float1))

/** `.x` on a `float3` (the `p.x >= 0.0` sign-select pattern). */
@ExperimentalMirage
public val Float3.x: Float1 get() = Float1(Swizzle(e, "x", ShaderType.Float1))

/** `.xyz` on a `float4` (Chromatic's `chromaticKRGB.xyz` — the uniform's `.w` is declared but unused). */
@ExperimentalMirage
public val Float4.xyz: Float3 get() = Float3(Swizzle(e, "xyz", ShaderType.Float3))

/**
 * `.rgb` on a `half4`: read narrows to `half3`, and — on a `var pixel by local(...)` — *write* lowers to
 * `pixel.rgb = ...;` in place, exactly as the GLSL original writes a channel (instead of rebuilding the
 * whole `half4(processColor(pixel.rgb, ...), pixel.a)`).
 *
 * Only `plus` is defined on [Half3], never `plusAssign`, so `pixel.rgb += x` lowers as get→plus→set — one
 * `.rgb =` write — automatically; defining `plusAssign` alongside `plus` would be a compile error (Kotlin
 * rejects the assign-operator ambiguity), so it must never be added.
 *
 * The write only makes sense on a mutable local (a value whose node is a [VarRef]); writing a channel of a
 * computed `half4` has no target, so [localName] fails loudly there. The target `"$name.rgb"` prints
 * verbatim; the hoist leaves it alone (a [VarRef] is un-liftable) and only lifts the assigned value's CSEs.
 */
@ExperimentalMirage
public var Half4.rgb: Half3
  get() = Half3(Swizzle(e, "rgb", ShaderType.Half3))
  set(value) {
    activeTrace().statements += Reassign("${localName(e, "rgb")}.rgb", value.e)
  }

@ExperimentalMirage
public var Half4.a: Half1
  get() = Half1(Swizzle(e, "a", ShaderType.Half1))
  set(value) {
    activeTrace().statements += Reassign("${localName(e, "a")}.a", value.e)
  }

/** The backing local name a write-swizzle targets, or a loud failure if the value is not a mutable local. */
private fun localName(node: Expression, channel: String): String = (node as? VarRef)?.name
  ?: error("cannot write .$channel of a computed half4 — write-swizzle needs a `var x by local(...)`")

@ExperimentalMirage
public fun float1(v: Float): Float1 = Float1(Literal(v, ShaderType.Float1))

@ExperimentalMirage
public fun float2(x: Float1, y: Float1): Float2 =
  Float2(Call("float2", listOf(x.e, y.e), ShaderType.Float2))

@ExperimentalMirage
public fun float2(x: Float, y: Float): Float2 =
  Float2(Call("float2", listOf(Literal(x), Literal(y)), ShaderType.Float2))

@ExperimentalMirage
public fun float3(x: Float, y: Float, z: Float): Float3 = Float3(
  Call("float3", listOf(Literal(x), Literal(y), Literal(z)), ShaderType.Float3),
)

@ExperimentalMirage
public fun float3(v: Float1): Float3 = Float3(Call("float3", listOf(v.e), ShaderType.Float3))

@ExperimentalMirage
public fun float3(xy: Float2, z: Float1): Float3 =
  Float3(Call("float3", listOf(xy.e, z.e), ShaderType.Float3))

@ExperimentalMirage
public fun float3(xy: Float2, z: Float): Float3 =
  Float3(Call("float3", listOf(xy.e, Literal(z)), ShaderType.Float3))

/**
 * `float4(0.0, 0.0, 0.0, 0.0)` — Specular's transparent-tint argument to `processColor`. A [Float4],
 * like every other value type, so a body can pass it around; the emitter prints the `float4(...)`
 * constructor verbatim.
 */
@ExperimentalMirage
public fun float4(x: Float, y: Float, z: Float, w: Float): Float4 =
  Float4(Call("float4", listOf(Literal(x), Literal(y), Literal(z), Literal(w)), ShaderType.Float4))

@ExperimentalMirage
public fun half3(v: Float3): Half3 = Half3(Call("half3", listOf(v.e), ShaderType.Half3))

@ExperimentalMirage
public fun half(v: Float1): Half1 = Half1(Call("half", listOf(v.e), ShaderType.Half1))

@ExperimentalMirage
public fun half4(rgb: Half3, a: Half1): Half4 =
  Half4(Call("half4", listOf(rgb.e, a.e), ShaderType.Half4))

@ExperimentalMirage
public fun mix(a: Half4, b: Half4, t: Float1): Half4 =
  Half4(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Half4))

/** `half4(0.0)` — the all-channels-zero constructor Foil's lens-bounds early-out returns. */
@ExperimentalMirage
public fun half4(scalar: Float): Half4 =
  Half4(Call("half4", listOf(Literal(scalar)), ShaderType.Half4))

/** `cond ? ifTrue : ifFalse` — Kotlin has no ternary to overload, so this is the DSL spelling of one. */
@ExperimentalMirage
public fun select(condition: UBool, ifTrue: Float1, ifFalse: Float1): Float1 =
  Float1(Select(condition.e, ifTrue.e, ifFalse.e, ShaderType.Float1))

@ExperimentalMirage
public infix fun Float1.greaterThanEqual(o: Float): UBool = UBool(Comparison(">=", e, Literal(o)))

/**
 * `p.x >= 0.0 ? 1.0 : -1.0` — the sign-select the superellipse bevel direction needs on each axis.
 * Shared by the Specular and Chromatic bodies (both build the same bevel field).
 */
@ExperimentalMirage
public fun signSelect(v: Float1): Float1 = select(v greaterThanEqual 0f, float1(1f), float1(-1f))

@ExperimentalMirage
public fun min(a: Float1, b: Float1): Float1 =
  Float1(Call("min", listOf(a.e, b.e), ShaderType.Float1))

@ExperimentalMirage
public fun max(a: Float1, b: Float1): Float1 =
  Float1(Call("max", listOf(a.e, b.e), ShaderType.Float1))

@ExperimentalMirage
public fun max(a: Float1, b: Float): Float1 =
  Float1(Call("max", listOf(a.e, Literal(b)), ShaderType.Float1))

@ExperimentalMirage
public fun max(a: Half1, b: Half1): Half1 = Half1(Call("max", listOf(a.e, b.e), ShaderType.Half1))

@ExperimentalMirage
public fun exp(v: Float1): Float1 = Float1(Call("exp", listOf(v.e), ShaderType.Float1))

@ExperimentalMirage
public fun abs(a: Float1): Float1 = Float1(Call("abs", listOf(a.e), ShaderType.Float1))

@ExperimentalMirage
public fun abs(a: Float2): Float2 = Float2(Call("abs", listOf(a.e), ShaderType.Float2))

@ExperimentalMirage
public fun abs(a: Float3): Float3 = Float3(Call("abs", listOf(a.e), ShaderType.Float3))

@ExperimentalMirage
public fun clamp(a: Float1, lo: Float1, hi: Float1): Float1 =
  Float1(Call("clamp", listOf(a.e, lo.e, hi.e), ShaderType.Float1))

@ExperimentalMirage
public fun clamp(a: Float1, lo: Float, hi: Float): Float1 =
  Float1(Call("clamp", listOf(a.e, Literal(lo), Literal(hi)), ShaderType.Float1))

@ExperimentalMirage
public fun clamp(a: Float3, lo: Float1, hi: Float1): Float3 =
  Float3(Call("clamp", listOf(a.e, lo.e, hi.e), ShaderType.Float3))

@ExperimentalMirage
public fun clamp(a: Float3, lo: Float, hi: Float): Float3 =
  Float3(Call("clamp", listOf(a.e, Literal(lo), Literal(hi)), ShaderType.Float3))

@ExperimentalMirage
public fun smoothstep(lo: Float1, hi: Float1, x: Float1): Float1 =
  Float1(Call("smoothstep", listOf(lo.e, hi.e, x.e), ShaderType.Float1))

@ExperimentalMirage
public fun smoothstep(lo: Float, hi: Float, x: Float1): Float1 =
  Float1(Call("smoothstep", listOf(Literal(lo), Literal(hi), x.e), ShaderType.Float1))

@ExperimentalMirage
public fun smoothstep(lo: Float, hi: Float1, x: Float1): Float1 =
  Float1(Call("smoothstep", listOf(Literal(lo), hi.e, x.e), ShaderType.Float1))

@ExperimentalMirage
public fun smoothstep(lo: Float1, hi: Float, x: Float1): Float1 =
  Float1(Call("smoothstep", listOf(lo.e, Literal(hi), x.e), ShaderType.Float1))

@ExperimentalMirage
public fun step(edge: Float1, x: Float1): Float1 =
  Float1(Call("step", listOf(edge.e, x.e), ShaderType.Float1))

@ExperimentalMirage
public fun step(edge: Float, x: Float1): Float1 =
  Float1(Call("step", listOf(Literal(edge), x.e), ShaderType.Float1))

@ExperimentalMirage
public fun length(v: Float2): Float1 = Float1(Call("length", listOf(v.e), ShaderType.Float1))

@ExperimentalMirage
public fun normalize(v: Float2): Float2 = Float2(Call("normalize", listOf(v.e), ShaderType.Float2))

@ExperimentalMirage
public fun normalize(v: Float3): Float3 = Float3(Call("normalize", listOf(v.e), ShaderType.Float3))

@ExperimentalMirage
public fun dot(a: Float2, b: Float2): Float1 =
  Float1(Call("dot", listOf(a.e, b.e), ShaderType.Float1))

@ExperimentalMirage
public fun dot(a: Float3, b: Float3): Float1 =
  Float1(Call("dot", listOf(a.e, b.e), ShaderType.Float1))

@ExperimentalMirage
public fun pow(base: Float1, exponent: Float1): Float1 =
  Float1(Call("pow", listOf(base.e, exponent.e), ShaderType.Float1))

@ExperimentalMirage
public fun pow(base: Float1, exponent: Float): Float1 =
  Float1(Call("pow", listOf(base.e, Literal(exponent)), ShaderType.Float1))

@ExperimentalMirage
public fun sqrt(v: Float1): Float1 = Float1(Call("sqrt", listOf(v.e), ShaderType.Float1))

/**
 * `processColor(src, vibrancy, intensity, overlay)` — the preamble helper every Composite/Generate
 * kernel already has in scope (MiragePreamble.kt); Specular calls it at saturation=1/contrast=1/
 * transparent-overlay (identity, kept only for bit-parity with the hand-written kernel).
 */
@ExperimentalMirage
public fun processColor(src: Half3, vibrancy: Float1, intensity: Float1, overlay: Float4): Half3 =
  Half3(Call("processColor", listOf(src.e, vibrancy.e, intensity.e, overlay.e), ShaderType.Half3))

@ExperimentalMirage
public fun processColor(src: Half3, vibrancy: Float, intensity: Float, overlay: Float4): Half3 =
  Half3(
    Call(
      "processColor",
      listOf(src.e, Literal(vibrancy), Literal(intensity), overlay.e),
      ShaderType.Half3,
    ),
  )

@ExperimentalMirage
public fun fract(v: Float1): Float1 = Float1(Call("fract", listOf(v.e), ShaderType.Float1))

@ExperimentalMirage
public fun fract(v: Float2): Float2 = Float2(Call("fract", listOf(v.e), ShaderType.Float2))

@ExperimentalMirage
public fun fract(v: Float3): Float3 = Float3(Call("fract", listOf(v.e), ShaderType.Float3))

@ExperimentalMirage
public fun floor(v: Float2): Float2 = Float2(Call("floor", listOf(v.e), ShaderType.Float2))

@ExperimentalMirage
public fun sin(v: Float1): Float1 = Float1(Call("sin", listOf(v.e), ShaderType.Float1))

@ExperimentalMirage
public fun cos(v: Float3): Float3 = Float3(Call("cos", listOf(v.e), ShaderType.Float3))

@ExperimentalMirage
public fun mix(a: Float1, b: Float1, t: Float1): Float1 =
  Float1(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Float1))

@ExperimentalMirage
public fun mix(a: Float1, b: Float1, t: Float): Float1 =
  Float1(Call("mix", listOf(a.e, b.e, Literal(t)), ShaderType.Float1))

@ExperimentalMirage
public fun mix(a: Float, b: Float1, t: Float1): Float1 =
  Float1(Call("mix", listOf(Literal(a), b.e, t.e), ShaderType.Float1))

@ExperimentalMirage
public fun mix(a: Float3, b: Float3, t: Float1): Float3 =
  Float3(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Float3))

@ExperimentalMirage
public fun mix(a: Float3, b: Float3, t: Float): Float3 =
  Float3(Call("mix", listOf(a.e, b.e, Literal(t)), ShaderType.Float3))

/**
 * `boxRoundedSDF(p, halfDim, r)` — signed distance to the rounded lens box (negative inside), a
 * preamble helper (MiragePreamble.kt) every Composite/Generate lens kernel already has in scope.
 */
@ExperimentalMirage
public fun boxRoundedSDF(p: Float2, halfDim: Float2, r: Float1): Float1 =
  Float1(Call("boxRoundedSDF", listOf(p.e, halfDim.e, r.e), ShaderType.Float1))

/** `lensNormalDirection(p, halfDim, r)` — outward lens-surface direction; a preamble helper. */
@ExperimentalMirage
public fun lensNormalDirection(p: Float2, halfDim: Float2, r: Float1): Float2 =
  Float2(Call("lensNormalDirection", listOf(p.e, halfDim.e, r.e), ShaderType.Float2))

/** Rec. 709 luma weights — the same constant the hand-written Duotone kernel uses. */
@ExperimentalMirage
public fun luma(c: Half3): Half1 = Half1(Call("mirage_luma", listOf(c.e), ShaderType.Half1))

@ExperimentalMirage
public fun mix(a: Half3, b: Half3, t: Half1): Half3 =
  Half3(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Half3))

/** `mix(half3, half3, float)` — GLSL widens the `float` t, so Duotone blends by a bare `float` amount. */
@ExperimentalMirage
public fun mix(a: Half3, b: Half3, t: Float1): Half3 =
  Half3(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Half3))

@ExperimentalMirage
public fun mix(a: Half1, b: Half1, t: Half1): Half1 =
  Half1(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Half1))

/**
 * `foilHash(c)` — a bounded-input hash so `sin()` never blows up at scale, declared as a top-level
 * helper function ahead of `main` (the same shape [com.skydoves.cloudy.internal.MirageKernels]'
 * hand-written Foil kernel uses). Registers the function on the active trace the first time it is
 * called and returns the call node, so the body just writes `foilHash(cell)`.
 */
@ExperimentalMirage
public fun foilHash(c: Float2): Float1 {
  activeTrace().addHelper(
    HelperFunction(
      name = "foilHash",
      params = listOf("c" to ShaderType.Float2),
      returnType = ShaderType.Float1,
      body = emptyList(),
      returnExpr = fract(
        sin(dot(Float2(Argument("c", ShaderType.Float2)), float2(127.1f, 311.7f))) * 43758.5453f,
      ).e,
    ),
  )
  return Float1(Call("foilHash", listOf(c.e), ShaderType.Float1))
}

// --- Host-type literals: bake a Compose Color/Offset/Size into the source as a constructor call, so a
// body can write a design-token color or a geometry constant without hand-expanding its channels. ---

/**
 * A `half4` color literal from packed `0xAARRGGBB` bits, e.g. `color(0xFF1B1B3A)`.
 *
 * Unlike a `layout(color)` uniform, a literal is baked into the source and receives **no** color-space
 * conversion — its channels are emitted as-is (sRGB). For a color that must match a `layout(color)`
 * uniform's working-space value, pass it as a uniform, not a literal.
 */
@ExperimentalMirage
public fun color(argb: Long): Half4 {
  val a = ((argb shr 24) and 0xFF) / 255f
  val r = ((argb shr 16) and 0xFF) / 255f
  val g = ((argb shr 8) and 0xFF) / 255f
  val b = (argb and 0xFF) / 255f
  return Half4(
    Call("half4", listOf(Literal(r), Literal(g), Literal(b), Literal(a)), ShaderType.Half4),
  )
}

/**
 * A `half4` color literal from a Compose [Color] (e.g. an app design token). Its raw sRGB channels are
 * emitted verbatim — see [color] for the color-space note (no `layout(color)` conversion is applied).
 */
@ExperimentalMirage
public fun color(c: Color): Half4 = Half4(
  Call(
    "half4",
    listOf(Literal(c.red), Literal(c.green), Literal(c.blue), Literal(c.alpha)),
    ShaderType.Half4,
  ),
)

/** A `float2` literal from a Compose [Offset] (`float2(o.x, o.y)`). */
@ExperimentalMirage
public fun offset(o: Offset): Float2 =
  Float2(Call("float2", listOf(Literal(o.x), Literal(o.y)), ShaderType.Float2))

/** A `float2` literal from a Compose [Size] (`float2(s.width, s.height)`). */
@ExperimentalMirage
public fun size(s: Size): Float2 =
  Float2(Call("float2", listOf(Literal(s.width), Literal(s.height)), ShaderType.Float2))

/**
 * `mix(half4, color, t)` — convenience so a body can blend toward a literal [Color] without wrapping it
 * in [color] first. The literal's raw sRGB channels are baked in ([color]'s color-space note applies).
 */
@ExperimentalMirage
public fun mix(a: Half4, b: Color, t: Float1): Half4 = mix(a, color(b), t)

/**
 * `<texture>.eval(coord)` — samples an arbitrary `uniform shader` texture child (RainyWindow's
 * `wipeMask` finger-wipe mask), the texture counterpart of [sampleContent]. Position-dependent: each
 * tap stays where written (the four bilinear taps of a mask read are never CSE-merged) — see
 * [SampleTexture].
 */
@ExperimentalMirage
public fun UTexture.eval(coord: Float2): Half4 =
  Half4(SampleTexture(UniformRef(slot, ShaderType.Half4), coord.e))

// --- Builtins / operators / swizzles the RainyWindow ("Heartfelt") port needs, added one-per-line in
// the same factory style as the block above. Each mirrors an AGSL/SkSL builtin or GLSL operator the
// hand-written kernel spells out. ---

/** `mod(x, y)` — GLSL floating modulo (`x - y * floor(x/y)`), a real AGSL/SkSL builtin. */
@ExperimentalMirage
public fun mod(a: Float1, b: Float): Float1 =
  Float1(Call("mod", listOf(a.e, Literal(b)), ShaderType.Float1))

/** `float * float2` — GLSL scalar-broadcast on the left (`0.5 * res`). */
@ExperimentalMirage
public operator fun Float.times(o: Float2): Float2 =
  Float2(Binary("*", Literal(this), o.e, ShaderType.Float2))

/** `float2 - float` — GLSL scalar-broadcast subtract (`res - 0.5`). */
@ExperimentalMirage
public operator fun Float2.minus(o: Float): Float2 =
  Float2(Binary("-", e, Literal(o), ShaderType.Float2))

@ExperimentalMirage
public fun max(a: Float2, b: Float2): Float2 =
  Float2(Call("max", listOf(a.e, b.e), ShaderType.Float2))

@ExperimentalMirage
public fun min(a: Float1, b: Float): Float1 =
  Float1(Call("min", listOf(a.e, Literal(b)), ShaderType.Float1))

@ExperimentalMirage
public fun max(a: Half1, b: Float): Half1 =
  Half1(Call("max", listOf(a.e, Literal(b)), ShaderType.Half1))

/** `clamp` over `float2` with `float2` bounds (`clamp(tap, float2(0.5), res - 0.5)`). */
@ExperimentalMirage
public fun clamp(a: Float2, lo: Float2, hi: Float2): Float2 =
  Float2(Call("clamp", listOf(a.e, lo.e, hi.e), ShaderType.Float2))

/** `clamp` over `float2` with scalar bounds (`clamp(xy / res, 0.0, 1.0)`). */
@ExperimentalMirage
public fun clamp(a: Float2, lo: Float, hi: Float): Float2 =
  Float2(Call("clamp", listOf(a.e, Literal(lo), Literal(hi)), ShaderType.Float2))

/** `clamp(half1, float, float)` — the mask-read channel clamp (`clamp(float(wipeMask.eval(...).r), 0, 1)`). */
@ExperimentalMirage
public fun clamp(a: Half1, lo: Float, hi: Float): Float1 =
  Float1(Call("clamp", listOf(a.e, Literal(lo), Literal(hi)), ShaderType.Float1))

/** `mix(float, float, float1)` — bare-float endpoints with a value blend factor (`mix(3.0, 6.0, amount)`). */
@ExperimentalMirage
public fun mix(a: Float, b: Float, t: Float1): Float1 =
  Float1(Call("mix", listOf(Literal(a), Literal(b), t.e), ShaderType.Float1))

/** `half4 + half4` — the 5-tap box-blur accumulation (`sharpBg + content.eval(...) + ...`). */
@ExperimentalMirage
public operator fun Half4.plus(o: Half4): Half4 = Half4(Binary("+", e, o.e, ShaderType.Half4))

/** `mix(half4, half4, half1)` — blend by a `half` factor (`mix(sharpBg, blur, half(foggy))`). */
@ExperimentalMirage
public fun mix(a: Half4, b: Half4, t: Half1): Half4 =
  Half4(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Half4))

/** `half4 * half1` — normalizing the blur sum (`blur *= half(1.0 / 5.0)`). */
@ExperimentalMirage
public operator fun Half4.times(o: Half1): Half4 = Half4(Binary("*", e, o.e, ShaderType.Half4))

/** `half1 * half1` — `half(foggy) * half(hazeStrength)`. */
@ExperimentalMirage
public operator fun Half1.times(o: Half1): Half1 = Half1(Binary("*", e, o.e, ShaderType.Half1))

/** `half3(r, g, b)` from bare floats — the haze constant `half3(0.86, 0.90, 0.94)`. */
@ExperimentalMirage
public fun half3(r: Float, g: Float, b: Float): Half3 =
  Half3(Call("half3", listOf(Literal(r), Literal(g), Literal(b)), ShaderType.Half3))

/** `float3(x, y, z)` from three scalar expressions (`float3(p, p, p)`, the hash lane spread). */
@ExperimentalMirage
public fun float3(x: Float1, y: Float1, z: Float1): Float3 =
  Float3(Call("float3", listOf(x.e, y.e, z.e), ShaderType.Float3))

/** `half(v)` from a bare Kotlin float (`half(1.0 / 5.0)`, the box-blur normalizer). */
@ExperimentalMirage
public fun half(v: Float): Half1 = Half1(Call("half", listOf(Literal(v)), ShaderType.Half1))

// Swizzles the drop-field math reads.
@ExperimentalMirage
public val Float2.yx: Float2 get() = Float2(Swizzle(e, "yx", ShaderType.Float2))

@ExperimentalMirage
public val Float3.y: Float1 get() = Float1(Swizzle(e, "y", ShaderType.Float1))

@ExperimentalMirage
public val Float3.z: Float1 get() = Float1(Swizzle(e, "z", ShaderType.Float1))

@ExperimentalMirage
public val Float3.xy: Float2 get() = Float2(Swizzle(e, "xy", ShaderType.Float2))

@ExperimentalMirage
public val Float3.yzx: Float3 get() = Float3(Swizzle(e, "yzx", ShaderType.Float3))

/** `.r` on a `half4` — the wipe-mask alpha/red channel read. */
@ExperimentalMirage
public val Half4.r: Half1 get() = Half1(Swizzle(e, "r", ShaderType.Half1))

/** `float3 + float` scalar-broadcast (`p3.yzx + 19.19`). */
@ExperimentalMirage
public operator fun Float3.plus(o: Float): Float3 =
  Float3(Binary("+", e, Literal(o), ShaderType.Float3))

/** `float3 + float1` scalar-broadcast (`p3 += dot(...)`, adding a scalar to every lane). */
@ExperimentalMirage
public operator fun Float3.plus(o: Float1): Float3 = Float3(Binary("+", e, o.e, ShaderType.Float3))
