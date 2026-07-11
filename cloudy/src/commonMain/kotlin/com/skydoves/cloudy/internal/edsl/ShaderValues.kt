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
 * Typed user-facing wrappers around an [Expression] node. Plain classes, not value classes — see
 * [Expression]'s KDoc for why a value-class wrapper is a net loss for a polymorphic AST leaf.
 */
internal class Float1(val e: Expression)
internal class Float2(val e: Expression)
internal class Float3(val e: Expression)
internal class Float4(val e: Expression)
internal class Half1(val e: Expression)
internal class Half3(val e: Expression)
internal class Half4(val e: Expression)

/** The comparisons Foil/Specular's guards need (`sdf > SMOOTH_EDGE_PX`, `pixel.a <= 0.0`). */
internal infix fun Float1.gt(o: Float1): Expression = Comparison(">", e, o.e)
internal infix fun Half1.le(o: Float1): Expression = Comparison("<=", e, o.e)

/** `&&` — Specular's highlight gate (`edge > 0.0 && specStrength > 0.0`). */
internal infix fun Expression.and(o: Expression): Expression = Comparison("&&", this, o)

internal operator fun Float1.plus(o: Float1): Float1 = Float1(Binary("+", e, o.e, ShaderType.Float1))
internal operator fun Float1.minus(o: Float1): Float1 = Float1(Binary("-", e, o.e, ShaderType.Float1))
internal operator fun Float1.times(o: Float1): Float1 = Float1(Binary("*", e, o.e, ShaderType.Float1))
internal operator fun Float1.div(o: Float1): Float1 = Float1(Binary("/", e, o.e, ShaderType.Float1))
internal operator fun Float1.unaryMinus(): Float1 = Float1(Unary("-", e, ShaderType.Float1))

internal operator fun Float2.plus(o: Float2): Float2 = Float2(Binary("+", e, o.e, ShaderType.Float2))
internal operator fun Float2.plus(o: Float1): Float2 = Float2(Binary("+", e, o.e, ShaderType.Float2))
internal operator fun Float2.minus(o: Float2): Float2 = Float2(Binary("-", e, o.e, ShaderType.Float2))
internal operator fun Float2.times(o: Float1): Float2 = Float2(Binary("*", e, o.e, ShaderType.Float2))
internal operator fun Float2.times(o: Float2): Float2 = Float2(Binary("*", e, o.e, ShaderType.Float2))
internal operator fun Float2.div(o: Float1): Float2 = Float2(Binary("/", e, o.e, ShaderType.Float2))
internal operator fun Float2.div(o: Float2): Float2 = Float2(Binary("/", e, o.e, ShaderType.Float2))
internal operator fun Float2.unaryMinus(): Float2 = Float2(Unary("-", e, ShaderType.Float2))

internal operator fun Float3.plus(o: Float3): Float3 = Float3(Binary("+", e, o.e, ShaderType.Float3))
internal operator fun Float3.minus(o: Float1): Float3 = Float3(Binary("-", e, o.e, ShaderType.Float3))
internal operator fun Float3.times(o: Float1): Float3 = Float3(Binary("*", e, o.e, ShaderType.Float3))
internal operator fun Float1.times(o: Float3): Float3 = Float3(Binary("*", e, o.e, ShaderType.Float3))
internal operator fun Float3.times(o: Float3): Float3 = Float3(Binary("*", e, o.e, ShaderType.Float3))
internal operator fun Float3.div(o: Float1): Float3 = Float3(Binary("/", e, o.e, ShaderType.Float3))

internal operator fun Half3.plus(o: Half3): Half3 = Half3(Binary("+", e, o.e, ShaderType.Half3))
internal operator fun Half3.minus(o: Half3): Half3 = Half3(Binary("-", e, o.e, ShaderType.Half3))
internal operator fun Half3.times(o: Half3): Half3 = Half3(Binary("*", e, o.e, ShaderType.Half3))
internal operator fun Half3.times(o: Half1): Half3 = Half3(Binary("*", e, o.e, ShaderType.Half3))
internal operator fun Half1.times(o: Half3): Half3 = Half3(Binary("*", e, o.e, ShaderType.Half3))

/**
 * `half3 * float` — GLSL's implicit scalar-widening lets a `float` multiply a `half3` directly (as
 * `SPECULAR_KERNEL_AGSL`'s `(1.0 - pixel.rgb) * clamp(highlight, 0.0, 1.0)` does); the eDSL keeps the
 * operand a plain [Float1] here rather than forcing an explicit `half()` cast the source doesn't have.
 */
internal operator fun Half3.times(o: Float1): Half3 = Half3(Binary("*", e, o.e, ShaderType.Half3))

/** `half3(1.0)` — a broadcast constant, e.g. the `(1.0 - pixel.rgb)` screen-blend complement. */
internal fun half3(scalar: Float): Half3 = Half3(Call("half3", listOf(Literal(scalar)), ShaderType.Half3))

/** `.x` / `.y` on a `float2`. */
internal val Float2.x: Float1 get() = Float1(Swizzle(e, "x", ShaderType.Float1))
internal val Float2.y: Float1 get() = Float1(Swizzle(e, "y", ShaderType.Float1))

/** `.x` on a `float3` (the `p.x >= 0.0` sign-select pattern). */
internal val Float3.x: Float1 get() = Float1(Swizzle(e, "x", ShaderType.Float1))

/** `.xyz` on a `float4` (Chromatic's `chromaticKRGB.xyz` — the uniform's `.w` is declared but unused). */
internal val Float4.xyz: Float3 get() = Float3(Swizzle(e, "xyz", ShaderType.Float3))

/** `.rgb` on a `half4` narrows to `half3`; `.a` narrows to a scalar. */
internal val Half4.rgb: Half3 get() = Half3(Swizzle(e, "rgb", ShaderType.Half3))
internal val Half4.a: Half1 get() = Half1(Swizzle(e, "a", ShaderType.Half1))

internal fun float1(v: Float): Float1 = Float1(Literal(v, ShaderType.Float1))
internal fun float2(x: Float1, y: Float1): Float2 = Float2(Call("float2", listOf(x.e, y.e), ShaderType.Float2))
internal fun float2(x: Float, y: Float): Float2 = Float2(Call("float2", listOf(Literal(x), Literal(y)), ShaderType.Float2))
internal fun float3(x: Float, y: Float, z: Float): Float3 = Float3(
  Call("float3", listOf(Literal(x), Literal(y), Literal(z)), ShaderType.Float3),
)
internal fun float3(v: Float1): Float3 = Float3(Call("float3", listOf(v.e), ShaderType.Float3))
internal fun float3(xy: Float2, z: Float1): Float3 = Float3(Call("float3", listOf(xy.e, z.e), ShaderType.Float3))

/**
 * `float4(0.0, 0.0, 0.0, 0.0)` — Specular's transparent-tint argument to `processColor`. Returns the
 * raw [Expression] rather than a typed wrapper: it is only ever spliced straight into a [Call]'s
 * argument list, never bound to a `let` or read back, so a `Float4` wrapper type has no second caller.
 */
internal fun float4(x: Float, y: Float, z: Float, w: Float): Expression =
  Call("float4", listOf(Literal(x), Literal(y), Literal(z), Literal(w)), ShaderType.Half4)

internal fun half3(v: Float3): Half3 = Half3(Call("half3", listOf(v.e), ShaderType.Half3))
internal fun half(v: Float1): Half1 = Half1(Call("half", listOf(v.e), ShaderType.Half1))
internal fun half4(rgb: Half3, a: Half1): Half4 =
  Half4(Call("half4", listOf(rgb.e, a.e), ShaderType.Half4))
internal fun mix(a: Half4, b: Half4, t: Float1): Half4 = Half4(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Half4))

/** `half4(0.0)` — the all-channels-zero constructor Foil's lens-bounds early-out returns. */
internal fun half4(scalar: Float): Half4 = Half4(Call("half4", listOf(Literal(scalar)), ShaderType.Half4))

/** `sampleContent(coord)` — the `content.eval(coord)` sample point, Composite-only. */
internal fun sampleContent(coord: Float2): Half4 = Half4(SampleContent(coord.e))

/** `cond ? ifTrue : ifFalse` — Kotlin has no ternary to overload, so this is the DSL spelling of one. */
internal fun select(condition: Expression, ifTrue: Float1, ifFalse: Float1): Float1 =
  Float1(Select(condition, ifTrue.e, ifFalse.e, ShaderType.Float1))

internal infix fun Float1.ge(o: Float1): Expression = Comparison(">=", e, o.e)

/**
 * `p.x >= 0.0 ? 1.0 : -1.0` — the sign-select the superellipse bevel direction needs on each axis.
 * Shared by [traceSpecular] and [traceChromatic] (both build the same bevel field).
 */
internal fun signSelect(v: Float1): Float1 = select(v ge float1(0f), float1(1f), float1(-1f))

internal fun min(a: Float1, b: Float1): Float1 = Float1(Call("min", listOf(a.e, b.e), ShaderType.Float1))
internal fun max(a: Float1, b: Float1): Float1 = Float1(Call("max", listOf(a.e, b.e), ShaderType.Float1))
internal fun max(a: Half1, b: Half1): Half1 = Half1(Call("max", listOf(a.e, b.e), ShaderType.Half1))
internal fun exp(v: Float1): Float1 = Float1(Call("exp", listOf(v.e), ShaderType.Float1))
internal fun abs(a: Float1): Float1 = Float1(Call("abs", listOf(a.e), ShaderType.Float1))
internal fun abs(a: Float2): Float2 = Float2(Call("abs", listOf(a.e), ShaderType.Float2))
internal fun abs(a: Float3): Float3 = Float3(Call("abs", listOf(a.e), ShaderType.Float3))
internal fun clamp(a: Float1, lo: Float1, hi: Float1): Float1 = Float1(Call("clamp", listOf(a.e, lo.e, hi.e), ShaderType.Float1))
internal fun clamp(a: Float3, lo: Float1, hi: Float1): Float3 = Float3(Call("clamp", listOf(a.e, lo.e, hi.e), ShaderType.Float3))
internal fun smoothstep(lo: Float1, hi: Float1, x: Float1): Float1 =
  Float1(Call("smoothstep", listOf(lo.e, hi.e, x.e), ShaderType.Float1))
internal fun step(edge: Float1, x: Float1): Float1 = Float1(Call("step", listOf(edge.e, x.e), ShaderType.Float1))
internal fun length(v: Float2): Float1 = Float1(Call("length", listOf(v.e), ShaderType.Float1))
internal fun normalize(v: Float2): Float2 = Float2(Call("normalize", listOf(v.e), ShaderType.Float2))
internal fun normalize(v: Float3): Float3 = Float3(Call("normalize", listOf(v.e), ShaderType.Float3))
internal fun dot(a: Float2, b: Float2): Float1 = Float1(Call("dot", listOf(a.e, b.e), ShaderType.Float1))
internal fun dot(a: Float3, b: Float3): Float1 = Float1(Call("dot", listOf(a.e, b.e), ShaderType.Float1))
internal fun pow(base: Float1, exponent: Float1): Float1 = Float1(Call("pow", listOf(base.e, exponent.e), ShaderType.Float1))
internal fun sqrt(v: Float1): Float1 = Float1(Call("sqrt", listOf(v.e), ShaderType.Float1))

/**
 * `processColor(src, vibrancy, intensity, overlay)` — the preamble helper every Composite/Generate
 * kernel already has in scope (MiragePreamble.kt); Specular calls it at saturation=1/contrast=1/
 * transparent-overlay (identity, kept only for bit-parity with the hand-written kernel).
 */
internal fun processColor(src: Half3, vibrancy: Float1, intensity: Float1, overlay: Expression): Half3 =
  Half3(Call("processColor", listOf(src.e, vibrancy.e, intensity.e, overlay), ShaderType.Half3))
internal fun fract(v: Float1): Float1 = Float1(Call("fract", listOf(v.e), ShaderType.Float1))
internal fun fract(v: Float2): Float2 = Float2(Call("fract", listOf(v.e), ShaderType.Float2))
internal fun fract(v: Float3): Float3 = Float3(Call("fract", listOf(v.e), ShaderType.Float3))
internal fun floor(v: Float2): Float2 = Float2(Call("floor", listOf(v.e), ShaderType.Float2))
internal fun sin(v: Float1): Float1 = Float1(Call("sin", listOf(v.e), ShaderType.Float1))
internal fun cos(v: Float3): Float3 = Float3(Call("cos", listOf(v.e), ShaderType.Float3))
internal fun mix(a: Float1, b: Float1, t: Float1): Float1 = Float1(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Float1))
internal fun mix(a: Float3, b: Float3, t: Float1): Float3 = Float3(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Float3))

/** Rec. 709 luma weights — the same constant the hand-written Duotone kernel uses. */
internal fun luma(c: Half3): Half1 =
  Half1(Call("mirage_luma", listOf(c.e), ShaderType.Half1))

internal fun mix(a: Half3, b: Half3, t: Half1): Half3 =
  Half3(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Half3))

internal fun mix(a: Half1, b: Half1, t: Half1): Half1 =
  Half1(Call("mix", listOf(a.e, b.e, t.e), ShaderType.Half1))
