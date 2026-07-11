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

import com.skydoves.cloudy.internal.MirageBackendBand
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Band ladder: the SDK->backend boundaries that gate which mirage path an optic takes. */
internal class MirageBackendBandTest :
  FunSpec({

    test("API 33+ resolves to the AGSL band") {
      MirageBackendBand.resolve(33) shouldBe MirageBackendBand.Agsl
      MirageBackendBand.resolve(37) shouldBe MirageBackendBand.Agsl
    }

    test("API 29-32 resolves to the GLES band") {
      MirageBackendBand.resolve(29) shouldBe MirageBackendBand.Gles
      MirageBackendBand.resolve(32) shouldBe MirageBackendBand.Gles
    }

    test("API 23-28 resolves to the ColorGrade band") {
      MirageBackendBand.resolve(23) shouldBe MirageBackendBand.ColorGrade
      MirageBackendBand.resolve(28) shouldBe MirageBackendBand.ColorGrade
    }
  })
