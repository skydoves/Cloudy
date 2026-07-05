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
 * with no library change. "Rain on a window" — a light blur of the background reads as slightly misted
 * glass that is *wiped sharp* wherever a raindrop or its slide-trail sits (the wet-glass contrast), and
 * grid-hashed teardrop drops refract the content sharply on top; `mirageTime` drifts the drops downward
 * so the demo's [com.skydoves.cloudy.MirageClock.Auto] clock slowly evolves the field.
 *
 * ### Technique & attribution (clean-room)
 * This is an independent implementation of the well-documented single-pass "rain on a window" grid
 * technique. The general approach (one hashed candidate drop per grid cell, a per-drop refraction offset,
 * and a fog that is wiped near drops) is described at https://greentec.github.io/rain-drops-en/ and the
 * MIT-licensed shape/normal reference https://github.com/SardineFish/raindrop-fx. Only the *technique*
 * (algorithms are not copyrightable) was taken from those; **no shader code was copied** from the
 * canonical CC BY-NC-SA ShaderToy rain shaders (BigWings "Heartfelt", eliemichel "Rain drops on
 * screen"). The teardrop silhouette, sine wiggle, trail column, and fog-wipe math below are written from
 * scratch for this Apache-2.0 repo and are deliberately sin-argument-bounded for GPU robustness.
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
 * @property blurRadius background fog blur step in px. Deliberately small — the misted glass must stay
 *   light so the drops read (S25 feedback: the old `3` px fog was too muddy). Default `1.6`; the fog is
 *   further wiped to sharp near drops and trails, so the perceived blur is lower still.
 */
public class RainyWindowParams : MirageParams() {
  public val rainAmount: UFloat by uniform(0.35f)
  public val blurRadius: UFloat by uniform(1.6f)
}

/**
 * The rainy-window kernel, byte-identical AGSL/SKSL. A composite `main` (it samples `content`): a light
 * 9-tap box blur of the background (reused center = the sharp source) plus one sharp refracted drop tap,
 * so **10 `content.eval` total** (9 blur + 1 refracted; the center blur tap is reused as the sharp
 * source for the fog wipe, no extra tap). No derivative builtins — every edge is shaped with
 * `smoothstep`, so it passes the mirage lint.
 *
 * ### Shape math (clean-room single-pass grid technique)
 * Per grid cell we place one candidate teardrop and its slide-trail, all derived from the same small
 * hash so the whole silhouette is coherent. In cell-local coordinates (`local = fract(uv) - center`,
 * `local.y` grows downward like screen space):
 *  - **Teardrop:** `x` is wiggled by `y` with a small-argument sine (`x += WIG * sin(WIG_K * local.y)`,
 *    the classic sine-squish), then a signed ellipse is evaluated whose **half-width tapers to a point
 *    going up** (`halfW *= 1 - smoothstep(0, TAIL, up)`, `up = max(0, -local.y)`) and whose **vertical
 *    reach is longer upward than downward** (`hy = up>0 ? H_TAIL : H_BELLY`). That yields a round belly
 *    at the bottom and a narrowing tail pointing up — a real raindrop, not a bokeh circle.
 *  - **Trail:** three shrinking round droplets stacked *above* the belly (`cy = -T0 - T_STEP*i`,
 *    radius shrinks with `i`), the track the drop left as it slid down. Their x is jittered by the cell
 *    hash. Static per cell — the field only drifts slowly with `mirageTime`; no per-frame animation is
 *    needed (S25 feedback explicitly accepted a static/slow field).
 *  - **Fog wipe:** the (light) blur is mixed toward the sharp center tap by `fog * (1 - wipe)`, where
 *    `wipe` peaks over drops and trails. Sharp glass at the drops against misted glass around them is
 *    what reads as *wet* glass; the low `blurRadius` default keeps the mist subtle (S25: old fog muddy).
 *  - **Refraction:** the drop mask pushes the sample toward the drop center (a lens); trails refract
 *    faintly. A small core lift is the wet highlight.
 *
 * Robustness notes (see the desktop `RainyWindowRasterTest`) — these are lessons already paid for on
 * this branch and must not regress:
 *  - The hash is `sin`-free and every input stays in `[0, 289)`. The old kernel hashed the *drifting*
 *    cell coordinate through `sin(dot(cell, ...))`; with the gravity drift (`cell.y -= floor(time*speed)`)
 *    that argument grows without bound (~4.4e5 at t=600s, ~2.7e6 near the wrap at t=3599s), and fp32
 *    `sin()` range reduction decays on some GPUs (measured Adreno on a Galaxy S25) into near-random
 *    output — the droplet field collapsed and offsets became arbitrary. Wrapping the cell with
 *    `mod(cell, 289.0)` (a period unrelated to the grid, so no visible repeat) keeps every hash input
 *    small, so fp32 and fp16-ish paths agree on every GPU. The shape sines take only `local.y`
 *    (in `[-0.7, 0.7]`) as an argument, so they stay exact on every GPU too.
 *  - The refraction offset is clamped to `<= 14px`. Per-pixel `content.eval(xy + offset)` with large,
 *    scattered offsets thrashes the texture cache (DRAM-bound frames); a hard small bound keeps the
 *    reads local.
 *  - The sharp/refracted taps are clamped into `[0.5, res - 0.5]`. On Android `RuntimeShaderEffect`,
 *    `content.eval` outside the content bounds returns transparent black, which read as dark edge smears.
 */
private const val RAINY_WINDOW_KERNEL: String = """
// Screen-space "rain on a window": a light blur is the misted glass, wiped sharp at grid-hashed
// teardrop drops + their slide-trails, which also refract the content. mirageTime drifts the field.
// rainAmount is the per-cell drop probability; blurRadius is the (small) background fog step in px.

// sin-free hash (Dave Hoskins style, small constants). Inputs are pre-wrapped to [0, 289) by the
// caller, so |argument| stays tiny -> fp32/fp16 GPU paths all agree (no large-arg sin() decay).
float2 rainHash(float2 c) {
    float3 p = fract(float3(c.xyx) * float3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 33.33);
    return fract((p.xx + p.yz) * p.zy);
}

// Teardrop mask in cell-local coords. localY grows downward; belly at bottom, tail points up.
// Returns 1 inside the drop core, 0 outside, soft edge via smoothstep. The only sine arg is localY.
float teardropMask(float2 local, float halfW) {
    float x = local.x + 0.05 * sin(2.2 * local.y);   // sine-squish: x wiggles with y (small arg)
    float up = max(0.0, -local.y);                   // >0 above center (the tail side)
    float hw = halfW * (1.0 - smoothstep(0.0, 0.30, up)); // taper width to a point going up
    hw = max(hw, 0.006);
    float hy = (local.y < 0.0) ? 0.30 : 0.18;        // reach further up (tail) than down (belly)
    float nx = x / hw;
    float ny = local.y / hy;
    return 1.0 - smoothstep(0.72, 1.0, sqrt(nx * nx + ny * ny));
}

// Slide-trail: 3 shrinking round droplets stacked above the belly (the track the drop left).
float trailMask(float2 local, float2 h) {
    float acc = 0.0;
    float cx = (h.x - 0.5) * 0.10;                   // per-cell x jitter of the trail column
    for (float i = 1.0; i < 4.0; i += 1.0) {
        float2 c = float2(cx, -0.16 - 0.15 * i);     // each dot sits further up
        float r = 0.055 * (1.0 - 0.16 * i);          // and shrinks
        acc = max(acc, 1.0 - smoothstep(0.4 * r, r, length(local - c)));
    }
    return acc;
}

half4 main(float2 xy) {
    float2 res = mirageResolution;
    float minRes = max(min(res.x, res.y), 1.0);
    float amount = clamp(rainAmount, 0.0, 1.0);

    // --- accumulate refraction + a wipe mask from 2 droplet layers (cheap hash grid, no textures) ---
    float2 offset = float2(0.0);
    float dropM = 0.0;                                  // teardrop core coverage (sharp + strong lens)
    float wipe = 0.0;                                   // fog-wipe coverage (drops + trails)
    for (float layer = 0.0; layer < 2.0; layer += 1.0) {
        float scale = mix(9.0, 13.0, layer);           // cells across the shorter side
        float2 uv = xy / minRes * scale;
        float2 cell = floor(uv);
        cell.y -= floor(mirageTime * mix(0.9, 1.5, layer)); // slow gravity drift, per-layer speed
        cell.x += layer * 41.0;                         // decorrelate the two layers
        cell = mod(cell, 289.0);                        // wrap BEFORE hashing -> small hash inputs
        float2 h = rainHash(cell);
        float present = step(1.0 - amount, h.x);        // per-cell drop probability = amount
        float2 center = float2(0.35 + 0.30 * h.x, 0.42 + 0.24 * h.y);
        float2 local = fract(uv) - center;              // vector from the drop center
        float halfW = mix(0.13, 0.17, h.y);             // per-cell drop width
        float drop = present * teardropMask(local, halfW);
        float trail = present * trailMask(local, h) * 0.7;
        offset += local * drop * (minRes / scale) * 0.55;  // teardrop pushes the sample (lens)
        offset += local * trail * (minRes / scale) * 0.18; // trails refract faintly
        dropM = max(dropM, drop);
        wipe = max(wipe, max(drop, trail));
    }

    // Hard-clamp the refraction offset so scattered taps stay cache-local (perf lever, and it caps
    // how far a lens can bend the content).
    float offLen = length(offset);
    offset *= min(offLen, 14.0) / max(offLen, 1e-4);

    // --- light 9-tap box blur of the background (the misted glass). Keep the CENTER tap separate so
    //     it doubles as the sharp source for the fog wipe (no extra content.eval). ---
    // blurRadius is already in pixels; the step is isotropic in px directly (no res scaling).
    float st = max(blurRadius, 0.0);
    half4 sharpBg = content.eval(xy);                  // center tap, reused as the sharp source
    half4 blur = sharpBg
               + content.eval(xy + float2(-st, -st))
               + content.eval(xy + float2(0.0, -st))
               + content.eval(xy + float2( st, -st))
               + content.eval(xy + float2(-st, 0.0))
               + content.eval(xy + float2( st, 0.0))
               + content.eval(xy + float2(-st,  st))
               + content.eval(xy + float2(0.0,  st))
               + content.eval(xy + float2( st,  st));
    blur *= half(1.0 / 9.0);

    // --- fog wipe: mist everywhere, wiped to sharp where drops/trails sit (the wet-glass contrast) ---
    half fogAmount = half(0.60);                        // ceiling of how misted the plain glass reads
    half w = half(clamp(wipe, 0.0, 1.0));
    half fog = fogAmount * (half(1.0) - w);
    half4 fogged = mix(sharpBg, blur, fog);

    // --- drop cores sample sharp + refracted (1 tap); a faint core lift reads as a wet highlight ---
    // Clamp into the content bounds so an out-of-bounds eval never bleeds transparent black.
    float2 tap = clamp(xy + offset, float2(0.5), res - 0.5);
    half4 refracted = content.eval(tap);
    half k = half(clamp(dropM, 0.0, 1.0));
    half4 col = mix(fogged, refracted, k);
    col.rgb += half3(k * 0.06);
    return col;
}
"""
