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

      test("should contain mouse uniform") {
        LiquidGlassShaderSource.AGSL.contains("uniform float2 mouse").shouldBe(true)
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

      test("should contain content shader input") {
        LiquidGlassShaderSource.AGSL.contains("uniform shader content").shouldBe(true)
      }

      test("should contain main function") {
        LiquidGlassShaderSource.AGSL.contains("half4 main(float2 fragCoord)").shouldBe(true)
      }

      test("should contain roundedRectDistance function") {
        LiquidGlassShaderSource.AGSL.contains("float roundedRectDistance").shouldBe(true)
      }

      test("should contain calculateSurfaceGradient function") {
        LiquidGlassShaderSource.AGSL.contains("float2 calculateSurfaceGradient").shouldBe(true)
      }

      test("should contain getLuminance function") {
        LiquidGlassShaderSource.AGSL.contains("float getLuminance").shouldBe(true)
      }

      test("should contain applyColorGrading function") {
        LiquidGlassShaderSource.AGSL.contains("half3 applyColorGrading").shouldBe(true)
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

      test("should contain mouse uniform") {
        LiquidGlassShaderSource.SKSL.contains("uniform float2 mouse").shouldBe(true)
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

      test("should contain content shader input") {
        LiquidGlassShaderSource.SKSL.contains("uniform shader content").shouldBe(true)
      }

      test("should contain main function") {
        LiquidGlassShaderSource.SKSL.contains("half4 main(float2 fragCoord)").shouldBe(true)
      }

      test("should contain roundedRectDistance function") {
        LiquidGlassShaderSource.SKSL.contains("float roundedRectDistance").shouldBe(true)
      }

      test("should contain calculateSurfaceGradient function") {
        LiquidGlassShaderSource.SKSL.contains("float2 calculateSurfaceGradient").shouldBe(true)
      }

      test("should contain getLuminance function") {
        LiquidGlassShaderSource.SKSL.contains("float getLuminance").shouldBe(true)
      }

      test("should contain applyColorGrading function") {
        LiquidGlassShaderSource.SKSL.contains("half3 applyColorGrading").shouldBe(true)
      }
    }

    context("Shader source consistency") {
      test("AGSL and SKSL should have same uniforms") {
        val agslUniforms = listOf(
          "resolution",
          "mouse",
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

      test("AGSL and SKSL should have same helper functions") {
        val functions = listOf(
          "roundedRectDistance",
          "calculateSurfaceGradient",
          "getLuminance",
          "applyColorGrading",
        )

        functions.forEach { function ->
          LiquidGlassShaderSource.AGSL.contains(function).shouldBe(true)
          LiquidGlassShaderSource.SKSL.contains(function).shouldBe(true)
        }
      }

      test("AGSL and SKSL should have same ANTIALIAS_RADIUS constant") {
        LiquidGlassShaderSource.AGSL.contains("const float ANTIALIAS_RADIUS = 1.5").shouldBe(true)
        LiquidGlassShaderSource.SKSL.contains("const float ANTIALIAS_RADIUS = 1.5").shouldBe(true)
      }
    }
  })
