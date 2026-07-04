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
 * A compiled program together with the backend handle it produced. The pair is what a node holds and
 * draws through: [compiled] carries the schema the binder walks each draw, [backend] carries the live
 * GPU program the [UniformSink] writes into.
 */
internal class CachedProgram(val compiled: CompiledProgram, val backend: MirageBackendProgram)

/**
 * Process-wide compiled-program cache, keyed by the **generated source text** (not by [Optic]
 * identity) so that two optics that lower to the same kernel — e.g. the chromatic preset family, or
 * an optic hoisted into two different `val`s — share one GPU program instead of compiling twice.
 *
 * ## Ownership
 * This is a process-wide singleton, deliberately **not** owned by any node's attach/detach lifecycle.
 * A per-node cache would recompile on every re-attach and could not be pre-warmed; keeping it here
 * lets a future `warmUp()` and an eviction policy (P2) attach without moving ownership.
 *
 * ## Concurrency
 * The draw thread reads and fills this, and different draw targets can race on first compile, so the
 * map is an immutable snapshot swapped under a lock-free copy-on-write ([AtomicReference] +
 * compare-and-set). The insert loop re-checks after a lost CAS and returns the concurrent winner, so
 * a given source resolves to exactly one canonical [CachedProgram] instance even under contention —
 * two racing threads may each *compile* once (a rare, harmless duplicate the loser discards), but
 * only one program is ever stored and handed out.
 *
 * LRU / trim-on-memory is intentionally absent in M1 (P2): the map is unbounded but real.
 */
internal object MirageProgramCache {

  private val entries = AtomicReference<Map<String, CachedProgram>>(emptyMap())

  /**
   * Returns the cached program for [optic] under [dialect], compiling and caching it on first use.
   * Returns `null` when the platform cannot build the program right now (Android API < 33), which the
   * caller treats as a draw-time no-op.
   */
  fun obtain(optic: Optic<*>, dialect: Dialect): CachedProgram? {
    val compiled = MirageCompiler.compile(optic, dialect)
    val key = compiled.source

    entries.load()[key]?.let { return it }

    // Build the backend program once for this call. Null means the platform cannot support it (e.g.
    // API < 33); nothing to cache, the caller no-ops.
    val backend = createBackendProgram(compiled) ?: return null
    val fresh = CachedProgram(compiled, backend)

    // Copy-on-write insert. On a lost CAS, re-read: if a concurrent thread stored this key, adopt its
    // (canonical) instance and drop ours; otherwise retry with the newer snapshot.
    while (true) {
      val current = entries.load()
      current[key]?.let { return it }
      val next = current + (key to fresh)
      if (entries.compareAndSet(current, next)) return fresh
    }
  }
}
