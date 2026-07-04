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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import com.skydoves.cloudy.internal.UniformEntry
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * The uniform schema of a mirage optic, declared by subclassing and writing one
 * `val <name> by uniform(...)` property per uniform.
 *
 * Each delegated property does two things at once: it names a shader uniform (the property name is
 * the uniform identifier the codegen emits) and it exposes a typed [handle][UniformHandle] the
 * caller writes each draw (`strength(8f)` or `strength.value = 8f`). Declaration order is the
 * uniform binding order — the delegate registers slots eagerly in `provideDelegate`, so the slot
 * index equals the source order with no reflection (KMP-safe).
 *
 * Lifetime: the engine creates one instance per node and reuses it — every param write happens on
 * the single draw-phase thread, so no synchronization and no per-frame allocation.
 *
 * A handle here is a plain typed slot, not a shader expression: it carries the per-draw value and
 * its binding slot, nothing more. (The tracing eDSL that turns handles into expression nodes is a
 * later milestone.)
 */
@ExperimentalMirage
public abstract class MirageParams {

  private val registered: MutableList<UniformEntry> = mutableListOf()

  // The live handles, parallel to [registered] by declaration order (handle.slot == list index). The
  // node walks these to read each draw's current value; the schema entry at the same slot carries the
  // name / type / default. Kept alongside the entries so the two lists never drift.
  private val registeredHandles: MutableList<UniformHandle> = mutableListOf()

  internal val schemaEntries: List<UniformEntry>
    get() = registered

  /** The declared handles in binding order (index == [UniformHandle.slot]). Read per draw. */
  internal val handles: List<UniformHandle>
    get() = registeredHandles

  /** Declares a `uniform float`. Handle: [UFloat]. */
  protected fun uniform(default: Float): UniformProvider<UFloat> =
    registerHandle("float", default) { UFloat(it, default) }

  /** Declares a `uniform float2` for a coordinate or direction (y-down local px). Handle: [UOffset]. */
  protected fun uniform(default: Offset): UniformProvider<UOffset> =
    registerHandle("float2", default) { UOffset(it, default) }

  /** Declares a `uniform float2` for a size. Handle: [USize]. */
  protected fun uniform(default: Size): UniformProvider<USize> =
    registerHandle("float2", default) { USize(it, default) }

  /** Declares a `uniform int` (Android `setIntUniform` / skiko `uniform(Int)`). Handle: [UInt1]. */
  protected fun uniform(default: Int): UniformProvider<UInt1> =
    registerHandle("int", default) { UInt1(it, default) }

  /** Declares a `uniform float3`. Handle: [UVec3]. */
  protected fun uniform3(default: FloatArray): UniformProvider<UVec3> {
    require(default.size == 3) { "uniform3 default must have size 3, was ${default.size}" }
    return registerHandle("float3", default) { UVec3(it, default.copyOf()) }
  }

  /** Declares a `uniform float4`. Handle: [UVec4]. */
  protected fun uniform4(default: FloatArray): UniformProvider<UVec4> {
    require(default.size == 4) { "uniform4 default must have size 4, was ${default.size}" }
    return registerHandle("float4", default) { UVec4(it, default.copyOf()) }
  }

  /** Declares a fixed-length `uniform float[N]` where `N == default.size`. Handle: [UFloatArray]. */
  protected fun uniform(default: FloatArray): UniformProvider<UFloatArray> =
    registerHandle("float[${default.size}]", default) { UFloatArray(it, default.copyOf()) }

  /**
   * Declares a `layout(color) uniform vec4`. Android converts to the working color space via
   * `setColorUniform` (official guarantee); skiko writes sRGB unpremultiplied `float4` directly.
   * Handle: [UColor].
   */
  protected fun uniformColor(default: Color): UniformProvider<UColor> =
    registerHandle("float4", default, isColor = true) { UColor(it, default) }

  /**
   * Declares a `uniform shader` texture child (noise / mask / displacement map). Android binds a
   * `BitmapShader` via `setInputShader`; skiko binds `Image.makeShader`. Unlike SwiftUI's one-image
   * limit, any number of textures may be declared. Handle: [UTexture].
   */
  protected fun texture(
    default: ImageBitmap? = null,
    tileMode: TileMode = TileMode.Clamp,
  ): UniformProvider<UTexture> =
    registerHandle("shader", default, isTexture = true) { UTexture(it, default, tileMode) }

  // Provider whose slot index is the current registration size at delegate time, so the handle and
  // its schema entry are appended together in declaration order.
  private inline fun <H : UniformHandle> registerHandle(
    glslType: String,
    default: Any?,
    isColor: Boolean = false,
    isTexture: Boolean = false,
    crossinline makeHandle: (slot: Int) -> H,
  ): UniformProvider<H> = UniformProvider { name ->
    val handle = makeHandle(registered.size)
    registered += UniformEntry(name, glslType, default, isColor, isTexture)
    registeredHandles += handle
    handle
  }
}

/**
 * `PropertyDelegateProvider` that registers a uniform slot under the delegated property's name and
 * returns a read-only handle. Naming happens in `provideDelegate`, which is where the property name
 * is available without reflection.
 */
@ExperimentalMirage
public class UniformProvider<H : Any> internal constructor(
  private val register: (name: String) -> H,
) : PropertyDelegateProvider<MirageParams, ReadOnlyProperty<MirageParams, H>> {

  override fun provideDelegate(
    thisRef: MirageParams,
    property: KProperty<*>,
  ): ReadOnlyProperty<MirageParams, H> {
    val handle = register(property.name)
    return ReadOnlyProperty { _, _ -> handle }
  }
}

/** Common contract of a uniform handle: a typed slot reference into the per-draw param buffer. */
@ExperimentalMirage
public sealed interface UniformHandle {
  /** The binding slot index, equal to the declaration order in [MirageParams]. */
  public val slot: Int
}

/** A scalar `float` uniform slot. */
@ExperimentalMirage
public class UFloat internal constructor(override val slot: Int, public var value: Float) :
  UniformHandle {
  public operator fun invoke(v: Float) {
    value = v
  }
}

/** A `float2` uniform slot carrying a coordinate or direction. */
@ExperimentalMirage
public class UOffset internal constructor(override val slot: Int, public var value: Offset) :
  UniformHandle {
  public operator fun invoke(v: Offset) {
    value = v
  }

  public operator fun invoke(x: Float, y: Float) {
    value = Offset(x, y)
  }
}

/** A `float2` uniform slot carrying a size. */
@ExperimentalMirage
public class USize internal constructor(override val slot: Int, public var value: Size) :
  UniformHandle {
  public operator fun invoke(v: Size) {
    value = v
  }
}

/** An `int` uniform slot. */
@ExperimentalMirage
public class UInt1 internal constructor(override val slot: Int, public var value: Int) :
  UniformHandle {
  public operator fun invoke(v: Int) {
    value = v
  }
}

/** A `float3` uniform slot. The backing array is always length 3. */
@ExperimentalMirage
public class UVec3 internal constructor(override val slot: Int, public var value: FloatArray) :
  UniformHandle {
  public operator fun invoke(v: FloatArray) {
    require(v.size == 3) { "UVec3 value must have size 3, was ${v.size}" }
    value = v.copyOf()
  }

  public operator fun invoke(x: Float, y: Float, z: Float) {
    value = floatArrayOf(x, y, z)
  }
}

/** A `float4` uniform slot. The backing array is always length 4. */
@ExperimentalMirage
public class UVec4 internal constructor(override val slot: Int, public var value: FloatArray) :
  UniformHandle {
  public operator fun invoke(v: FloatArray) {
    require(v.size == 4) { "UVec4 value must have size 4, was ${v.size}" }
    value = v.copyOf()
  }

  public operator fun invoke(x: Float, y: Float, z: Float, w: Float) {
    value = floatArrayOf(x, y, z, w)
  }
}

/** A fixed-length `float[N]` uniform slot. */
@ExperimentalMirage
public class UFloatArray internal constructor(
  override val slot: Int,
  public var value: FloatArray,
) : UniformHandle {
  public operator fun invoke(v: FloatArray) {
    value = v.copyOf()
  }
}

/** A `layout(color) vec4` uniform slot. */
@ExperimentalMirage
public class UColor internal constructor(override val slot: Int, public var value: Color) :
  UniformHandle {
  public operator fun invoke(v: Color) {
    value = v
  }
}

/** A `uniform shader` texture-child slot, carrying its bitmap and tile mode. */
@ExperimentalMirage
public class UTexture internal constructor(
  override val slot: Int,
  public var value: ImageBitmap?,
  public var tileMode: TileMode,
) : UniformHandle {
  public operator fun invoke(v: ImageBitmap?) {
    value = v
  }
}
