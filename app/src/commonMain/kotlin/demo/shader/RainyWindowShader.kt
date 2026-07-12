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

package demo.shader

import androidx.compose.ui.graphics.TileMode
import com.skydoves.cloudy.CompositeShader
import com.skydoves.cloudy.MirageParams
import com.skydoves.cloudy.MirageShader
import com.skydoves.cloudy.UFloat
import com.skydoves.cloudy.UTexture

/**
 * A demo-authored shader that proves the open mirage API: any consumer can author a composite kernel
 * plus a [MirageParams] subclass — here even a *texture-backed* one — and apply it through
 * `Modifier.mirage { }` with no library change. "Rain on a window": a light blur of the background
 * reads as misted glass, wiped sharp where droplets sit, and each droplet refracts a warped, brighter
 * view of what is behind it (the paraboloid-lens "inverted world in the drop"). `mirageTime` drives a
 * per-drop appear/sit/fade life cycle so the field slowly evolves under [com.skydoves.cloudy.MirageClock.Auto].
 *
 * ### Architecture: static droplet maps + a cheap animating shader
 * Rather than hashing and shaping a drop per grid cell every frame, the drop shapes are baked **once**
 * into a tileable RGBA texture ([DropletMap]) and the shader only animates and refracts. Each texel
 * carries the drop's surface normal (`R,G`), coverage/height (`A`), and a per-drop random phase (`B`);
 * see [DropletMap] for the full channel encoding and tileability notes. The kernel takes a single
 * texture tap and never hashes — the texture *is* the randomness.
 *
 * ### Technique & attribution (clean-room, independent implementation)
 * The static-droplet-map approach (offline normal + height + per-drop-phase maps; the shader remaps
 * normals into a small bounded UV/pixel offset to refract the background; an alpha-erosion life cycle
 * driven by the per-drop phase so drops appear and fade without per-drop animation) is described in
 * prose by the author of the "Rainy Glass Shader" at https://www.toadstorm.com/blog/?p=742. That
 * shader is LGPL-2.1; **no code or asset from it was copied.** Only the *technique* (algorithms are not
 * copyrightable) was reimplemented from scratch for this Apache-2.0 repo: the droplet generator, the
 * channel encoding, the smoothstep erosion pulse, and the fog-wipe math below are all original and are
 * deliberately GPU-robust (see the robustness notes and the desktop `RainyWindowRasterTest`).
 */
public object RainyWindowShader {

  /** The rainy-window composite shader. Full-bleed by design — see the demo screen's framing note. */
  public val RainyWindow: CompositeShader<RainyWindowParams> = MirageShader.composite(
    name = "rainyWindow",
    paramsFactory = ::RainyWindowParams,
    agsl = RAINY_WINDOW_KERNEL,
    sksl = RAINY_WINDOW_KERNEL,
  )
}

/**
 * Uniforms for [RainyWindowShader.RainyWindow]. The property names are the shader uniform identifiers.
 *
 * @property dropletMap the baked, tileable droplet texture (REPEAT tile mode). The demo remembers one
 *   [DropletMap.generate] result and binds it every draw. It has no useful default (a null texture is
 *   simply not bound by the mirage binder, leaving `dropletMap.eval` reading an empty child), so the
 *   demo must always set it; the kernel additionally degrades gracefully if coverage reads as zero.
 * @property rainAmount `0..1` droplet density — drives the coverage threshold, so a low value shows
 *   only the largest/most-covered drop cores and a high value reveals the fine mist too. Default `0.35`.
 * @property blurRadius background fog blur step in px. Kept small so the misted glass stays light and
 *   the drops read; heavy fog turns muddy. Default `1.6`; further wiped sharp near drops.
 * @property dropScale texture repeats across the screen; larger `dropScale` = smaller drops on screen
 *   (more repeats). Default `1.6` (a comfortable drop size on a phone pane).
 */
public class RainyWindowParams : MirageParams() {
  public val dropletMap: UTexture by texture(default = null, tileMode = TileMode.Repeated)
  public val rainAmount: UFloat by uniform(0.35f)
  public val blurRadius: UFloat by uniform(1.6f)
  public val dropScale: UFloat by uniform(1.6f)
}

/**
 * The rainy-window kernel, byte-identical AGSL/SKSL. A composite `main` (it samples `content`): a light
 * 9-tap box blur of the background (the center tap is reused as the sharp source, so **10 `content.eval`
 * total** — 9 blur + 1 refracted) plus one `dropletMap.eval` texture tap (a child *shader* read, not a
 * content read, so it does not count against the content budget). No derivative builtins — every edge
 * is a `smoothstep`, so it passes the mirage lint.
 *
 * ### How the texture drives the look
 * One tap `d = dropletMap.eval(mod(xy * dropScale, SIZE))` decodes to:
 *  - **normal** `n = (d.rg - 0.5) * 2` — the paraboloid surface slope, outward from each drop center;
 *  - **coverage** `a = d.a` — 1 at a drop apex fading to 0 at its rim, 0 outside drops;
 *  - **phase** `d.b` — a per-drop random constant used only as a time offset.
 *
 * Refraction pushes the content sample along the normal, scaled by coverage (rims bend less than the
 * belly) and by a small `refractPx` budget: `content.eval(xy + n * refractPx * a)`. That outward push
 * on a paraboloid is what shows the inverted, brighter world inside the drop.
 *
 * The life cycle is texture-native (no hashing): `life = fract(mirageTime * speed + d.b)` runs each
 * drop through its own 0..1 loop; a smoothstep envelope makes it fade in, sit, then erode away, so the
 * field evolves without per-drop animation and without popping. `rainAmount` sets a coverage threshold
 * so the slider scales how much of the mist is visible.
 *
 * Robustness notes (see `RainyWindowRasterTest`) — GPU-portability constraints the kernel keeps:
 *  - **No large-argument sin/hash.** fp32 `sin()` range reduction decays on some GPUs (notably Adreno)
 *    once its argument grows large, so hashing a time-drifting coordinate through `sin(dot(...))` would
 *    collapse the field at long run times. The texture removes all hashing; the only time math is
 *    `fract(mirageTime * speed + phase)`, whose product stays small, so fp32/fp16 paths agree on every GPU.
 *  - **Bounded refraction.** The offset is clamped to `<= 14px`; scattered large `content.eval` offsets
 *    thrash the texture cache (DRAM-bound frames), so a hard small bound keeps reads local.
 *  - **Clamped taps.** Every `content.eval` coord is clamped into `[0.5, res - 0.5]`; on Android
 *    `RuntimeShaderEffect`, an out-of-bounds content read returns transparent black (dark edge smears).
 */
private const val RAINY_WINDOW_KERNEL: String = """
// Texture-backed "rain on a window": a baked droplet map (normals in R,G; coverage in A; per-drop
// phase in B) is sampled once; the shader refracts the content through each drop's paraboloid normal
// and runs a per-drop appear/sit/fade life cycle off mirageTime. A light blur is the misted glass,
// wiped sharp at the drops. rainAmount scales drop visibility; blurRadius is the (small) fog step.

half4 main(float2 xy) {
    float2 res = mirageResolution;
    float amount = clamp(rainAmount, 0.0, 1.0);

    // --- one texture tap: decode normal (R,G), coverage (A), per-drop phase (B) ---
    // The map is 512x512 and REPEAT-tiled; mod keeps the sample in-range so Android BitmapShader REPEAT
    // and skiko FilterTileMode.REPEAT agree exactly (both tile in texel space).
    float2 mapUV = mod(xy * max(dropScale, 0.01), 512.0);
    half4 d = dropletMap.eval(mapUV);
    float2 n = (float2(d.rg) - 0.5) * 2.0;   // outward paraboloid normal
    float cov = float(d.a);                  // coverage/height: 1 at apex -> 0 at rim/outside
    float phase = float(d.b);                // per-drop random constant

    // --- per-drop life cycle (texture-native erosion, hash-free) ---
    // Each drop loops on its own phase; fade in fast, sit, then erode out near the end of the loop.
    float life = fract(mirageTime * 0.06 + phase);
    float appear = smoothstep(0.0, 0.12, life);
    float fade = 1.0 - smoothstep(0.72, 1.0, life);
    float env = appear * fade;

    // rainAmount as a coverage threshold: low amount keeps only the strongest cores, high reveals mist.
    float covThresh = mix(0.55, 0.05, amount);
    float present = smoothstep(covThresh, covThresh + 0.12, cov);
    float dropVis = present * env;           // 0..1 how "on" this drop is (mask * life envelope)

    // --- refraction: push the sample along the drop normal at the FULL px budget across the whole
    //     drop (gated by the drop mask, NOT by coverage) so the drop body acts as a lens and shows the
    //     inverted world inside it, rather than only a thin refracting rim. Hard-bounded to 14px. ---
    float2 offset = n * (14.0 * dropVis);
    float offLen = length(offset);
    offset *= min(offLen, 14.0) / max(offLen, 1e-4);

    // --- light 9-tap box blur of the background (misted glass). Keep the CENTER tap separate so it
    //     doubles as the sharp source for the fog wipe (no extra content.eval). ---
    float st = max(blurRadius, 0.0);
    half4 sharpBg = content.eval(clamp(xy, float2(0.5), res - 0.5));
    half4 blur = sharpBg
               + content.eval(clamp(xy + float2(-st, -st), float2(0.5), res - 0.5))
               + content.eval(clamp(xy + float2(0.0, -st), float2(0.5), res - 0.5))
               + content.eval(clamp(xy + float2( st, -st), float2(0.5), res - 0.5))
               + content.eval(clamp(xy + float2(-st, 0.0), float2(0.5), res - 0.5))
               + content.eval(clamp(xy + float2( st, 0.0), float2(0.5), res - 0.5))
               + content.eval(clamp(xy + float2(-st,  st), float2(0.5), res - 0.5))
               + content.eval(clamp(xy + float2(0.0,  st), float2(0.5), res - 0.5))
               + content.eval(clamp(xy + float2( st,  st), float2(0.5), res - 0.5));
    blur *= half(1.0 / 9.0);

    // --- fog wipe: mist everywhere, wiped to sharp where drops sit (the wet-glass contrast) ---
    half fogAmount = half(0.60);
    half w = half(clamp(dropVis, 0.0, 1.0));
    half fog = fogAmount * (half(1.0) - w);
    half4 fogged = mix(sharpBg, blur, fog);

    // --- drop body samples sharp + refracted (1 tap); a rim-weighted highlight reads as the wet
    //     meniscus catching the light, and a small core lift keeps the drop from looking flat. ---
    float2 tap = clamp(xy + offset, float2(0.5), res - 0.5);
    half4 refracted = content.eval(tap);
    half k = half(clamp(dropVis, 0.0, 1.0));
    half4 col = mix(fogged, refracted, k);
    float rim = length(n);                              // strongest mid-drop (the meniscus band)
    col.rgb += half3(half(rim * dropVis) * half(0.14)); // wet edge highlight
    col.rgb += half3(k * 0.05);                         // faint overall lift inside the drop
    return col;
}
"""
