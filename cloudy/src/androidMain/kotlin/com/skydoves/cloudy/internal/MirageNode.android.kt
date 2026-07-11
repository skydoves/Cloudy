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

import android.os.Build

/**
 * The shading dialect for the running Android band:
 * - API 33+ ([MirageBackendBand.Agsl]) : AGSL, run as a `RuntimeShader`.
 * - API 29-32 ([MirageBackendBand.Gles]) : GLSL ES, the AGSL kernel translated for an FBO program.
 * - API 23-28 ([MirageBackendBand.ColorGrade]) : AGSL as the cache key only; the ColorGrade backend
 *   reads the compiled program's category + schema, never its GLSL source, so no translation runs.
 */
internal actual fun currentDialect(): Dialect =
  when (MirageBackendBand.resolve(Build.VERSION.SDK_INT)) {
    MirageBackendBand.Gles -> Dialect.GlslEs
    MirageBackendBand.Agsl, MirageBackendBand.ColorGrade -> Dialect.Agsl
  }
