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
@file:OptIn(com.skydoves.cloudy.ExperimentalMirage::class)

package demo.optic

import com.skydoves.cloudy.CompositeOptic
import com.skydoves.cloudy.MirageParams
import com.skydoves.cloudy.Optic
import com.skydoves.cloudy.UFloat

/**
 * A brand-new optic authored entirely in the demo app to prove the open mirage API: any consumer can
 * write a composite kernel plus a [MirageParams] subclass and apply it through `Modifier.mirage { }`
 * with no library change. "Rain on a window" — a small fixed-tap blur of the background reads as wet
 * glass, and two layers of grid-hashed droplets refract the content sharply on top; `mirageTime`
 * drifts the droplets downward so the demo's [com.skydoves.cloudy.MirageClock.Auto] clock animates it.
 */
public object RainyWindowOptic {

  /** The rainy-window composite optic. Full-bleed by design — see the demo screen's framing note. */
  public val RainyWindow: CompositeOptic<RainyWindowParams> = Optic.composite(
    name = "rainyWindow",
    paramsFactory = ::RainyWindowParams,
    agsl = RAINY_WINDOW_KERNEL,
    sksl = RAINY_WINDOW_KERNEL,
  )
}

/**
 * Uniforms for [RainyWindowOptic.RainyWindow]. The property names are the shader uniform identifiers.
 *
 * @property rainAmount `0..1` droplet density — how many grid cells carry a drop. Default `0.6`.
 * @property blurRadius background blur step in px (the wet-glass softness). Default `3`.
 */
public class RainyWindowParams : MirageParams() {
  public val rainAmount: UFloat by uniform(0.6f)
  public val blurRadius: UFloat by uniform(3f)
}

/**
 * The rainy-window kernel, byte-identical AGSL/SKSL. A composite `main` (it samples `content`): a tight
 * 9-tap box blur of the background plus a 1-tap sharp refracted droplet sample (10 taps total). No
 * derivative builtins — droplet edges are shaped with `smoothstep`, so it passes the mirage lint.
 */
private const val RAINY_WINDOW_KERNEL: String = """
// Screen-space "rain on a window": a small fixed-tap blur reads as wet glass, and two layers of
// grid-hashed droplets refract the content sharply on top. mirageTime drifts the drops downward.
// rainAmount scales how many cells carry a drop; blurRadius is the background blur step in px.
float rainHash(float2 c) {
    return fract(sin(dot(c, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 xy) {
    float2 res = mirageResolution;
    float minRes = max(min(res.x, res.y), 1.0);
    float amount = clamp(rainAmount, 0.0, 1.0);

    // --- accumulate a refraction offset from 2 droplet layers (cheap hash grid, no textures) ---
    float2 offset = float2(0.0);
    float sharp = 0.0;                                      // 1 at a drop core -> sample sharp
    for (float layer = 0.0; layer < 2.0; layer += 1.0) {
        float scale = mix(9.0, 15.0, layer);               // cells across the shorter side
        float2 uv = xy / minRes * scale;
        float2 cell = floor(uv);
        cell.y -= floor(mirageTime * mix(1.4, 2.4, layer)); // gravity drift, per-layer speed
        float2 seed = cell + float2(layer * 41.0, 0.0);
        float h = rainHash(seed);
        float h2 = rainHash(seed + float2(19.0, 7.0));
        float present = step(1.0 - amount * 0.7, h);       // more drops as amount rises
        float2 center = float2(0.25 + 0.5 * h, 0.25 + 0.5 * h2);
        float2 local = fract(uv) - center;                 // vector from the drop center
        float drop = present * (1.0 - smoothstep(0.18, 0.34, length(local)));
        offset += local * drop * (minRes / scale) * 0.9;   // push sample toward the drop (lens)
        sharp = max(sharp, drop);
    }

    // --- fixed 9-tap box blur of the background (tight kernel) => wet-glass softness ---
    float2 st = float2(blurRadius) / res * minRes;         // isotropic px step
    half4 blur = content.eval(xy + float2(-st.x, -st.y))
               + content.eval(xy + float2(   0.0, -st.y))
               + content.eval(xy + float2( st.x, -st.y))
               + content.eval(xy + float2(-st.x,    0.0))
               + content.eval(xy)
               + content.eval(xy + float2( st.x,    0.0))
               + content.eval(xy + float2(-st.x,  st.y))
               + content.eval(xy + float2(   0.0,  st.y))
               + content.eval(xy + float2( st.x,  st.y));
    blur *= half(1.0 / 9.0);

    // --- droplets sample sharp + refracted (1 tap); a faint core lift reads as a wet highlight ---
    half4 sharpTap = content.eval(xy + offset);
    half k = half(clamp(sharp, 0.0, 1.0));
    half4 col = mix(blur, sharpTap, k);
    col.rgb += half3(k * 0.06);
    return col;
}
"""
