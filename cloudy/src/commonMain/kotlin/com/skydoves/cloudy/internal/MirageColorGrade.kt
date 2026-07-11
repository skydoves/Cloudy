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

package com.skydoves.cloudy.internal

import androidx.compose.ui.graphics.Color
import com.skydoves.cloudy.ExperimentalMirage

/**
 * Below API 33 there is no `RuntimeShader`, so a lens optic cannot run. The one built-in Colorize
 * optic — Duotone — is nonetheless a **pure affine transform of the source pixel**, so it can be
 * reproduced exactly with a 4x5 color matrix (the thing a `ColorMatrixColorFilter` runs, available on
 * every API). This file derives that matrix from the optic's schema defaults; the Android ColorGrade
 * backend turns it into a `ColorMatrixColorFilter`.
 *
 * ## Why this is exact
 * The Duotone kernel is (per-channel c, over unpremultiplied rgb):
 * ```
 *   g       = dot(rgb, LUMA)                              // BT.709 luminance
 *   dz_c    = shadow_c + g * (highlight_c - shadow_c)     // shadow->highlight ramp
 *   out_c   = (1 - amount) * rgb_c + amount * dz_c        // cross-fade toward the ramp
 *   out_a   = a                                           // alpha untouched
 * ```
 * Substituting `g` and grouping by input channel gives, for each output channel c:
 * ```
 *   out_c = amount*(highlight_c - shadow_c) * (LUMA·rgb)    // luminance mix
 *         + (1 - amount) * rgb_c                            // passthrough on channel c
 *         + amount * shadow_c                               // translation
 * ```
 * which is one 4x5 matrix row. A random-sample check (desktop test) confirms the reproduction is
 * bit-exact against the kernel formula.
 */

/** BT.709 luma weights — must match the `dot(src.rgb, half3(...))` in the Duotone kernel. */
private val LUMA = floatArrayOf(0.2126f, 0.7152f, 0.0722f)

/** Schema-entry names of the Duotone params. A Colorize optic with exactly these is reproducible. */
private const val NAME_SHADOW = "shadow"
private const val NAME_HIGHLIGHT = "highlight"
private const val NAME_AMOUNT = "amount"

/**
 * Whether [compiled] is a reproducible affine Duotone Colorize (category Colorize + `shadow`/
 * `highlight` color uniforms + a `float amount`). Only then can the ColorGrade band stand in for it
 * below API 33; any other optic's kernel is not affine and stays a no-op.
 */
internal fun isColorGradeReproducible(compiled: CompiledProgram): Boolean {
  if (compiled.category != OpticCategory.Colorize) return false
  val e = compiled.schema.entries
  return e.any { it.name == NAME_SHADOW && it.isColor } &&
    e.any { it.name == NAME_HIGHLIGHT && it.isColor } &&
    e.any { it.name == NAME_AMOUNT && !it.isColor }
}

/**
 * Builds the 4x5 row-major color matrix (android.graphics.ColorMatrix layout: rows R,G,B,A; cols
 * R,G,B,A,offset — offset column in 0..255 scale) reproducing the Duotone grade from the **current**
 * `shadow`/`highlight`/`amount` values in [params] (per-draw, so a `filter(Duotone){ shadow(Red) }`
 * override is honored, matching 33+/skiko). Falls back to the schema default for any value the draw's
 * block left unset — the params were reset to defaults before the block ran.
 */
internal fun colorGradeMatrixOf(compiled: CompiledProgram, params: com.skydoves.cloudy.MirageParams): FloatArray {
  val entries = compiled.schema.entries
  var shadow = Color(0f, 0f, 0f)
  var highlight = Color(1f, 1f, 1f)
  var amount = 1f
  for (handle in params.handles) {
    when (entries[handle.slot].name) {
      NAME_SHADOW -> (handle as? com.skydoves.cloudy.UColor)?.let { shadow = it.value }
      NAME_HIGHLIGHT -> (handle as? com.skydoves.cloudy.UColor)?.let { highlight = it.value }
      NAME_AMOUNT -> (handle as? com.skydoves.cloudy.UFloat)?.let { amount = it.value }
    }
  }
  return duotoneMatrix(shadow, highlight, amount)
}

/**
 * The affine matrix for one (shadow, highlight, amount) grade. Split out from the schema lookup so it
 * is directly unit-testable against the kernel formula.
 */
internal fun duotoneMatrix(shadow: Color, highlight: Color, amount: Float): FloatArray {
  val s = floatArrayOf(shadow.red, shadow.green, shadow.blue)
  val h = floatArrayOf(highlight.red, highlight.green, highlight.blue)

  // Row-major 4x5. Start at zero; fill each RGB row, alpha passthrough.
  val m = FloatArray(20)
  for (c in 0 until 3) {
    val delta = amount * (h[c] - s[c]) // coefficient on the luminance dot
    val row = c * 5
    m[row + 0] = delta * LUMA[0]
    m[row + 1] = delta * LUMA[1]
    m[row + 2] = delta * LUMA[2]
    m[row + c] += (1f - amount) // passthrough term on this channel's own input
    // android.graphics.ColorMatrix applies the offset column in 0..255 units.
    m[row + 4] = amount * s[c] * 255f
  }
  // Alpha row: pass alpha through unchanged.
  m[15 + 3] = 1f

  return m
}
