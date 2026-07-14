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
import com.skydoves.cloudy.RainyWindowOptic
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.MirageProgramCache
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

private const val RASTER = 64

/**
 * Raster-parity gate for [RainyWindowOptic.RainyWindow] — the "Heartfelt" rain-on-glass kernel ported
 * through the eDSL. It is the widest port so far: six helper functions (three single-expression, three
 * statement-body with mutable locals), a `mirageResolution` standard uniform, and a bilinear `wipeMask`
 * texture read via [SampleTexture] (four un-merged taps). Proves the eDSL-traced + program-cache-compiled
 * source rasterizes to within a hair of the original hand-written `RAINY_WINDOW_KERNEL` string when both
 * are bound with identical uniforms and content/mask children.
 *
 * The eDSL rewrites the kernel structurally (write-swizzle mutations become full reassignments, blur
 * accumulation reorders), so byte-identical *text* is not the gate — pixel parity is. A tiny tolerance
 * absorbs float-reassociation rounding the restructuring can introduce; it is far below any visible or
 * structural difference (a real regression moves it by orders of magnitude).
 */
internal class MirageEdslRainyWindowTest :
  FunSpec({

    test(
      "RainyWindow rasterizes like the hand-written RAINY_WINDOW_KERNEL through skiko RuntimeEffect",
    ) {
      meanAbsDiff(
        rasterize(buildEdslRainyWindowShader(), RASTER),
        rasterize(handRolledRainyWindowShader(), RASTER),
      ).shouldBeLessThanOrEqual(0.5)
    }
  })

private fun buildEdslRainyWindowShader(): Shader {
  val cached =
    MirageProgramCache.obtain(RainyWindowOptic.RainyWindow, Dialect.Sksl).shouldNotBeNull()
  return bindRainyWindowUniforms(
    RuntimeShaderBuilder(RuntimeEffect.makeForShader(cached.compiled.source)),
  )
}

/** The pre-eDSL kernel text (the `RAINY_WINDOW_KERNEL` string), the golden reference for parity. */
private fun handRolledRainyWindowShader(): Shader {
  val source = """
    uniform float2 mirageResolution;
    uniform float mirageTime;
    uniform shader content;
    uniform shader wipeMask;
    uniform float maskSize;
    uniform float introProgress;
    uniform float rainAmount;
    uniform float blurRadius;
    uniform float fogAmount;
    uniform float hazeStrength;
    uniform float dropScale;

    float3 N13(float p) {
        float3 p3 = fract(float3(p, p, p) * float3(0.1031, 0.11369, 0.13787));
        p3 += dot(p3, p3.yzx + 19.19);
        return fract(float3((p3.x + p3.y) * p3.z, (p3.x + p3.z) * p3.y, (p3.y + p3.z) * p3.x));
    }
    float N(float t) { return fract(sin(t * 12345.564) * 7658.76); }
    float Saw(float b, float t) { return smoothstep(0.0, b, t) * smoothstep(1.0, b, t); }

    float2 DropLayer2(float2 uv, float t) {
        float2 UV = uv;
        uv.y += t * 0.75;
        float2 a = float2(6.0, 1.0);
        float2 grid = a * 2.0;
        float2 id = floor(uv * grid);
        float colShift = N(id.x);
        uv.y += colShift;
        id = floor(uv * grid);
        float3 n = N13(id.x * 35.2 + id.y * 2376.1);
        float2 st = fract(uv * grid) - float2(0.5, 0.0);
        float x = n.x - 0.5;
        float y = UV.y * 20.0;
        float wiggle = sin(y + sin(y));
        x += wiggle * (0.5 - abs(x)) * (n.z - 0.5);
        x *= 0.7;
        float ti = fract(t + n.z);
        y = (Saw(0.85, ti) - 0.5) * 0.9 + 0.5;
        float2 p = float2(x, y);
        float d = length((st - p) * a.yx);
        float mainDrop = smoothstep(0.4, 0.0, d);
        float r = sqrt(smoothstep(1.0, y, st.y));
        float cd = abs(st.x - x);
        float trail = smoothstep(0.23 * r, 0.15 * r * r, cd);
        float trailFront = smoothstep(-0.02, 0.02, st.y - y);
        trail *= trailFront * r * r;
        y = UV.y;
        float trail2 = smoothstep(0.2 * r, 0.0, cd);
        float droplets = max(0.0, (sin(y * (1.0 - y) * 120.0) - st.y)) * trail2 * trailFront * n.z;
        y = fract(y * 10.0) + (st.y - 0.5);
        float dd = length(st - float2(x, y));
        droplets = smoothstep(0.3, 0.0, dd);
        float m = mainDrop + droplets * r * trailFront;
        return float2(m, trail);
    }

    float StaticDrops(float2 uv, float t) {
        uv *= 40.0;
        float2 id = floor(uv);
        uv = fract(uv) - 0.5;
        float3 n = N13(id.x * 107.45 + id.y * 3543.654);
        float2 p = (n.xy - 0.5) * 0.7;
        float d = length(uv - p);
        float fade = Saw(0.025, fract(t + n.z));
        float c = smoothstep(0.3, 0.0, d) * fract(n.z * 10.0) * fade;
        return c;
    }

    float2 Drops(float2 uv, float t, float l0, float l1, float l2) {
        float s = StaticDrops(uv, t) * l0;
        float2 m1 = DropLayer2(uv, t) * l1;
        float2 m2 = DropLayer2(uv * 1.85, t) * l2;
        float c = s + m1.x + m2.x;
        c = smoothstep(0.3, 1.0, c);
        return float2(c, max(m1.y * l0, m2.y * l1));
    }

    half4 main(float2 xy) {
        float2 res = mirageResolution;
        float amount = clamp(rainAmount, 0.0, 1.0);

        float2 UV = xy / max(res, float2(1.0));
        float2 uv = (xy - 0.5 * res) / max(res.y, 1.0);
        uv.y = -uv.y;
        float scale = max(dropScale, 0.2);
        uv *= scale;

        float T = mod(mirageTime, 120.0);
        float t = T * 0.2;

        float2 maskUV = clamp(xy / max(res, float2(1.0)), 0.0, 1.0) * maskSize;
        float2 mlo = float2(0.5);
        float2 mhi = float2(maskSize - 0.5);
        float2 base = floor(maskUV - 0.5) + 0.5;
        float2 f = fract(maskUV - 0.5);
        float w00 = clamp(float(wipeMask.eval(clamp(base,                   mlo, mhi)).r), 0.0, 1.0);
        float w10 = clamp(float(wipeMask.eval(clamp(base + float2(1.0, 0.0), mlo, mhi)).r), 0.0, 1.0);
        float w01 = clamp(float(wipeMask.eval(clamp(base + float2(0.0, 1.0), mlo, mhi)).r), 0.0, 1.0);
        float w11 = clamp(float(wipeMask.eval(clamp(base + float2(1.0, 1.0), mlo, mhi)).r), 0.0, 1.0);
        float wiped = mix(mix(w00, w10, f.x), mix(w01, w11, f.x), f.y);

        float maxBlur = mix(3.0, 6.0, amount);
        float minBlur = 2.0;
        float staticDrops = smoothstep(-0.5, 1.0, amount) * 2.0;
        float layer1 = smoothstep(0.25, 0.75, amount);
        float layer2 = smoothstep(0.0, 0.5, amount);

        float2 c = Drops(uv, t, staticDrops, layer1, layer2);

        float2 e = float2(0.001, 0.0);
        float cx = Drops(uv + e,    t, staticDrops, layer1, layer2).x;
        float cy = Drops(uv + e.yx, t, staticDrops, layer1, layer2).x;
        float2 n = float2(cx - c.x, cy - c.x);
        n.y = -n.y;

        float focus = mix(maxBlur - c.y, minBlur, smoothstep(0.1, 0.2, c.x));
        focus = mix(focus, minBlur, clamp(wiped, 0.0, 1.0));
        float foggy = clamp((focus - minBlur) / max(maxBlur - minBlur, 1e-3), 0.0, 1.0);
        foggy *= clamp(fogAmount, 0.0, 1.0);

        float2 nPx = n * res;
        float nLen = length(nPx);
        nPx *= min(nLen, 24.0) / max(nLen, 1e-4);
        float2 tap = clamp((UV * res) + nPx, float2(0.5), res - 0.5);
        half4 sharpBg = content.eval(tap);

        float st = max(blurRadius, 0.0) * foggy;
        float st2 = st * 2.0;

        half4 fogged = sharpBg;
        if (foggy > 0.01) {
            half4 blur = sharpBg
                       + content.eval(clamp(tap + float2(-st2, -st2), float2(0.5), res - 0.5))
                       + content.eval(clamp(tap + float2( st2, -st2), float2(0.5), res - 0.5))
                       + content.eval(clamp(tap + float2(-st2,  st2), float2(0.5), res - 0.5))
                       + content.eval(clamp(tap + float2( st2,  st2), float2(0.5), res - 0.5));
            blur *= half(1.0 / 5.0);
            fogged = mix(sharpBg, blur, half(foggy));
        }
        half3 haze = half3(0.86, 0.90, 0.94);
        fogged.rgb = mix(fogged.rgb, haze, half(foggy) * half(clamp(hazeStrength, 0.0, 1.0)));

        half4 photo = content.eval(clamp(xy, float2(0.5), res - 0.5));

        half4 col = mix(photo, fogged, half(clamp(introProgress, 0.0, 1.0)));
        return col;
    }
  """.trimIndent()
  return bindRainyWindowUniforms(RuntimeShaderBuilder(RuntimeEffect.makeForShader(source)))
}

/** Binds both shaders under test at one look with a full-bleed lens covering the raster. */
private fun bindRainyWindowUniforms(builder: RuntimeShaderBuilder): Shader {
  builder.uniform("mirageResolution", RASTER.toFloat(), RASTER.toFloat())
  builder.uniform("mirageTime", 1.75f)
  builder.uniform("maskSize", 256f)
  builder.uniform("introProgress", 1f)
  builder.uniform("rainAmount", 0.6f)
  builder.uniform("blurRadius", 9f)
  builder.uniform("fogAmount", 1f)
  builder.uniform("hazeStrength", 0.55f)
  builder.uniform("dropScale", 1f)
  builder.child("content", contentShader())
  builder.child("wipeMask", wipeMaskShader())
  return builder.makeShader()
}

private fun contentShader(): Shader = RuntimeEffect.makeForShader(
  """
  half4 main(float2 xy) {
    float2 uv = xy / float2($RASTER.0, $RASTER.0);
    return half4(half(uv.x), half(uv.y), half(1.0 - uv.x), 1.0);
  }
  """.trimIndent(),
).let { RuntimeShaderBuilder(it).makeShader() }

/** A partial finger-wipe mask: cleared (r=1) over a diagonal band, fogged (r=0) elsewhere. */
private fun wipeMaskShader(): Shader = RuntimeEffect.makeForShader(
  """
  half4 main(float2 xy) {
    float v = step(64.0, xy.x + xy.y) * step(xy.x + xy.y, 320.0);
    return half4(half(v), half(v), half(v), 1.0);
  }
  """.trimIndent(),
).let { RuntimeShaderBuilder(it).makeShader() }
