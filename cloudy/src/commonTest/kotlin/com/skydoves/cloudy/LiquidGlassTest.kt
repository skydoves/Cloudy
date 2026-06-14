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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Unit tests for [LiquidGlassDefaults] and [LiquidGlassShaderSource].
 *
 * Tests verify:
 * - Default values are correctly defined
 * - Shader source strings are valid and contain required uniforms
 * - Parameter ranges are sensible defaults
 */
internal class LiquidGlassTest :
  FunSpec({

    context("LiquidGlassDefaults") {
      test("should have valid default lens size") {
        LiquidGlassDefaults.LENS_SIZE.shouldBe(Size(350f, 350f))
        LiquidGlassDefaults.LENS_SIZE.width.shouldBe(350f)
        LiquidGlassDefaults.LENS_SIZE.height.shouldBe(350f)
      }

      test("should have valid default corner radius") {
        LiquidGlassDefaults.CORNER_RADIUS.shouldBe(50f)
      }

      test("should have valid default refraction") {
        LiquidGlassDefaults.REFRACTION.shouldBe(0.25f)
      }

      test("should have valid default curve") {
        LiquidGlassDefaults.CURVE.shouldBe(0.25f)
      }

      test("should have valid default dispersion") {
        LiquidGlassDefaults.DISPERSION.shouldBe(0.0f)
      }

      test("should have valid default saturation") {
        LiquidGlassDefaults.SATURATION.shouldBe(1.0f)
      }

      test("should have valid default contrast") {
        LiquidGlassDefaults.CONTRAST.shouldBe(1.0f)
      }

      test("should have transparent default tint") {
        LiquidGlassDefaults.TINT.shouldBe(Color.Transparent)
      }

      test("should have valid default edge") {
        LiquidGlassDefaults.EDGE.shouldBe(0.2f)
      }

      test("should have valid default glow intensity") {
        // Reproduces the historical hardcoded SPEC_STRENGTH.
        LiquidGlassDefaults.GLOW_INTENSITY.shouldBe(0.7f)
      }

      test("should have valid default glow sharpness") {
        // Reproduces the historical hardcoded SPEC_POWER.
        LiquidGlassDefaults.GLOW_SHARPNESS.shouldBe(10.0f)
      }

      test("default Glow should carry the default intensity and sharpness") {
        LiquidGlassDefaults.Glow.intensity.shouldBe(0.7f)
        LiquidGlassDefaults.Glow.sharpness.shouldBe(10.0f)
      }

      test("NoGlow should have zero intensity") {
        LiquidGlassDefaults.NoGlow.intensity.shouldBe(0f)
      }

      test("should have valid default light direction") {
        // Unnormalized raw value; the shader applies normalize(). At the default the single
        // glint rests toward screen bottom-left (the -135° direction in pixel space, y-down).
        LiquidGlassDefaults.LIGHT_DIR.shouldBe(Offset(-1f, -1f))
      }

      test("should have correct minimum Android API levels") {
        LiquidGlassDefaults.MIN_ANDROID_API_FULL.shouldBe(33)
        LiquidGlassDefaults.MIN_ANDROID_API_FALLBACK.shouldBe(23)
      }
    }

    context("LiquidGlassShaderSource.AGSL") {
      test("should not be empty") {
        LiquidGlassShaderSource.AGSL.shouldNotBe("")
        LiquidGlassShaderSource.AGSL.isNotBlank().shouldBe(true)
      }

      test("should contain resolution uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float2 resolution").shouldBe(true)
      }

      test("should contain lensCenter uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float2 lensCenter").shouldBe(true)
      }

      test("should contain lensSize uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float2 lensSize").shouldBe(true)
      }

      test("should contain cornerRadius uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float cornerRadius").shouldBe(true)
      }

      test("should contain refraction uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float refraction").shouldBe(true)
      }

      test("should contain curve uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float curve").shouldBe(true)
      }

      test("should contain dispersion uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float dispersion").shouldBe(true)
      }

      test("should contain saturation uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float saturation").shouldBe(true)
      }

      test("should contain contrast uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float contrast").shouldBe(true)
      }

      test("should contain tint uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float4 tint").shouldBe(true)
      }

      test("should contain edge uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float edge").shouldBe(true)
      }

      test("should contain lightDir uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float2 lightDir").shouldBe(true)
      }

      test("should contain specular tuning uniforms") {
        // The SPEC_* compile-time consts were promoted to uniforms so they can be tuned per draw.
        LiquidGlassShaderSource.AGSL.contains("uniform float specStrength").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float specPower").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float specRimMix").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float specWidthPx").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float specLightZ").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float specDomeFrac").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float specBodyPower").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float specBodyGain").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float specFocalK").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float specPoolFrac").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float specPoolGain").shouldBe(true)
      }

      test("should contain chromatic overlay uniforms") {
        // All 6 chromatic uniforms must be declared; the bindings write every one each draw, so a
        // missing declaration would read garbage (AGSL).
        LiquidGlassShaderSource.AGSL.contains("uniform float chromaticIntensity").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float chromaticMode").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float chromaticBands").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float chromaticCycles").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float chromaticPhase").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("uniform float chromaticModulate").shouldBe(true)
      }

      test("should drive specular from the lightDir uniform, not a hardcoded vector") {
        LiquidGlassShaderSource.AGSL.contains("normalize(lightDir)").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("normalize(float2(-1.0, -1.0))").shouldBe(false)
      }

      test("should synthesize a fake-3D bevel normal for the specular term") {
        // SDF-bevel normal + body/rim crossfade (no radial-mix path).
        LiquidGlassShaderSource.AGSL.contains("specBodyPower").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("float3(specDir2 * n_cos").shouldBe(true)
        LiquidGlassShaderSource.AGSL.contains("mix(normal, radial").shouldBe(false)
        LiquidGlassShaderSource.AGSL.contains("abs(dot(normal, lightVec))").shouldBe(false)
      }

      test("should contain content shader input") {
        LiquidGlassShaderSource.AGSL.contains("uniform shader content").shouldBe(true)
      }

      test("should contain main function") {
        LiquidGlassShaderSource.AGSL.contains("half4 main(float2 xy)").shouldBe(true)
      }

      test("should contain boxRoundedSDF function") {
        LiquidGlassShaderSource.AGSL.contains("float boxRoundedSDF").shouldBe(true)
      }

      test("should contain lensNormalDirection function") {
        LiquidGlassShaderSource.AGSL.contains("float2 lensNormalDirection").shouldBe(true)
      }

      test("should contain toBrightness function") {
        LiquidGlassShaderSource.AGSL.contains("float toBrightness").shouldBe(true)
      }

      test("should contain processColor function") {
        LiquidGlassShaderSource.AGSL.contains("half3 processColor").shouldBe(true)
      }
    }

    context("LiquidGlassShaderSource.SKSL") {
      test("should not be empty") {
        LiquidGlassShaderSource.SKSL.shouldNotBe("")
        LiquidGlassShaderSource.SKSL.isNotBlank().shouldBe(true)
      }

      test("should contain resolution uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float2 resolution").shouldBe(true)
      }

      test("should contain lensCenter uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float2 lensCenter").shouldBe(true)
      }

      test("should contain lensSize uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float2 lensSize").shouldBe(true)
      }

      test("should contain cornerRadius uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float cornerRadius").shouldBe(true)
      }

      test("should contain refraction uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float refraction").shouldBe(true)
      }

      test("should contain curve uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float curve").shouldBe(true)
      }

      test("should contain dispersion uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float dispersion").shouldBe(true)
      }

      test("should contain saturation uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float saturation").shouldBe(true)
      }

      test("should contain contrast uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float contrast").shouldBe(true)
      }

      test("should contain tint uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float4 tint").shouldBe(true)
      }

      test("should contain edge uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float edge").shouldBe(true)
      }

      test("should contain lightDir uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float2 lightDir").shouldBe(true)
      }

      test("should contain specular tuning uniforms") {
        // The SPEC_* compile-time consts were promoted to uniforms so they can be tuned per draw.
        LiquidGlassShaderSource.SKSL.contains("uniform float specStrength").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float specPower").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float specRimMix").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float specWidthPx").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float specLightZ").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float specDomeFrac").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float specBodyPower").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float specBodyGain").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float specFocalK").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float specPoolFrac").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float specPoolGain").shouldBe(true)
      }

      test("should contain chromatic overlay uniforms") {
        // All 6 chromatic uniforms must be declared; Skia throws on any unset declared uniform, so
        // the bindings write every one each draw.
        LiquidGlassShaderSource.SKSL.contains("uniform float chromaticIntensity").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float chromaticMode").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float chromaticBands").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float chromaticCycles").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float chromaticPhase").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float chromaticModulate").shouldBe(true)
      }

      test("should drive specular from the lightDir uniform, not a hardcoded vector") {
        LiquidGlassShaderSource.SKSL.contains("normalize(lightDir)").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("normalize(float2(-1.0, -1.0))").shouldBe(false)
      }

      test("should synthesize a fake-3D bevel normal for the specular term") {
        // SDF-bevel normal + body/rim crossfade (no radial-mix path).
        LiquidGlassShaderSource.SKSL.contains("specBodyPower").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("float3(specDir2 * n_cos").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("mix(normal, radial").shouldBe(false)
        LiquidGlassShaderSource.SKSL.contains("abs(dot(normal, lightVec))").shouldBe(false)
      }

      test("should contain content shader input") {
        LiquidGlassShaderSource.SKSL.contains("uniform shader content").shouldBe(true)
      }

      test("should contain main function") {
        LiquidGlassShaderSource.SKSL.contains("half4 main(float2 xy)").shouldBe(true)
      }

      test("should contain boxRoundedSDF function") {
        LiquidGlassShaderSource.SKSL.contains("float boxRoundedSDF").shouldBe(true)
      }

      test("should contain lensNormalDirection function") {
        LiquidGlassShaderSource.SKSL.contains("float2 lensNormalDirection").shouldBe(true)
      }

      test("should contain toBrightness function") {
        LiquidGlassShaderSource.SKSL.contains("float toBrightness").shouldBe(true)
      }

      test("should contain processColor function") {
        LiquidGlassShaderSource.SKSL.contains("half3 processColor").shouldBe(true)
      }
    }

    context("Shader source consistency") {
      test("AGSL and SKSL should have same uniforms") {
        val agslUniforms = listOf(
          "resolution",
          "lensCenter",
          "lensSize",
          "cornerRadius",
          "refraction",
          "curve",
          "dispersion",
          "saturation",
          "contrast",
          "tint",
          "edge",
          "content",
        )

        agslUniforms.forEach { uniform ->
          LiquidGlassShaderSource.AGSL.contains(uniform).shouldBe(true)
          LiquidGlassShaderSource.SKSL.contains(uniform).shouldBe(true)
        }
      }

      test("AGSL and SKSL should both declare the lightDir uniform") {
        // Assert the full declaration (not the bare token) on both shaders: the bare
        // "lightDir" is already satisfied by the normalize(lightDir) usage, so it would
        // pass even if one shader were missing the declaration — defeating the parity guard.
        LiquidGlassShaderSource.AGSL.contains("uniform float2 lightDir").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("uniform float2 lightDir").shouldBe(true)
      }

      test("AGSL and SKSL should have same helper functions") {
        val functions = listOf(
          "boxRoundedSDF",
          "lensNormalDirection",
          "toBrightness",
          "processColor",
        )

        functions.forEach { function ->
          LiquidGlassShaderSource.AGSL.contains(function).shouldBe(true)
          LiquidGlassShaderSource.SKSL.contains(function).shouldBe(true)
        }
      }

      test("AGSL and SKSL should have same SMOOTH_EDGE_PX constant") {
        LiquidGlassShaderSource.AGSL.contains("const float SMOOTH_EDGE_PX = 1.5").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("const float SMOOTH_EDGE_PX = 1.5").shouldBe(true)
      }
    }
  })
