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
package com.skydoves.cloudy

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Which way the chromatic overlay paints its rainbow over the glass.
 *
 * - [Iridescent] — thin-film interference: the hue rotates with the light/normal angle, like oil on
 *   water or a soap bubble. The sheen shifts as the surface (or the light) tilts.
 * - [Foil] — flowing bands: a fixed number of rainbow bands projected along the light direction, so
 *   moving the light makes the bands stream across the face like a holographic foil.
 *
 * Maps to the shader's `chromaticMode` uniform (`0f` = [Iridescent], `1f` = [Foil]).
 */
@ExperimentalLiquidGlassMaterial
public enum class ChromaticMode { Iridescent, Foil }

/**
 * Perceptual description of the Liquid Glass **chromatic overlay** — the light-reactive iridescent
 * sheen layered on top of the white specular glint.
 *
 * Exposes the knobs a caller usually wants: how strong the sheen is ([intensity]), how it paints
 * ([mode]), and the rainbow [spectrum] / [customBrush] used by the consumer (non-shader) path. The
 * remaining shader tunables (band count, hue cycles, phase, focal-pool modulation) are held at their
 * tuned defaults and written as constants by each platform binding, so they are not part of this
 * stable surface.
 *
 * This is intentionally **not** a `data class`: the constructor is internal and `equals`/`hashCode`
 * are hand-written, so future knobs can be added via a secondary constructor (or by widening the
 * factory) without breaking the generated-component / copy ABI of a data class.
 *
 * Construct with the [ChromaticOverlay] companion `invoke`; presets live on [LiquidGlassDefaults]
 * ([LiquidGlassDefaults.NoChromatic], [LiquidGlassDefaults.Holographic]).
 *
 * > **Shader path note:** when fed to [Modifier.liquidGlass], only [intensity] and [mode] reach the
 * > shader (the rainbow hue is synthesized on the GPU). [spectrum] and [customBrush] are carried for
 * > the consumer / `customBrush` path and future expansion; they are **not** sampled by the current
 * > liquid-glass shader binding.
 *
 * @property intensity Overlay strength, roughly `0..1`. `0` disables the sheen (bit-exact: the shader
 *   gate skips the term entirely). Maps to the shader's `chromaticIntensity`.
 * @property mode How the rainbow is painted — [ChromaticMode.Iridescent] or [ChromaticMode.Foil].
 *   Maps to the shader's `chromaticMode`.
 * @property spectrum Rainbow color stops for the consumer / [customBrush] path. The shader path
 *   synthesizes hue on the GPU and does **not** sample this list. Defensively copied by the factory,
 *   so the holder stays truly immutable even if the caller mutates the original list.
 * @property customBrush Optional caller-supplied [Brush] for the consumer path (currently unused by
 *   the liquid-glass shader binding; reserved for the future cloudy consumer path). If you build a
 *   brush inline, wrap it in `remember` so the holder identity is stable across recompositions —
 *   constructing a new brush every recomposition reallocates the modifier and defeats the stability
 *   guarantee (see [LiquidGlassLight]'s remember note).
 *
 * @see LiquidGlassDefaults.NoChromatic
 * @see LiquidGlassDefaults.Holographic
 */
@ExperimentalLiquidGlassMaterial
@Immutable
public class ChromaticOverlay internal constructor(
  public val intensity: Float,
  public val mode: ChromaticMode,
  public val spectrum: List<Color>,
  public val customBrush: Brush? = null,
) {
  override fun equals(other: Any?): Boolean = this === other ||
    (
      other is ChromaticOverlay &&
        intensity == other.intensity &&
        mode == other.mode &&
        spectrum == other.spectrum &&
        customBrush == other.customBrush
      )

  override fun hashCode(): Int {
    // equals() uses `==` so -0.0f and 0.0f compare equal, but Float.hashCode() hashes their bits and
    // would differ. Normalize -0.0f -> 0.0f (the `== 0f` check catches both) to keep the
    // equals/hashCode contract: equal instances must yield equal hash codes.
    val i = if (intensity == 0f) 0f else intensity
    var result = i.hashCode()
    result = result * 31 + mode.hashCode()
    result = result * 31 + spectrum.hashCode()
    result = result * 31 + (customBrush?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String =
    "ChromaticOverlay(intensity=$intensity, mode=$mode, spectrum=$spectrum, " +
      "customBrush=$customBrush)"

  public companion object {
    /**
     * Creates a [ChromaticOverlay] from its perceptual knobs.
     *
     * Exposed as a companion `invoke` rather than a same-named top-level factory function: the
     * primary constructor is `internal` and takes the same signature, so a top-level
     * `fun ChromaticOverlay(...)` would be a conflicting overload. `invoke` keeps the intended
     * `ChromaticOverlay(...)` call syntax while leaving the internal constructor free for future
     * secondary-constructor additions (ABI stability).
     *
     * @param intensity Overlay strength (`0..1`). `0` disables the sheen.
     *   Default: [LiquidGlassDefaults.CHROMATIC_INTENSITY].
     * @param mode How the rainbow is painted. Default: [ChromaticMode.Iridescent].
     * @param spectrum Rainbow color stops (consumer / [customBrush] path only).
     *   Default: [LiquidGlassDefaults.HOLOGRAPHIC_SPECTRUM]. Defensively copied with `.toList()` so
     *   the resulting holder is truly immutable.
     * @param customBrush Optional caller-supplied brush (consumer path; unused by the shader path).
     */
    public operator fun invoke(
      intensity: Float = LiquidGlassDefaults.CHROMATIC_INTENSITY,
      mode: ChromaticMode = ChromaticMode.Iridescent,
      spectrum: List<Color> = LiquidGlassDefaults.HOLOGRAPHIC_SPECTRUM,
      customBrush: Brush? = null,
    ): ChromaticOverlay = ChromaticOverlay(intensity, mode, spectrum.toList(), customBrush)
  }
}
