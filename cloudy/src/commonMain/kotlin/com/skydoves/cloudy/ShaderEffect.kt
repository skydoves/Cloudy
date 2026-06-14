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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Default geometry for [Modifier.shaderEffect].
 *
 * Mirrors the lens defaults of [LiquidGlassDefaults] so the open shader-effect modifier starts from
 * the same lens framing as the built-in liquid glass effect.
 */
@ExperimentalShaderEffect
public object ShaderEffectDefaults {
  /**
   * Default lens center, in pixels. There is no built-in equivalent on [LiquidGlassDefaults]
   * (`liquidGlass` requires an explicit center), so this defaults to the content origin; callers
   * driving an interactive lens should pass a pointer-tracked value.
   */
  public val LensCenter: Offset = Offset.Zero

  /** Default lens size in pixels. Mirrors [LiquidGlassDefaults.LENS_SIZE] (350x350). */
  public val LensSize: Size = LiquidGlassDefaults.LENS_SIZE

  /**
   * Default lens corner radius. Mirrors [LiquidGlassDefaults.CORNER_RADIUS] (50).
   *
   * A plain `val` (not `const`) so it keeps the PascalCase name alongside [LensCenter] / [LensSize];
   * ktlint's property-naming rule forces screaming-snake on `const val`.
   */
  public val CornerRadius: Float = LiquidGlassDefaults.CORNER_RADIUS
}

/**
 * Applies an open, caller-supplied [ShaderRecipe] to the content.
 *
 * This is the extensible counterpart to [Modifier.liquidGlass]: instead of a fixed set of liquid-
 * glass parameters, it takes a [recipe] that carries the AGSL / SKSL shader bodies and a uniform-
 * binding block. The built-in lens geometry ([lensCenter] / [lensSize] / [cornerRadius]), the
 * specular [light] holder, and [time] are passed through to the recipe's shader as standard
 * uniforms (declared by the shared preamble), so recipes can build on the same lens framing the
 * liquid-glass effect uses.
 *
 * Reuses the existing [LiquidGlassLight] holder (via [LiquidGlassDefaults.Light]) rather than
 * introducing a new light type, so a motion-driven source from `rememberGyroLightSource` drives
 * both the built-in effect and custom recipes.
 *
 * Hoist [recipe] into a `remember(recipe)` (or a top-level constant) so the modifier is not
 * reallocated every recomposition; see [ShaderRecipe] for the recipe-author contract.
 *
 * @param recipe The shader recipe to apply (shader bodies + uniform binding + input mode).
 * @param lensCenter The lens center in pixels. Default: [ShaderEffectDefaults.LensCenter].
 * @param lensSize The lens size in pixels. Default: [ShaderEffectDefaults.LensSize].
 * @param cornerRadius The lens corner radius. Default: [ShaderEffectDefaults.CornerRadius].
 * @param light The specular light source holder, reused from the liquid-glass API.
 *   Default: [LiquidGlassDefaults.Light].
 * @param time A monotonic time value (seconds) forwarded to the shader for animated recipes.
 *   Default: `0f`.
 * @param enabled If false, the effect is disabled and the original [Modifier] is returned unchanged.
 *
 * @return A [Modifier] with the shader effect applied.
 *
 * @see ShaderRecipe
 * @see Modifier.liquidGlass
 */
@ExperimentalShaderEffect
@Composable
public expect fun Modifier.shaderEffect(
  recipe: ShaderRecipe,
  lensCenter: Offset = ShaderEffectDefaults.LensCenter,
  lensSize: Size = ShaderEffectDefaults.LensSize,
  cornerRadius: Float = ShaderEffectDefaults.CornerRadius,
  light: LiquidGlassLight = LiquidGlassDefaults.Light,
  time: Float = 0f,
  enabled: Boolean = true,
): Modifier
