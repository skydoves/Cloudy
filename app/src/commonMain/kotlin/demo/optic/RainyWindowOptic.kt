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
 * @property rainAmount `0..1` droplet density — the probability that a grid cell carries a drop.
 *   Default `0.35` (scattered droplets, not a wall of lenses).
 * @property blurRadius background blur step in px (the wet-glass softness). Default `3`.
 */
public class RainyWindowParams : MirageParams() {
  public val rainAmount: UFloat by uniform(0.35f)
  public val blurRadius: UFloat by uniform(3f)
}

/**
 * The rainy-window kernel, byte-identical AGSL/SKSL. A composite `main` (it samples `content`): a tight
 * 9-tap box blur of the background plus a 1-tap sharp refracted droplet sample (10 taps total). No
 * derivative builtins — droplet edges are shaped with `smoothstep`, so it passes the mirage lint.
 *
 * Robustness notes (see the desktop `RainyWindowRasterTest`):
 *  - The hash is `sin`-free and every input stays in `[0, 289)`. The old kernel hashed the *drifting*
 *    cell coordinate through `sin(dot(cell, ...))`; with the gravity drift (`cell.y -= floor(time*speed)`)
 *    that argument grows without bound (~4.4e5 at t=600s, ~2.7e6 near the wrap at t=3599s), and fp32
 *    `sin()` range reduction decays on some GPUs (measured Adreno on a Galaxy S25) into near-random
 *    output — the droplet field collapsed and offsets became arbitrary. Wrapping the cell with
 *    `mod(cell, 289.0)` (a period unrelated to the grid, so no visible repeat) keeps every hash input
 *    small, so fp32 and fp16-ish paths agree on every GPU.
 *  - The refraction offset is clamped to `<= 14px`. Per-pixel `content.eval(xy + offset)` with large,
 *    scattered offsets thrashes the texture cache (DRAM-bound frames); a hard small bound keeps the
 *    reads local.
 *  - The sharp tap is clamped into `[0.5, res - 0.5]`. On Android `RuntimeShaderEffect`, `content.eval`
 *    outside the content bounds returns transparent black, which read as the dark edge smears.
 */
private const val RAINY_WINDOW_KERNEL: String = """
// Screen-space "rain on a window": a small fixed-tap blur reads as wet glass, and two layers of
// grid-hashed droplets refract the content sharply on top. mirageTime drifts the drops downward.
// rainAmount is the per-cell drop probability; blurRadius is the background blur step in px.

// sin-free hash (Dave Hoskins style, small constants). Inputs are pre-wrapped to [0, 289) by the
// caller, so |argument| stays tiny -> fp32/fp16 GPU paths all agree (no large-arg sin() decay).
float2 rainHash(float2 c) {
    float3 p = fract(float3(c.xyx) * float3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 33.33);
    return fract((p.xx + p.yz) * p.zy);
}

half4 main(float2 xy) {
    float2 res = mirageResolution;
    float minRes = max(min(res.x, res.y), 1.0);
    float amount = clamp(rainAmount, 0.0, 1.0);

    // --- accumulate a refraction offset from 2 droplet layers (cheap hash grid, no textures) ---
    float2 offset = float2(0.0);
    float sharp = 0.0;                                      // 1 at a drop core -> sample sharp
    for (float layer = 0.0; layer < 2.0; layer += 1.0) {
        float scale = mix(9.0, 13.0, layer);               // cells across the shorter side
        float2 uv = xy / minRes * scale;
        float2 cell = floor(uv);
        cell.y -= floor(mirageTime * mix(1.4, 2.4, layer)); // gravity drift, per-layer speed
        cell.x += layer * 41.0;                             // decorrelate the two layers
        cell = mod(cell, 289.0);                            // wrap BEFORE hashing -> small hash inputs
        float2 h = rainHash(cell);
        float present = step(1.0 - amount, h.x);           // per-cell drop probability = amount
        float2 center = float2(0.3 + 0.4 * h.x, 0.3 + 0.4 * h.y);
        float2 local = fract(uv) - center;                 // vector from the drop center
        float drop = present * (1.0 - smoothstep(0.10, 0.26, length(local)));
        offset += local * drop * (minRes / scale) * 0.7;   // push sample toward the drop (lens)
        sharp = max(sharp, drop);
    }

    // Hard-clamp the refraction offset so scattered taps stay cache-local (perf lever, and it caps
    // how far a lens can bend the content).
    float offLen = length(offset);
    offset *= min(offLen, 14.0) / max(offLen, 1e-4);

    // --- fixed 9-tap box blur of the background (tight kernel) => wet-glass softness ---
    // blurRadius is already in pixels; the step is isotropic in px directly (no res scaling).
    float st = max(blurRadius, 0.0);
    half4 blur = content.eval(xy + float2(-st, -st))
               + content.eval(xy + float2(0.0, -st))
               + content.eval(xy + float2( st, -st))
               + content.eval(xy + float2(-st, 0.0))
               + content.eval(xy)
               + content.eval(xy + float2( st, 0.0))
               + content.eval(xy + float2(-st,  st))
               + content.eval(xy + float2(0.0,  st))
               + content.eval(xy + float2( st,  st));
    blur *= half(1.0 / 9.0);

    // --- droplets sample sharp + refracted (1 tap); a faint core lift reads as a wet highlight ---
    // Clamp into the content bounds so an out-of-bounds eval never bleeds transparent black.
    float2 tap = clamp(xy + offset, float2(0.5), res - 0.5);
    half4 sharpTap = content.eval(tap);
    half k = half(clamp(sharp, 0.0, 1.0));
    half4 col = mix(blur, sharpTap, k);
    col.rgb += half3(k * 0.06);
    return col;
}
"""
