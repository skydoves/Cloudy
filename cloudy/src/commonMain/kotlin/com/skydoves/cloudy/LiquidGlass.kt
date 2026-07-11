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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Stable holder for the Liquid Glass specular light direction (screen-space, y-down).
 *
 * The stable holder identity (vs. a raw [Offset]) lets a high-frequency source invalidate
 * only the draw, not the composition. Create a static light with the [LiquidGlassLight]
 * factory, or a motion-driven one with `rememberGyroLightSource`.
 *
 * @see rememberGyroLightSource
 * @see LiquidGlassDefaults.Light
 */
@Immutable
public class LiquidGlassLight internal constructor(internal val direction: State<Offset>)

/**
 * Creates a static (fixed) [LiquidGlassLight] pointing in [direction].
 *
 * Wrap inline calls in `remember` (or use [LiquidGlassDefaults.Light]) so the holder
 * identity stays stable across recompositions.
 *
 * @param direction Screen-space light direction (y-down). Magnitude is irrelevant; the
 *   shader normalizes it.
 */
public fun LiquidGlassLight(direction: Offset): LiquidGlassLight =
  LiquidGlassLight(mutableStateOf(direction))

/**
 * Perceptual tuning for the Liquid Glass specular glint — the bright rim highlight that the
 * [light] source sweeps around the lens.
 *
 * Exposes the two knobs a caller usually wants: brightness ([intensity]) and focus
 * ([sharpness]). The remaining shader tunables are only reachable through the experimental
 * [Modifier.liquidGlassTuned] modifier.
 *
 * Intentionally not a `data class`: the internal constructor and hand-written `equals`/
 * `hashCode` let future knobs be added without breaking ABI.
 *
 * Construct with the [LiquidGlassGlow] factory; defaults are [LiquidGlassDefaults.Glow].
 *
 * @property intensity Peak highlight brightness, roughly `0..1` (screen-blended, so it stays
 *   `<= 1.0`). `0` disables the glint. Maps to the shader's `specStrength`.
 * @property sharpness Glint lobe sharpness — higher = one tighter, more focused highlight; lower =
 *   a broader, softer wash. Maps to the shader's `specPower`.
 *
 * @see LiquidGlassDefaults.Glow
 * @see LiquidGlassDefaults.NoGlow
 */
@Immutable
public class LiquidGlassGlow internal constructor(
  public val intensity: Float,
  public val sharpness: Float,
) {
  override fun equals(other: Any?): Boolean = this === other ||
    (other is LiquidGlassGlow && intensity == other.intensity && sharpness == other.sharpness)

  override fun hashCode(): Int {
    // Normalize -0.0f to 0.0f: it compares == in equals() but Float.hashCode() hashes its bits.
    val i = if (intensity == 0f) 0f else intensity
    val s = if (sharpness == 0f) 0f else sharpness
    return i.hashCode() * 31 + s.hashCode()
  }

  override fun toString(): String = "LiquidGlassGlow(intensity=$intensity, sharpness=$sharpness)"

  public companion object {
    /**
     * Creates a [LiquidGlassGlow] from the two perceptual knobs.
     *
     * A companion `invoke` rather than a top-level factory: the internal constructor has the
     * same `(Float, Float)` signature, so a top-level factory would be a conflicting overload.
     *
     * @param intensity Peak highlight brightness (`0..1`). `0` disables the glint.
     *   Default: [LiquidGlassDefaults.GLOW_INTENSITY].
     * @param sharpness Glint lobe sharpness; higher = tighter glint.
     *   Default: [LiquidGlassDefaults.GLOW_SHARPNESS].
     */
    public operator fun invoke(
      intensity: Float = LiquidGlassDefaults.GLOW_INTENSITY,
      sharpness: Float = LiquidGlassDefaults.GLOW_SHARPNESS,
    ): LiquidGlassGlow = LiquidGlassGlow(intensity, sharpness)
  }
}

/**
 * Full specular tuning (internal). The public surface exposes only [intensity]/[sharpness] via
 * [LiquidGlassGlow]; the extra knobs are reachable through the experimental
 * [Modifier.liquidGlassTuned]. Every platform binding writes all knobs as uniforms each draw.
 *
 * @property intensity peak highlight brightness (screen-blended; stays `<= 1.0`).
 * @property sharpness rim/back lobe sharpness (Blinn).
 * @property rimMix body↔rim crossfade: `0` = pure body sheen, `1` = pure rim glint.
 * @property widthPx rim band thickness, decoupled from `edge`.
 * @property lightZ fake-3D light z-height; tilts the synthesized bevel normal toward the viewer.
 * @property domeFrac bevel depth as a fraction of the lens half-extent; `> 1` removes the static
 *   dead-core.
 * @property bodyPower body-sheen lobe sharpness (broad, gentle ramp).
 * @property bodyGain body-sheen intensity scale.
 * @property focalK moving-hotspot offset toward the light, as a fraction of the lens half-extent.
 *   Drives the highlight sweep on both axes: pool center is `lensCenter + lightVec * (minHalf * focalK)`.
 * @property poolFrac focal-pool radius as a fraction of the lens half-extent; smaller = tighter.
 * @property poolGain focal-pool peak scale (multiplies `intensity`); the dominant visible term at
 *   the default [rimMix].
 */
@Immutable
internal class GlowTuning(
  val intensity: Float = LiquidGlassDefaults.GLOW_INTENSITY,
  val sharpness: Float = LiquidGlassDefaults.GLOW_SHARPNESS,
  // Biased toward the body so the moving focal pool, not the thin rim, carries the visible motion.
  val rimMix: Float = 0.4f,
  val widthPx: Float = 12.0f,
  val lightZ: Float = 0.55f,
  val domeFrac: Float = 1.15f,
  val bodyPower: Float = 2.5f,
  val bodyGain: Float = 0.6f,
  val focalK: Float = 0.55f,
  val poolFrac: Float = 0.7f,
  val poolGain: Float = 1.3f,
)

/** Widens a public [LiquidGlassGlow] into the internal 4-knob [GlowTuning] (extra knobs default). */
internal fun LiquidGlassGlow.toTuning(): GlowTuning =
  GlowTuning(intensity = intensity, sharpness = sharpness)

/**
 * Default values for the Liquid Glass effect.
 */
public object LiquidGlassDefaults {
  /** Default lens size in pixels. */
  public val LENS_SIZE: Size = Size(350f, 350f)

  /** Default corner radius for the lens shape. */
  public const val CORNER_RADIUS: Float = 50f

  /** Default refraction strength. Controls how much the background distorts. */
  public const val REFRACTION: Float = 0.25f

  /** Default curve strength. Controls how strongly the lens curves at center vs edges. */
  public const val CURVE: Float = 0.25f

  /** Default dispersion (chromatic aberration) strength. */
  public const val DISPERSION: Float = 0.0f

  /** Default saturation. 1.0 = normal, <1.0 = desaturated, >1.0 = oversaturated. */
  public const val SATURATION: Float = 1.0f

  /** Default contrast. 1.0 = normal, <1.0 = less contrast, >1.0 = more contrast. */
  public const val CONTRAST: Float = 1.0f

  /** Default tint color (transparent = no tint). */
  public val TINT: Color = Color.Transparent

  /** Default edge lighting width. 0.0 = no edge, higher = wider edge lighting. */
  public const val EDGE: Float = 0.2f

  /**
   * Default specular light direction (screen-space, y-down). Unnormalized; the shader applies
   * `normalize(lightDir)`. Reproduces the old fixed light's direction, but the highlight is a new
   * multi-term specular model, so the visuals differ from pre-1.x.
   */
  public val LIGHT_DIR: Offset = Offset(-1f, -1f)

  /**
   * Default fixed light — reuses the pre-motion light direction. Singleton; do not reassign.
   */
  public val Light: LiquidGlassLight = LiquidGlassLight(LIGHT_DIR)

  /**
   * Default specular glint intensity (peak brightness) for the new multi-term specular model.
   * Screen-blended, so it stays `<= 1.0`.
   */
  public const val GLOW_INTENSITY: Float = 0.7f

  /**
   * Default specular glint sharpness (lobe power) for the new multi-term specular model —
   * one tight glint.
   */
  public const val GLOW_SHARPNESS: Float = 10.0f

  /**
   * Default glow for the new specular model. Singleton; do not reassign.
   */
  public val Glow: LiquidGlassGlow = LiquidGlassGlow(GLOW_INTENSITY, GLOW_SHARPNESS)

  /**
   * A glow that disables the specular glint (zero intensity). Singleton; do not reassign.
   * Sharpness is a harmless `1.0` since the highlight is scaled to zero regardless.
   */
  public val NoGlow: LiquidGlassGlow = LiquidGlassGlow(0f, 1f)

  /** Minimum Android API level required for the full liquid glass effect. */
  public const val MIN_ANDROID_API_FULL: Int = 33

  /** Minimum Android API level for fallback support. */
  public const val MIN_ANDROID_API_FALLBACK: Int = 23
}

/**
 * Applies a cross-platform Liquid Glass effect to the content.
 *
 * This modifier creates an interactive glass lens effect that distorts the dynamic content
 * beneath it in real-time. The effect uses SDF (Signed Distance Field) for crisp edges,
 * normal-based refraction, and chromatic dispersion.
 *
 * **Note:** For blur effects, use [Modifier.cloudy] separately. This modifier focuses on
 * the lens distortion effect and can be combined with Cloudy's blur for a complete
 * frosted glass look.
 *
 * **Refraction-only:** for pure refraction without the specular glint, pass
 * `glow = LiquidGlassDefaults.NoGlow`; apply the specular separately and tunably with
 * `Modifier.mirage { filter(MirageShaders.Specular) }`.
 *
 * ## Platform Behavior
 *
 * | Platform | Implementation | Features |
 * |----------|----------------|----------|
 * | Android API 33+ | RuntimeShader (AGSL) | Full effect |
 * | Android API 23-32 | Fallback | Saturation + edge (no refraction/dispersion) |
 * | iOS/macOS/Desktop | Skia RuntimeEffect (SKSL) | Full effect |
 *
 * ## Example Usage
 *
 * ```kotlin
 * var lensCenter by remember { mutableStateOf(Offset(100f, 100f)) }
 *
 * Box(
 *   modifier = Modifier
 *     .fillMaxSize()
 *     .pointerInput(Unit) {
 *       detectDragGestures { change, _ ->
 *         lensCenter = change.position
 *       }
 *     }
 *     .cloudy(radius = 15) // Use Cloudy for blur
 *     .liquidGlass(
 *       lensCenter = lensCenter,
 *       lensSize = Size(350f, 350f),
 *       cornerRadius = 50f,
 *       refraction = 0.25f,
 *       curve = 0.25f,
 *     )
 * ) {
 *   Image(painter = painterResource(R.drawable.photo), ...)
 * }
 * ```
 *
 * @param lensCenter The current position of the glass lens center in pixels.
 *   This should be updated based on touch/pointer input for interactive effects.
 *
 * @param lensSize The size of the lens in pixels (width, height).
 *   Default: [LiquidGlassDefaults.LENS_SIZE] (350x350).
 *
 * @param cornerRadius The corner radius of the rounded rectangle lens shape.
 *   Use higher values for more rounded corners, or set to half of lensSize for circular.
 *   Default: [LiquidGlassDefaults.CORNER_RADIUS] (50).
 *
 * @param refraction Controls how much the background distorts through the liquid lens.
 *   Setting to 0 removes the liquid effect. No-op on Android API < 33.
 *   Default: [LiquidGlassDefaults.REFRACTION] (0.25).
 *
 * @param curve Controls how strongly the liquid lens curves at its center vs edges.
 *   Setting to 0 removes the liquid effect. No-op on Android API < 33.
 *   Default: [LiquidGlassDefaults.CURVE] (0.25).
 *
 * @param dispersion The chromatic dispersion (aberration) intensity.
 *   Controls the RGB channel separation that creates the prism-like effect.
 *   No-op on Android API < 33.
 *   Default: [LiquidGlassDefaults.DISPERSION] (0.0).
 *
 * @param saturation Color saturation adjustment. 1.0 = normal.
 *   Works on all platforms including fallback.
 *   Default: [LiquidGlassDefaults.SATURATION] (1.0).
 *
 * @param contrast Adjusts the difference between light and dark areas.
 *   1.0 = normal, >1.0 = more contrast, <1.0 = less contrast.
 *   Default: [LiquidGlassDefaults.CONTRAST] (1.0).
 *
 * @param tint Optional color tint to apply over the glass effect.
 *   Use Color.Transparent for no tint.
 *   Default: [LiquidGlassDefaults.TINT] (Transparent).
 *
 * @param edge The edge lighting/rim width. Higher values create wider, softer edges.
 *   Set to 0 to disable edge lighting. On Android API < 33, this becomes
 *   a boolean where value > 0 draws a fixed width edge effect.
 *   Default: [LiquidGlassDefaults.EDGE] (0.2).
 *
 * @param light The specular light source driving the rim highlight. Defaults to a fixed
 *   light ([LiquidGlassDefaults.Light]) that reproduces the previous behavior. Pass a
 *   motion-driven source from `rememberGyroLightSource` to make the highlight sweep as the
 *   device tilts. No-op on Android API < 33 (the fallback path has no shader).
 *
 * @param glow Perceptual tuning for the specular glint — how bright ([LiquidGlassGlow.intensity])
 *   and how tight ([LiquidGlassGlow.sharpness]) the rim highlight is. Defaults to
 *   [LiquidGlassDefaults.Glow] (the historical look); use [LiquidGlassDefaults.NoGlow] to remove
 *   the glint. For the full set of shader tunables, see the experimental [Modifier.liquidGlassTuned].
 *   No-op on Android API < 33 (the fallback path has no shader).
 *
 * @param enabled If false, disables the effect and returns the original modifier.
 *
 * @return A [Modifier] with the Liquid Glass effect applied.
 *
 * @see LiquidGlassDefaults
 * @see LiquidGlassShaderSource
 * @see cloudy
 */
@Composable
public expect fun Modifier.liquidGlass(
  lensCenter: Offset,
  lensSize: Size = LiquidGlassDefaults.LENS_SIZE,
  cornerRadius: Float = LiquidGlassDefaults.CORNER_RADIUS,
  refraction: Float = LiquidGlassDefaults.REFRACTION,
  curve: Float = LiquidGlassDefaults.CURVE,
  dispersion: Float = LiquidGlassDefaults.DISPERSION,
  saturation: Float = LiquidGlassDefaults.SATURATION,
  contrast: Float = LiquidGlassDefaults.CONTRAST,
  tint: Color = LiquidGlassDefaults.TINT,
  edge: Float = LiquidGlassDefaults.EDGE,
  light: LiquidGlassLight = LiquidGlassDefaults.Light,
  glow: LiquidGlassGlow = LiquidGlassDefaults.Glow,
  enabled: Boolean = true,
): Modifier
