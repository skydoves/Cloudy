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
package com.skydoves.cloudy.internal.edsl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.io.File

/**
 * Divergence gate for [MirageReservedNames]: every function name `ShaderValues` emits via `Call("name",
 * ...)` must be reserved, or a future user `shaderFunction` could reuse it and silently miscompile. The
 * two lists live apart (one emits, one reserves), so this test reads the emitter source and fails if any
 * emitted builtin is missing from `RESERVED`.
 */
internal class MirageReservedNamesTest :
  FunSpec({

    test("every emitted builtin is reserved") {
      // Walk up to the repo root (Gradle sets user.dir to the module dir for JVM tests), then read the
      // emitter source off disk — the same on-disk lookup RainyWindowRasterTest uses.
      var dir: File? = File(System.getProperty("user.dir")).absoluteFile
      while (dir != null && !File(dir, "settings.gradle.kts").exists()) {
        dir = dir.parentFile
      }
      requireNotNull(dir) { "could not locate the repo root (settings.gradle.kts) from user.dir" }
      val source = File(
        dir,
        "cloudy/src/commonMain/kotlin/com/skydoves/cloudy/internal/edsl/ShaderValues.kt",
      )
      require(source.exists()) { "ShaderValues.kt not found at ${source.absolutePath}" }

      val emitted = Regex("""Call\("([A-Za-z_][A-Za-z0-9_]*)"""")
        .findAll(source.readText())
        .map { it.groupValues[1] }
        .toSet()

      (emitted - MirageReservedNames.RESERVED).shouldBeEmpty()
    }
  })
