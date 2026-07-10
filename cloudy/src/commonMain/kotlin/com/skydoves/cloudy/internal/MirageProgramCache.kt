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
@file:OptIn(ExperimentalMirage::class, ExperimentalAtomicApi::class)

package com.skydoves.cloudy.internal

import com.skydoves.cloudy.ExperimentalMirage
import com.skydoves.cloudy.Optic
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A compiled program paired with the backend handle it draws through. This is what a node holds and
 * draws through: [compiled] carries the schema the binder walks each draw, [backend] carries the live
 * GPU program the [UniformSink] writes into.
 *
 * The [backend] is **shared per source** (see [MirageProgramCache]); [compiled] is **per call**. Two
 * optics that lower to the same kernel share one GPU program but each keeps its own [compiled], so its
 * own [UniformSchema] defaults reach the binder. Only [CompiledProgram.schema]'s per-entry defaults
 * differ between such optics — the `uses*` flags, category, and source are functions of the source
 * text and are therefore identical.
 */
internal class CachedProgram(val compiled: CompiledProgram, val backend: MirageBackendProgram)

/**
 * Process-wide **backend** cache, keyed by the **generated source text** (not by [Optic] identity) so
 * that two optics that lower to the same kernel — e.g. the chromatic preset family, or an optic
 * hoisted into two different `val`s — share one GPU program instead of compiling twice.
 *
 * ## What is shared vs per-optic
 * Only the compiled GPU program (the expensive artifact) is shared. Each [obtain] returns a fresh
 * [CachedProgram] wrapping **this call's** [CompiledProgram], so the caller always sees its own
 * optic's [UniformSchema] defaults. Sharing the compiled schema instead would alias every same-source
 * optic to whichever compiled first, collapsing all thin-film looks to a single default set.
 *
 * ## Ownership
 * This is a process-wide singleton, deliberately **not** owned by any node's attach/detach lifecycle.
 * A per-node cache would recompile on every re-attach and could not be pre-warmed.
 *
 * ## Concurrency
 * The draw thread reads and fills this, and different draw targets can race on first compile, so the
 * map is an immutable snapshot swapped under a lock-free copy-on-write ([AtomicReference] +
 * compare-and-set). The insert loop re-checks after a lost CAS and returns the concurrent winner, so
 * a given source resolves to exactly one canonical [MirageBackendProgram] instance even under
 * contention — two racing threads may each *compile* once (a rare, harmless duplicate the loser
 * discards), but only one backend is ever stored and handed out.
 *
 * The map is unbounded: it holds one backend per distinct kernel source, with no eviction.
 */
internal object MirageProgramCache {

  private val backends = AtomicReference<Map<String, MirageBackendProgram>>(emptyMap())

  /**
   * Returns the program for [optic] under [dialect], compiling and caching the backend on first use.
   * Returns `null` when the platform cannot build the program right now (Android API < 33), which the
   * caller treats as a draw-time no-op.
   *
   * The returned [CachedProgram] carries **this call's** [CompiledProgram] (so this optic's schema
   * defaults reach the binder) over the source-shared backend.
   */
  fun obtain(optic: Optic<*>, dialect: Dialect): CachedProgram? {
    val compiled = MirageCompiler.compile(optic, dialect)
    val backend = obtainBackend(compiled) ?: return null
    return CachedProgram(compiled, backend)
  }

  /**
   * Resolves the source-shared backend for [compiled], compiling it once per source. Returns `null`
   * when the platform cannot build it right now (e.g. API < 33); nothing is cached and the caller
   * no-ops.
   */
  private fun obtainBackend(compiled: CompiledProgram): MirageBackendProgram? {
    val key = compiled.source

    backends.load()[key]?.let { return it }

    // Build the backend once for this source. Null means the platform cannot support it (e.g.
    // API < 33); nothing to cache, the caller no-ops.
    val fresh = createBackendProgram(compiled) ?: return null

    // Copy-on-write insert. On a lost CAS, re-read: if a concurrent thread stored this key, adopt its
    // (canonical) instance and drop ours; otherwise retry with the newer snapshot.
    while (true) {
      val current = backends.load()
      current[key]?.let { return it }
      val next = current + (key to fresh)
      if (backends.compareAndSet(current, next)) return fresh
    }
  }
}

/**
 * True when at least one of [stages]'s programs renders **on a self-lit content node** under [dialect]
 * — i.e. the plan produces some output there. False when every stage is unsupported (e.g. a lens optic
 * on Android below API 33, or any optic on the API 29-32 GLES band, which is backdrop-only), which is
 * when a [com.skydoves.cloudy.MirageFallback.Content] should stand in.
 *
 * A [FilterApplication.Blit] stage (the async GLES path) does **not** count as rendering here: only the
 * backdrop node drives that async capture (it has the `Sky.contentVersion` cache key a self-lit node
 * lacks — see [MirageGlesBackdrop]). So a self-lit GLES plan renders nothing and its fallback shows.
 *
 * Uses the same [MirageProgramCache.obtain] + [filterApplication] the self-lit draw loop uses, so
 * "renders" here means exactly what that node will draw. Warming the cache during composition is cheap.
 */
@OptIn(ExperimentalMirage::class)
internal fun planRenders(stages: List<Stage>, dialect: Dialect): Boolean =
  stages.any { stage ->
    // Only program stages have an optic to compile; a blur PlatformFilter renders on its own path and
    // is never gated by a MirageFallback, so it does not participate in this check.
    val optic = when (stage) {
      is Stage.ProgramFilter -> stage.optic
      is Stage.Overlay -> stage.optic
      is Stage.PlatformFilter -> return@any false
    }
    rendersInPlace(MirageProgramCache.obtain(optic, dialect))
  }

/**
 * Whether [cached]'s program renders on a self-lit content node (drawn in place synchronously). Null
 * (unsupported band) and [FilterApplication.Blit] (async, backdrop-only) both render nothing in place.
 */
@OptIn(ExperimentalMirage::class)
internal fun rendersInPlace(cached: CachedProgram?): Boolean =
  cached != null && cached.backend.filterApplication() !is FilterApplication.Blit
