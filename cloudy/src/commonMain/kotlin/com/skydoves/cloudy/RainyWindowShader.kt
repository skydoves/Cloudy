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

package com.skydoves.cloudy

import androidx.compose.ui.graphics.TileMode
import com.skydoves.cloudy.internal.edsl.Argument
import com.skydoves.cloudy.internal.edsl.Float1
import com.skydoves.cloudy.internal.edsl.Float2
import com.skydoves.cloudy.internal.edsl.Float3
import com.skydoves.cloudy.internal.edsl.Half4
import com.skydoves.cloudy.internal.edsl.If
import com.skydoves.cloudy.internal.edsl.ShaderType
import com.skydoves.cloudy.internal.edsl.a
import com.skydoves.cloudy.internal.edsl.abs
import com.skydoves.cloudy.internal.edsl.clamp
import com.skydoves.cloudy.internal.edsl.defineHelper
import com.skydoves.cloudy.internal.edsl.div
import com.skydoves.cloudy.internal.edsl.dot
import com.skydoves.cloudy.internal.edsl.eval
import com.skydoves.cloudy.internal.edsl.float1
import com.skydoves.cloudy.internal.edsl.float2
import com.skydoves.cloudy.internal.edsl.float3
import com.skydoves.cloudy.internal.edsl.floor
import com.skydoves.cloudy.internal.edsl.fract
import com.skydoves.cloudy.internal.edsl.greaterThan
import com.skydoves.cloudy.internal.edsl.half
import com.skydoves.cloudy.internal.edsl.half3
import com.skydoves.cloudy.internal.edsl.half4
import com.skydoves.cloudy.internal.edsl.length
import com.skydoves.cloudy.internal.edsl.local
import com.skydoves.cloudy.internal.edsl.max
import com.skydoves.cloudy.internal.edsl.min
import com.skydoves.cloudy.internal.edsl.minus
import com.skydoves.cloudy.internal.edsl.mirageResolution
import com.skydoves.cloudy.internal.edsl.mirageTime
import com.skydoves.cloudy.internal.edsl.mix
import com.skydoves.cloudy.internal.edsl.mod
import com.skydoves.cloudy.internal.edsl.plus
import com.skydoves.cloudy.internal.edsl.r
import com.skydoves.cloudy.internal.edsl.rgb
import com.skydoves.cloudy.internal.edsl.sampleContent
import com.skydoves.cloudy.internal.edsl.sin
import com.skydoves.cloudy.internal.edsl.smoothstep
import com.skydoves.cloudy.internal.edsl.sqrt
import com.skydoves.cloudy.internal.edsl.times
import com.skydoves.cloudy.internal.edsl.unaryMinus
import com.skydoves.cloudy.internal.edsl.x
import com.skydoves.cloudy.internal.edsl.xy
import com.skydoves.cloudy.internal.edsl.y
import com.skydoves.cloudy.internal.edsl.yx
import com.skydoves.cloudy.internal.edsl.yzx
import com.skydoves.cloudy.internal.edsl.z

/**
 * Rain on steamed glass: running refracting raindrops + condensation you wipe clear with a finger.
 * The drop field is the "Heartfelt" shader by Martijn Steinrucken (BigWings), ported to the mirage
 * eDSL. A full-bleed [CompositeShader]: it samples the content freely and reads a [wipeMask][RainyWindowParams.wipeMask]
 * texture child for the finger-wipe fog mask.
 */
@ExperimentalMirage
public object RainyWindowOptic {
  public val RainyWindow: CompositeShader<RainyWindowParams> =
    MirageShader.composite("rainyWindow", ::RainyWindowParams) { xy ->
      val res = mirageResolution
      val amount = clamp(rainAmount, 0f, 1f)

      val uv0 = (xy - 0.5f * res) / max(res.y, 1f)
      // uv.y = -uv.y  (F4 write-swizzle workaround: rebuild flipped)
      val uv1 = float2(uv0.x, -uv0.y)
      val scale = max(dropScale, 0.2f)
      val uv = uv1 * scale

      val timeWrapped = mod(mirageTime, 120f)
      val t = timeWrapped * 0.2f

      val uvNorm = xy / max(res, float2(1f, 1f))

      // Bilinear wipe-mask read: four clamped taps (kept un-merged by SampleTexture position-dependence).
      val maskUV = clamp(xy / max(res, float2(1f, 1f)), 0f, 1f) * maskSize
      val mlo = float2(0.5f, 0.5f)
      val mhi = float2(maskSize - 0.5f, maskSize - 0.5f)
      val base = floor(maskUV - 0.5f) + 0.5f
      val f = fract(maskUV - 0.5f)
      val w00 = clamp(wipeMask.eval(clamp(base, mlo, mhi)).r, 0f, 1f)
      val w10 = clamp(wipeMask.eval(clamp(base + float2(1f, 0f), mlo, mhi)).r, 0f, 1f)
      val w01 = clamp(wipeMask.eval(clamp(base + float2(0f, 1f), mlo, mhi)).r, 0f, 1f)
      val w11 = clamp(wipeMask.eval(clamp(base + float2(1f, 1f), mlo, mhi)).r, 0f, 1f)
      val wiped = mix(mix(w00, w10, f.x), mix(w01, w11, f.x), f.y)

      val maxBlur = mix(3f, 6f, amount)
      val minBlur = float1(2f)
      val staticDrops = smoothstep(-0.5f, 1f, amount) * 2f
      val layer1 = smoothstep(0.25f, 0.75f, amount)
      val layer2 = smoothstep(0f, 0.5f, amount)

      val c = drops(uv, t, staticDrops, layer1, layer2)

      val e = float2(0.001f, 0f)
      val cx = drops(uv + e, t, staticDrops, layer1, layer2).x
      val cy = drops(uv + e.yx, t, staticDrops, layer1, layer2).x
      val n0 = float2(cx - c.x, cy - c.x)
      // n.y = -n.y
      val n = float2(n0.x, -n0.y)

      val focus0 = mix(maxBlur - c.y, minBlur, smoothstep(0.1f, 0.2f, c.x))
      val focus = mix(focus0, minBlur, clamp(wiped, 0f, 1f))
      var foggy by local(clamp((focus - minBlur) / max(maxBlur - minBlur, 1e-3f), 0f, 1f))
      foggy = foggy * clamp(fogAmount, 0f, 1f)

      val nPx0 = n * res
      val nLen = length(nPx0)
      val nPx = nPx0 * (min(nLen, 24f) / max(nLen, 1e-4f))
      val tap = clamp((uvNorm * res) + nPx, float2(0.5f, 0.5f), res - 0.5f)
      val sharpBg = sampleContent(tap)

      val st = max(blurRadius, 0f) * foggy
      val st2 = st * 2f

      var fogged by local(sharpBg)
      If(foggy greaterThan 0.01f) {
        val blur0 = sharpBg +
          sampleContent(clamp(tap + float2(-st2, -st2), float2(0.5f, 0.5f), res - 0.5f)) +
          sampleContent(clamp(tap + float2(st2, -st2), float2(0.5f, 0.5f), res - 0.5f)) +
          sampleContent(clamp(tap + float2(-st2, st2), float2(0.5f, 0.5f), res - 0.5f)) +
          sampleContent(clamp(tap + float2(st2, st2), float2(0.5f, 0.5f), res - 0.5f))
        val blur = blur0 * half(1f / 5f)
        fogged = mix(sharpBg, blur, half(foggy))
      }
      val haze = half3(0.86f, 0.90f, 0.94f)
      // fogged.rgb = mix(fogged.rgb, haze, foggy * hazeStrength)
      val foggedRgb = mix(fogged.rgb, haze, half(foggy) * half(clamp(hazeStrength, 0f, 1f)))
      val foggedFinal = half4(foggedRgb, fogged.a)

      val photo = sampleContent(clamp(xy, float2(0.5f, 0.5f), res - 0.5f))

      mix(photo, foggedFinal, half(clamp(introProgress, 0f, 1f)))
    }
}

/** Shader uniforms (property name == uniform id). [wipeMask] is the finger-wipe fog mask. */
@ExperimentalMirage
public class RainyWindowParams : MirageParams() {
  public val wipeMask: UTexture by texture(default = null, tileMode = TileMode.Clamp)
  public val maskSize: UFloat by uniform(256f)
  public val introProgress: UFloat by uniform(1f)
  public val rainAmount: UFloat by uniform(0.6f)
  public val blurRadius: UFloat by uniform(9f)
  public val fogAmount: UFloat by uniform(1f)
  public val hazeStrength: UFloat by uniform(0.55f)
  public val dropScale: UFloat by uniform(1f)
}

// --- The six "Heartfelt" helper functions, traced once and spliced ahead of main. Registration order
// is first-call order, which is dependency-first (N13/N/Saw before their callers), so the emitted GLSL
// declares every helper before it is used. ---

/** `float3 N13(float p)` — a 3-lane hash. Single-expression (no locals). */
private fun n13(p: Float1): Float3 {
  val node = defineHelper(
    name = "N13",
    params = listOf("p" to ShaderType.Float1),
    returnType = ShaderType.Float3,
    args = listOf(p.e),
  ) {
    val pArg = Float1(Argument("p", ShaderType.Float1))
    val p3a = fract(float3(pArg, pArg, pArg) * float3(0.1031f, 0.11369f, 0.13787f))
    val p3 = p3a + dot(p3a, p3a.yzx + 19.19f)
    fract(
      float3(
        (p3.x + p3.y) * p3.z,
        (p3.x + p3.z) * p3.y,
        (p3.y + p3.z) * p3.x,
      ),
    ).e
  }
  return Float3(node)
}

/** `float N(float t)`. */
private fun nHash(t: Float1): Float1 {
  val node = defineHelper(
    name = "N",
    params = listOf("t" to ShaderType.Float1),
    returnType = ShaderType.Float1,
    args = listOf(t.e),
  ) {
    val tArg = Float1(Argument("t", ShaderType.Float1))
    fract(sin(tArg * 12345.564f) * 7658.76f).e
  }
  return Float1(node)
}

/** `float Saw(float b, float t)`. */
private fun saw(b: Float1, t: Float1): Float1 {
  val node = defineHelper(
    name = "Saw",
    params = listOf("b" to ShaderType.Float1, "t" to ShaderType.Float1),
    returnType = ShaderType.Float1,
    args = listOf(b.e, t.e),
  ) {
    val bArg = Float1(Argument("b", ShaderType.Float1))
    val tArg = Float1(Argument("t", ShaderType.Float1))
    (smoothstep(float1(0f), bArg, tArg) * smoothstep(float1(1f), bArg, tArg)).e
  }
  return Float1(node)
}

/** `float2 DropLayer2(float2 uv, float t)` — the running-drop layer (statement body). */
private fun dropLayer2(uvIn: Float2, tIn: Float1): Float2 {
  val node = defineHelper(
    name = "DropLayer2",
    params = listOf("uv" to ShaderType.Float2, "t" to ShaderType.Float1),
    returnType = ShaderType.Float2,
    args = listOf(uvIn.e, tIn.e),
  ) {
    val uvArg = Float2(Argument("uv", ShaderType.Float2))
    val tArg = Float1(Argument("t", ShaderType.Float1))

    val bigUV = uvArg // float2 UV = uv;
    var uv by local(uvArg)
    uv = float2(uv.x, uv.y + tArg * 0.75f) // uv.y += t * 0.75

    val a = float2(6f, 1f)
    val grid = a * 2f
    var id by local(floor(uv * grid))
    val colShift = nHash(id.x)
    uv = float2(uv.x, uv.y + colShift) // uv.y += colShift
    id = floor(uv * grid)
    val n = n13(id.x * 35.2f + id.y * 2376.1f)
    val stv = fract(uv * grid) - float2(0.5f, 0f)
    var x by local(n.x - 0.5f)
    var y by local(bigUV.y * 20f)
    val wiggle = sin(y + sin(y))
    x = x + wiggle * (0.5f - abs(x)) * (n.z - 0.5f)
    x = x * 0.7f
    val ti = fract(tArg + n.z)
    y = (saw(float1(0.85f), ti) - 0.5f) * 0.9f + 0.5f
    // Freeze every intermediate that reads the mutable `x`/`y` here: a plain `val` captures VarRef(y),
    // which would re-read `y`'s *current* value at each inlined use — but `y` is reassigned below, so
    // these must snapshot now, exactly as the original's `float`/`float2` locals do.
    val p by local(float2(x, y))
    val d by local(length((stv - p) * a.yx))
    val mainDrop by local(smoothstep(0.4f, 0f, d))
    val r by local(sqrt(smoothstep(float1(1f), y, stv.y)))
    val cd by local(abs(stv.x - x))
    var trail by local(smoothstep(0.23f * r, 0.15f * r * r, cd))
    val trailFront by local(smoothstep(-0.02f, 0.02f, stv.y - y))
    trail = trail * trailFront * r * r
    y = bigUV.y
    val trail2 = smoothstep(0.2f * r, float1(0f), cd)
    var droplets by local(
      max(float1(0f), (sin(y * (1f - y) * 120f) - stv.y)) * trail2 * trailFront * n.z,
    )
    y = fract(y * 10f) + (stv.y - 0.5f)
    val dd = length(stv - float2(x, y))
    droplets = smoothstep(0.3f, 0f, dd)
    val m = mainDrop + droplets * r * trailFront
    float2(m, trail).e
  }
  return Float2(node)
}

/** `float StaticDrops(float2 uv, float t)` — the sparse static-drop layer (statement body). */
private fun staticDrops(uvIn: Float2, tIn: Float1): Float1 {
  val node = defineHelper(
    name = "StaticDrops",
    params = listOf("uv" to ShaderType.Float2, "t" to ShaderType.Float1),
    returnType = ShaderType.Float1,
    args = listOf(uvIn.e, tIn.e),
  ) {
    val uvArg = Float2(Argument("uv", ShaderType.Float2))
    val tArg = Float1(Argument("t", ShaderType.Float1))

    var uv by local(uvArg * 40f)
    // Freeze `id` before `uv` is reassigned below (a plain val would re-read the mutated `uv`).
    val id by local(floor(uv))
    uv = fract(uv) - 0.5f
    val n = n13(id.x * 107.45f + id.y * 3543.654f)
    val p = (n.xy - 0.5f) * 0.7f
    val d = length(uv - p)
    val fade = saw(float1(0.025f), fract(tArg + n.z))
    val c = smoothstep(0.3f, 0f, d) * fract(n.z * 10f) * fade
    c.e
  }
  return Float1(node)
}

/** `float2 Drops(float2 uv, float t, float l0, float l1, float l2)` (statement body). */
private fun drops(uvIn: Float2, tIn: Float1, l0In: Float1, l1In: Float1, l2In: Float1): Float2 {
  val node = defineHelper(
    name = "Drops",
    params = listOf(
      "uv" to ShaderType.Float2,
      "t" to ShaderType.Float1,
      "l0" to ShaderType.Float1,
      "l1" to ShaderType.Float1,
      "l2" to ShaderType.Float1,
    ),
    returnType = ShaderType.Float2,
    args = listOf(uvIn.e, tIn.e, l0In.e, l1In.e, l2In.e),
  ) {
    val uvArg = Float2(Argument("uv", ShaderType.Float2))
    val tArg = Float1(Argument("t", ShaderType.Float1))
    val l0 = Float1(Argument("l0", ShaderType.Float1))
    val l1 = Float1(Argument("l1", ShaderType.Float1))
    val l2 = Float1(Argument("l2", ShaderType.Float1))

    val s = staticDrops(uvArg, tArg) * l0
    val m1 = dropLayer2(uvArg, tArg) * l1
    val m2 = dropLayer2(uvArg * 1.85f, tArg) * l2
    var c by local(s + m1.x + m2.x)
    c = smoothstep(0.3f, 1f, c)
    float2(c, max(m1.y * l0, m2.y * l1)).e
  }
  return Float2(node)
}
