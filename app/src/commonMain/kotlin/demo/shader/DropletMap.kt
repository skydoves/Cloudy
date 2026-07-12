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
package demo.shader

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.sqrt

/**
 * Offline generator for the rainy-window droplet map: a single tileable RGBA texture that bakes every
 * droplet's shape, so the shader only animates and refracts (it never hashes or shapes a drop at draw
 * time). This is the texture-map half of the well-documented "static droplet maps + cheap animating
 * shader" architecture; see [RainyWindowShader] for the technique attribution and the clean-room note.
 *
 * ### Channel encoding (ARGB8888, non-premultiplied sRGB — the [ImageBitmap.readPixels] contract)
 * Each pixel packs four independent fields the kernel decodes:
 *  - **R, G = surface normal .xy** of the droplet's paraboloid cap, encoded `0.5 + 0.5 * n` so
 *    `0.5` is flat (the default outside every drop). The kernel reads it back as `(rg - 0.5) * 2`.
 *    A paraboloid cap `z = 1 - (r / R)^2` has an outward-pointing surface slope that grows with the
 *    normalized radius `u = r / R`; that outward normal is exactly what bends the background outward
 *    to form the "inverted world inside the drop" lens.
 *  - **A = height / coverage**: `1` at the apex fading to `0` at the rim, and `0` everywhere outside a
 *    drop. The kernel uses it both as the refraction strength (edges bend less than the belly) and as
 *    the erosion gradient for the appear/fade life cycle.
 *  - **B = per-drop random phase**: one uniform random value per drop, constant across all of that
 *    drop's pixels. The kernel adds it to `mirageTime` so each drop appears/fades on its own schedule
 *    (the texture *is* the randomness — no shader hashing).
 *
 * ### Depth trick (drop scales)
 * Drops are scattered at three scales — a few large caps, more medium, and many small "mist" specks.
 * Smaller drops are written dimmer (their coverage/normal amplitude is scaled down), so they refract
 * less and read as further from the glass, the same cheap depth cue the reference technique uses.
 *
 * ### Tileability
 * The texture is sampled with a REPEAT tile mode, so it must wrap seamlessly. Every drop is stamped
 * with wrap-around writes (pixel coordinates are taken `mod size`), so a drop whose disc crosses an
 * edge reappears on the opposite edge and the tile repeats with no visible seam.
 *
 * ### commonMain strategy
 * commonMain's [ImageBitmap] exposes only `readPixels` (no per-pixel write), and per-pixel writing is
 * what gives exact paraboloid normals (a `Canvas` radial gradient can only interpolate colors, not the
 * `x/r, y/r` normal field). So all droplet math lives here in commonMain producing an ARGB `IntArray`,
 * and the one platform primitive — turning that array into an [ImageBitmap] — is the tiny
 * [argbToImageBitmap] expect/actual (Android `Bitmap.setPixels`; skiko `Bitmap.installPixels`).
 */
internal object DropletMap {

  /** Texture edge length in pixels. A power of two keeps GPU tiling/mip paths happy. */
  const val SIZE: Int = 512

  /**
   * Builds the droplet map once. Deterministic: the same [seed] always yields the same texture, so the
   * remembered bitmap is stable across recompositions and process restarts (and the raster test can
   * reproduce a matching field). Call inside `remember { }` — this is a one-shot offline bake.
   */
  fun generate(seed: Int = 0x5EED): ImageBitmap {
    val size = SIZE
    val pixels = IntArray(size * size)

    // Base surface: flat normal (0.5, 0.5), zero coverage, zero phase. Alpha byte stays the coverage
    // field; the texture is opaque-by-encoding only in that A carries data, not transparency.
    val flat = argb(a = 0, r = 128, g = 128, b = 0)
    pixels.fill(flat)

    val rng = Rng(seed)

    // Three scales (radius px range, count, dimming). Larger drops are brightest/most refractive;
    // the small "mist" specks are dim so they read as further back (the depth cue).
    stampLayer(pixels, size, rng, count = 7, minR = 40f, maxR = 70f, dim = 1.0f)
    stampLayer(pixels, size, rng, count = 18, minR = 15f, maxR = 28f, dim = 0.72f)
    stampLayer(pixels, size, rng, count = 55, minR = 4f, maxR = 9f, dim = 0.42f)

    return argbToImageBitmap(size, size, pixels)
  }

  /** Scatters [count] paraboloid drops of radius in `[minR, maxR]`, each dimmed by [dim]. */
  private fun stampLayer(
    pixels: IntArray,
    size: Int,
    rng: Rng,
    count: Int,
    minR: Float,
    maxR: Float,
    dim: Float,
  ) {
    for (i in 0 until count) {
      val cx = rng.nextFloat() * size
      val cy = rng.nextFloat() * size
      val radius = minR + rng.nextFloat() * (maxR - minR)
      val phase = rng.nextFloat() // per-drop random phase -> B channel, constant across the drop
      stampDrop(pixels, size, cx, cy, radius, phase, dim)
    }
  }

  /**
   * Stamps one paraboloid drop centered at ([cx], [cy]) with wrap-around writes so it tiles seamlessly.
   * "Higher" pixels (nearer the apex, larger coverage) win where drops overlap, so a big drop is not
   * eaten by a small one it covers.
   */
  private fun stampDrop(
    pixels: IntArray,
    size: Int,
    cx: Float,
    cy: Float,
    radius: Float,
    phase: Float,
    dim: Float,
  ) {
    val r0 = radius.toInt() + 1
    val minX = (cx - r0).toInt()
    val maxX = (cx + r0).toInt()
    val minY = (cy - r0).toInt()
    val maxY = (cy + r0).toInt()
    val invR = 1f / radius

    for (py in minY..maxY) {
      for (px in minX..maxX) {
        val dx = px + 0.5f - cx
        val dy = py + 0.5f - cy
        val dist = sqrt(dx * dx + dy * dy)
        val u = dist * invR // normalized radius 0 (apex) .. 1 (rim)
        if (u >= 1f) continue

        // Coverage (height) is near-full across the belly and eases to 0 at the rim, so a drop reads as
        // a solid wet lens rather than a faint ring. dim pushes the small "mist" drops back.
        val belly = 1f - u * u * u * u // flatter top than a plain paraboloid -> a fuller lens body
        val coverage = (belly * smoothEdge(u)) * dim
        if (coverage <= 0.002f) continue

        // Surface normal .xy points outward from the center. A real drop-lens bends most across its
        // mid-body, so shape the slope as a smooth hump (0 at the apex, strong mid-drop, easing at the
        // rim) and encode it near full-scale — this is what makes the whole drop refract, not just a
        // thin edge ring. The magnitude is intentionally large; the kernel's refractPx budget bounds it.
        val slope = (4f * u * (1f - u)) // parabola peaking at u = 0.5, in [0, 1]
        val nx = if (dist > 1e-4f) (dx / dist) * slope else 0f
        val ny = if (dist > 1e-4f) (dy / dist) * slope else 0f

        // Wrap the target pixel so drops crossing an edge reappear on the opposite side (tileable).
        val wx = ((px % size) + size) % size
        val wy = ((py % size) + size) % size
        val idx = wy * size + wx

        // Higher coverage wins on overlap (compare against the stored A byte).
        val existingA = (pixels[idx] ushr 24) and 0xFF
        val newA = (coverage.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        if (newA <= existingA) continue

        val rByte = ((0.5f + 0.5f * nx.coerceIn(-1f, 1f)) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val gByte = ((0.5f + 0.5f * ny.coerceIn(-1f, 1f)) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val bByte = (phase.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        pixels[idx] = argb(a = newA, r = rByte, g = gByte, b = bByte)
      }
    }
  }

  /** Soft rim: full coverage in the belly, easing to 0 at the very edge to avoid a hard disc cut. */
  private fun smoothEdge(u: Float): Float {
    val t = ((1f - u) / 0.18f).coerceIn(0f, 1f) // last 18% of the radius fades out
    return t * t * (3f - 2f * t) // smoothstep
  }

  private fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Small deterministic PRNG (SplitMix-style) so the droplet field is identical across runs and targets
 * without depending on `kotlin.random` seeding nuances. Pure integer math, KMP-common.
 */
private class Rng(seed: Int) {
  // SplitMix64: seed the state from the given int, then each draw advances by the golden-ratio odd
  // constant and runs the standard mix. All literals are signed-hex Longs (the same 64-bit constants).
  private var state: Long = (seed.toLong() and 0xFFFFFFFFL) xor -0x61c8864680b583ebL

  fun nextFloat(): Float {
    state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
    var z = state
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
    z = z xor (z ushr 31)
    // Top 24 bits -> [0, 1).
    return ((z ushr 40).toInt() and 0xFFFFFF) / 16777216f
  }
}

/**
 * Turns an ARGB8888 [pixels] array (non-premultiplied sRGB, `0xAARRGGBB` per int) into an
 * [ImageBitmap]. The single platform primitive the droplet generator needs — everything else is
 * commonMain. Android: `Bitmap.setPixels` + `asImageBitmap`; skiko: `Bitmap.installPixels` (RGBA
 * byte order) + `asComposeImageBitmap`.
 */
internal expect fun argbToImageBitmap(width: Int, height: Int, pixels: IntArray): ImageBitmap
