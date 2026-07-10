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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.MirageLensParams
import com.skydoves.cloudy.MirageParams
import com.skydoves.cloudy.UColor
import com.skydoves.cloudy.UFloat
import com.skydoves.cloudy.UFloatArray
import com.skydoves.cloudy.UInt1
import com.skydoves.cloudy.UOffset
import com.skydoves.cloudy.USize
import com.skydoves.cloudy.UTexture
import com.skydoves.cloudy.UVec3
import com.skydoves.cloudy.UVec4

/**
 * Standard uniform names the compiler emits on demand. A node binds each one only when the compiled
 * program declared it: Android's RuntimeShader throws IllegalArgumentException on a write to an
 * undeclared uniform (skiko tolerates it), so the CompiledProgram.uses* flags gate every bind.
 *
 * Shared by every [Effect] draw regardless of stage-0 source (self content vs. backdrop bind
 * identically), so they live here rather than in the effect.
 */
internal const val STD_RESOLUTION = "mirageResolution"
internal const val STD_TIME = "mirageTime"
internal const val STD_DENSITY = "mirageDensity"

/**
 * Resets each handle to its declared default, runs the caller's per-draw [paramsBlock], then pushes
 * the standard uniforms + every schema slot through the sink in declaration order. A pure function of
 * (cached, params, block, w, h, density, time) — no clock, no lifecycle — so both mirage nodes share
 * it as the `bind` closure they hand to [MirageFilterChain].
 *
 * The shared [MirageLensParams] framing auto-resolves here: a `lensCenter` / `lensSize` still
 * unspecified after [paramsBlock] binds as the node's center / full size (the only place both the
 * final values and the canvas size are in scope). Only those two handles get the substitution — every
 * other Offset/Size uniform must be specified.
 */
internal fun bindUniforms(
  cached: CachedProgram,
  params: MirageParams,
  paramsBlock: (MirageParams.() -> Unit)?,
  width: Float,
  height: Float,
  density: Float,
  time: Float,
): Unit = bindUniformsInto(
  cached.backend.uniformSink(),
  cached,
  params,
  paramsBlock,
  width,
  height,
  density,
  time,
)

/**
 * Walks the standard uniforms + every schema slot into [sink]. Split from [bindUniforms] so a caller
 * that must own the sink (the GLES backend pairs a fresh recording sink with one render, to avoid a
 * shared-state race) can supply it, while the Agsl/skiko/ColorGrade path uses the backend's own sink.
 */
internal fun bindUniformsInto(
  sink: UniformSink,
  cached: CachedProgram,
  params: MirageParams,
  paramsBlock: (MirageParams.() -> Unit)?,
  width: Float,
  height: Float,
  density: Float,
  time: Float,
) {
  // Reset to defaults so a value written on a previous draw does not leak when this draw's block
  // leaves it unset — the schema's declared default is the single source of truth per draw.
  resetToDefaults(params, cached.compiled.schema)
  paramsBlock?.invoke(params)

  // Standard uniforms first, each gated on whether the compiled kernel declared it: Android's
  // RuntimeShader throws on a write to an undeclared uniform name.
  val compiled = cached.compiled
  if (compiled.usesResolution) sink.float2(STD_RESOLUTION, width, height)
  if (compiled.usesTime) sink.float(STD_TIME, time)
  if (compiled.usesDensity) sink.float(STD_DENSITY, density)

  // Non-null only for lens-shaped optics; identity-compared below against this specific instance's
  // lensCenter/lensSize handles.
  val lensParams = params as? MirageLensParams

  // Schema uniforms in declaration (= bind) order: the params' live handles and the compiled schema
  // entries share the same slot index, so entries[handle.slot] names each write.
  val entries = cached.compiled.schema.entries
  for (handle in params.handles) {
    val name = entries[handle.slot].name
    when (handle) {
      is UFloat -> sink.float(name, handle.value)

      is UOffset -> {
        // Identity (not type) match: an unrelated Offset uniform on a custom optic must never fall
        // into the auto-frame substitution just because it happens to be left Unspecified too.
        val value = handle.value
        if (value.isUnspecified && handle === lensParams?.lensCenter) {
          sink.float2(name, width * 0.5f, height * 0.5f)
        } else {
          sink.float2(name, value.x, value.y)
        }
      }

      is USize -> {
        val value = handle.value
        if (value.isUnspecified && handle === lensParams?.lensSize) {
          sink.float2(name, width, height)
        } else {
          sink.float2(name, value.width, value.height)
        }
      }

      is UInt1 -> sink.int(name, handle.value)

      is UVec3 -> sink.floatArray(name, handle.value)

      is UVec4 -> sink.floatArray(name, handle.value)

      is UFloatArray -> sink.floatArray(name, handle.value)

      is UColor -> sink.color(name, handle.value)

      is UTexture -> sink.texture(name, handle.value, handle.tileMode)
    }
  }
}

/**
 * Resets each handle on [params] back to the declared default from its schema entry, so an unset
 * uniform this draw re-uses its default rather than a stale prior-draw value.
 */
internal fun resetToDefaults(params: MirageParams, schema: UniformSchema) {
  for (handle in params.handles) {
    val default = schema.entries[handle.slot].default
    when (handle) {
      is UFloat -> handle.value = default as Float

      is UOffset -> handle.value = default as Offset

      is USize -> handle.value = default as Size

      is UInt1 -> handle.value = default as Int

      is UVec3 -> handle.value = (default as FloatArray).copyOf()

      is UVec4 -> handle.value = (default as FloatArray).copyOf()

      is UFloatArray -> handle.value = (default as FloatArray).copyOf()

      is UColor -> handle.value = default as Color

      is UTexture -> {
        @Suppress("UNCHECKED_CAST")
        handle.value = default as ImageBitmap?
      }
    }
  }
}
