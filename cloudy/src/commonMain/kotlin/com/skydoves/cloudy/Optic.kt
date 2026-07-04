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

import com.skydoves.cloudy.internal.OpticCategory

/**
 * A named mirage optic: a shader effect paired with the [MirageParams] subclass that declares its
 * uniforms. An optic is the authored front end — its kernel plus its uniform schema — that the
 * compiler later lowers into a per-dialect [CompiledProgram][com.skydoves.cloudy.internal.CompiledProgram].
 *
 * Build one with the [Companion] factories ([colorize], [composite], [generate], [raw]) rather than
 * constructing subtypes directly; the constructors are `internal` so new fields can be added without
 * breaking callers.
 *
 * ## Reuse and cache identity
 * [equals] / [hashCode] are keyed on `(name, kernel sources, category)` — the [paramsFactory] is
 * **excluded** because it is a lambda (never structurally comparable) and it only *mints* a fresh
 * params instance; two optics with identical name, sources, and category compile to the same GPU
 * program and so must compare equal for source-hash cache keys and dedup. This also lets an optic be
 * hoisted into a top-level `val` and reused across recompositions without reallocating the effect.
 *
 * The equality contract is declared abstract here and implemented per concrete subtype: each subtype
 * supplies its own kernel sources (and, for the [FilterOptic] family, its [OpticCategory]), so the
 * comparable tuple only becomes concrete at the leaf. This is intentionally **not** a `data class`
 * for the same reason as [MirageRecipe]: it holds a function-typed field ([paramsFactory]) for which
 * a generated structural `equals` would be meaningless, and an `internal` constructor keeps the
 * generated-component / `copy` ABI from freezing the field set.
 *
 * @param P The [MirageParams] subclass declaring this optic's uniforms.
 * @property name Stable identifier used in cache keys and diagnostics.
 * @property paramsFactory Mints a fresh params instance per node (one per draw target, reused across
 *   draws). Excluded from [equals] / [hashCode] — see above.
 */
@ExperimentalMirage
public sealed class Optic<P : MirageParams> protected constructor(
  public val name: String,
  internal val paramsFactory: () -> P,
) {
  // Declared abstract, not final: the comparable tuple (name + sources + category) is only complete
  // at the leaf subtype, so each supplies its own implementation over the fields it actually holds.
  abstract override fun equals(other: Any?): Boolean

  abstract override fun hashCode(): Int

  // The companion is a separate ABI class (Optic$Companion); the marker on the outer class does not
  // reach it, so it carries its own to stay out of the stable dumps (same precedent as MirageRecipe).
  @ExperimentalMirage
  public companion object {
    /**
     * Creates a [ColorizeOptic] — a point-wise color transform.
     *
     * The author writes only the kernel `half4 kernel(float2 p, half4 src)`; codegen wraps it with
     * the uniform declarations and the standard content-sampling `main`. Use for effects that map a
     * pixel to a new color without reading its neighbours (tint, curves, grade).
     *
     * @param name Stable identifier for cache keys / diagnostics.
     * @param paramsFactory Mints the optic's [MirageParams]; see [Optic.paramsFactory].
     * @param agsl The Android (AGSL) `kernel` body.
     * @param sksl The Skia (SKSL) `kernel` body for iOS / macOS / Desktop / Wasm.
     */
    public fun <P : MirageParams> colorize(
      name: String,
      paramsFactory: () -> P,
      agsl: String,
      sksl: String,
    ): ColorizeOptic<P> = ColorizeOptic(name, paramsFactory, agsl, sksl)

    /**
     * Creates a [CompositeOptic] — a free-access composite kernel.
     *
     * The author writes the full `half4 main(float2 xy)` directly; codegen supplies only the uniform
     * declarations and the shared lens preamble, leaving content access to the kernel. Use for
     * effects that need arbitrary sampling of the content and shared intermediates (specular,
     * chromatic thin-film).
     *
     * @param name Stable identifier for cache keys / diagnostics.
     * @param paramsFactory Mints the optic's [MirageParams]; see [Optic.paramsFactory].
     * @param agsl The Android (AGSL) `main` body.
     * @param sksl The Skia (SKSL) `main` body for the skiko platforms.
     */
    public fun <P : MirageParams> composite(
      name: String,
      paramsFactory: () -> P,
      agsl: String,
      sksl: String,
    ): CompositeOptic<P> = CompositeOptic(name, paramsFactory, agsl, sksl)

    /**
     * Creates a [GenerateOptic] — a content-free generator for an overlay.
     *
     * The author writes a `half4 main(float2 xy)` that synthesizes pixels from uniforms only; there
     * is no `content` sampler, so referencing content is a compile error. Not a [FilterOptic]: the
     * type system keeps it out of the content-filtering path.
     *
     * @param name Stable identifier for cache keys / diagnostics.
     * @param paramsFactory Mints the optic's [MirageParams]; see [Optic.paramsFactory].
     * @param agsl The Android (AGSL) `main` body.
     * @param sksl The Skia (SKSL) `main` body for the skiko platforms.
     */
    public fun <P : MirageParams> generate(
      name: String,
      paramsFactory: () -> P,
      agsl: String,
      sksl: String,
    ): GenerateOptic<P> = GenerateOptic(name, paramsFactory, agsl, sksl)

    /**
     * Creates a raw [FilterOptic] — an escape hatch with **no codegen**.
     *
     * The dialect sources are used verbatim: the author owns the uniform declarations and must keep
     * them in sync with [P] by hand (a later debug lint cross-checks the two). Always
     * [Composite][OpticCategory.Composite] category, since a raw kernel is assumed to access content.
     *
     * @param name Stable identifier for cache keys / diagnostics.
     * @param paramsFactory Mints the optic's [MirageParams]; see [Optic.paramsFactory].
     * @param agsl The complete Android (AGSL) shader body, uniform declarations included.
     * @param sksl The complete Skia (SKSL) shader body for the skiko platforms.
     */
    public fun <P : MirageParams> raw(
      name: String,
      paramsFactory: () -> P,
      agsl: String,
      sksl: String,
    ): FilterOptic<P> = CompositeOptic(name, paramsFactory, agsl, sksl)
  }
}

/**
 * An [Optic] that filters the content it is attached to — the common supertype accepted by
 * `MirageScope.filter` (wired in a later step).
 *
 * Sealed so the compiler can branch exhaustively on the concrete filtering [category] and so new
 * filter kinds can be added without breaking callers that only construct the provided subtypes.
 *
 * @property agsl The Android (AGSL) source. For [Colorize][OpticCategory.Colorize] and
 *   [Generate][OpticCategory.Generate] this is the `kernel` body; for
 *   [Composite][OpticCategory.Composite] and raw optics it is the full `main` body.
 * @property sksl The Skia (SKSL) counterpart of [agsl].
 * @property category The codegen category this optic lowers into.
 */
@ExperimentalMirage
public sealed class FilterOptic<P : MirageParams> protected constructor(
  name: String,
  paramsFactory: () -> P,
  internal val agsl: String,
  internal val sksl: String,
  internal val category: OpticCategory,
) : Optic<P>(name, paramsFactory) {

  // The comparable tuple is complete for every filter subtype at this level (all hold name + sources
  // + category), so the equality contract is implemented once here rather than per leaf. paramsFactory
  // is excluded on purpose: it is a lambda and only feeds params into the program identified by the
  // sources — two optics with equal name/sources/category compile to the same GPU program.
  final override fun equals(other: Any?): Boolean = this === other ||
    (
      other is FilterOptic<*> &&
        name == other.name &&
        agsl == other.agsl &&
        sksl == other.sksl &&
        category == other.category
      )

  final override fun hashCode(): Int {
    var result = name.hashCode()
    result = result * 31 + agsl.hashCode()
    result = result * 31 + sksl.hashCode()
    result = result * 31 + category.hashCode()
    return result
  }

  final override fun toString(): String =
    "FilterOptic(name=$name, category=$category, agsl=${agsl.length} chars, sksl=${sksl.length} chars)"
}

/**
 * A point-wise color transform. The author writes only `half4 kernel(float2 p, half4 src)`; codegen
 * wraps it with the uniform declarations and the content-sampling `main`. Build via [Optic.colorize].
 */
@ExperimentalMirage
public class ColorizeOptic<P : MirageParams> internal constructor(
  name: String,
  paramsFactory: () -> P,
  agsl: String,
  sksl: String,
) : FilterOptic<P>(name, paramsFactory, agsl, sksl, OpticCategory.Colorize)

/**
 * A free-access composite kernel. The author writes `half4 main(float2 xy)` directly; codegen
 * supplies only the uniform declarations and the shared lens preamble. Use for shared-intermediate
 * effects (specular, chromatic thin-film). Build via [Optic.composite] (or [Optic.raw] for the
 * no-codegen escape hatch).
 */
@ExperimentalMirage
public class CompositeOptic<P : MirageParams> internal constructor(
  name: String,
  paramsFactory: () -> P,
  agsl: String,
  sksl: String,
) : FilterOptic<P>(name, paramsFactory, agsl, sksl, OpticCategory.Composite)

/**
 * A content-free generator for an overlay (wired via `MirageScope.overlay` in a later step). The
 * kernel synthesizes pixels from uniforms only — there is no `content` sampler, so referencing
 * content is a compile error. Deliberately **not** a [FilterOptic]: the type system blocks it from
 * the content-filtering path. Its category is implicitly [Generate][OpticCategory.Generate]. Build
 * via [Optic.generate].
 *
 * @property agsl The Android (AGSL) `main` body.
 * @property sksl The Skia (SKSL) `main` body for the skiko platforms.
 */
@ExperimentalMirage
public class GenerateOptic<P : MirageParams> internal constructor(
  name: String,
  paramsFactory: () -> P,
  internal val agsl: String,
  internal val sksl: String,
) : Optic<P>(name, paramsFactory) {

  // Category is fixed (Generate) for every instance, so it is not part of the comparable tuple — the
  // (name, agsl, sksl) triple already distinguishes generators. paramsFactory excluded as elsewhere.
  override fun equals(other: Any?): Boolean = this === other ||
    (
      other is GenerateOptic<*> &&
        name == other.name &&
        agsl == other.agsl &&
        sksl == other.sksl
      )

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = result * 31 + agsl.hashCode()
    result = result * 31 + sksl.hashCode()
    return result
  }

  override fun toString(): String =
    "GenerateOptic(name=$name, agsl=${agsl.length} chars, sksl=${sksl.length} chars)"
}
