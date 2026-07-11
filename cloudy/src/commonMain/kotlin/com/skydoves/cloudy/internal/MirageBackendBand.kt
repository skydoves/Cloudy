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
package com.skydoves.cloudy.internal

/**
 * Which Android backend an optic runs on, chosen once per program build from the running SDK level.
 * A pure function of the SDK int (no framework types), so [resolve] is unit-testable off-device; the
 * android backend actual maps each band to a [Dialect] and a concrete program (or `null` = no-op).
 *
 * The skiko targets never consult this — they always have Skia and a single [Dialect.Sksl] backend.
 */
internal enum class MirageBackendBand {
  /** API 33+ : AGSL `RuntimeShader` + content-bound `RenderEffect` (the original, unchanged path). */
  Agsl,

  /** API 29-32 : GLES 3.0 offscreen FBO, AGSL translated to GLSL ES, output via HardwareBuffer. */
  Gles,

  /**
   * API 23-28 : no runtime shader available. A Colorize optic is reproduced with a `ColorMatrix`
   * grade; any other optic is a no-op (the caller may draw a user-supplied fallback instead).
   */
  ColorGrade,

  ;

  companion object {
    // API level constants named locally rather than via Build.VERSION_CODES so this stays a pure
    // commonMain function with no android dependency.
    private const val API_TIRAMISU = 33 // Android 13
    private const val API_Q = 29 // Android 10 (HardwareBuffer + Bitmap.wrapHardwareBuffer)

    /** Resolves the band for a running Android [sdkInt]. Below API 29 there is no GLES path yet. */
    fun resolve(sdkInt: Int): MirageBackendBand = when {
      sdkInt >= API_TIRAMISU -> Agsl
      sdkInt >= API_Q -> Gles
      else -> ColorGrade
    }
  }
}
