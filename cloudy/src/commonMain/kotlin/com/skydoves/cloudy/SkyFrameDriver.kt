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

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps a [Sky]'s captured backdrop and its [Modifier.cloudy] overlays refreshing while content
 * behind the glass moves (scroll, animation), then parks so the app idles at zero frames.
 *
 * A `Modifier.sky` recorder re-records the backdrop only when its own `draw()` runs, and an overlay
 * re-blurs only when its draw runs. A scroll or a cross-fade of the recorded subtree repaints the
 * subtree but does NOT re-invoke the recorder's or overlays' `draw()`, so the captured backdrop and
 * the composited blur would freeze while sharp content moves behind the glass. The recorder forwards
 * scroll/fling deltas (via a nested-scroll connection) to [onScrollActivity], and the app forwards
 * discrete content changes to [requestRefresh]; both arm a short refresh window during which the
 * driver re-invalidates the recorder (re-capture) and the overlays (re-blur) each frame.
 *
 * The window is measured in TIME, not frame count, so it covers a fixed-duration animation
 * identically at 60Hz and 120Hz (a frame count would halve the wall-clock tail on a 120Hz display).
 *
 * Idle stays at zero frames: the window is re-armed ONLY by real activity ([onScrollActivity] /
 * [requestRefresh]), never by the loop's own invalidations, so it always elapses and the loop exits.
 * [withFrameNanos] does post a Choreographer frame to resume the loop, but once the window has
 * elapsed the loop stops calling it, so nothing keeps the window producing frames.
 */
internal class SkyFrameDriver {

  private val overlays = mutableListOf<() -> Unit>()

  private var recorderScope: CoroutineScope? = null
  private var recorderInvalidate: (() -> Unit)? = null
  private var pumpJob: Job? = null

  // Wall-clock (frame-time) nanos until which the loop should keep refreshing. Re-armed by activity.
  private var refreshUntilNanos: Long = 0L

  private companion object {
    // Tail kept refreshing after the last activity, so the settled post-fling/post-animation frame
    // is captured. Small, so the app idles within ~tens of ms of the motion stopping.
    const val SETTLE_TAIL_MS = 80L
  }

  /** Registers the `Modifier.sky` recorder: [scope] hosts the loop, [invalidate] forces a capture. */
  fun attachRecorder(scope: CoroutineScope, invalidate: () -> Unit) {
    recorderScope = scope
    recorderInvalidate = invalidate
  }

  fun detachRecorder(scope: CoroutineScope) {
    if (recorderScope === scope) {
      pumpJob?.cancel()
      pumpJob = null
      recorderScope = null
      recorderInvalidate = null
      refreshUntilNanos = 0L
    }
  }

  /** Registers an overlay's re-blur invalidator. */
  fun addOverlay(invalidate: () -> Unit) {
    if (overlays.none { it === invalidate }) overlays += invalidate
  }

  fun removeOverlay(invalidate: () -> Unit) {
    overlays.removeAll { it === invalidate }
    if (overlays.isEmpty()) {
      pumpJob?.cancel()
      pumpJob = null
      refreshUntilNanos = 0L
    }
  }

  /**
   * Reports scroll/fling activity behind the glass (forwarded from the recorder's nested-scroll
   * connection). Re-arms the settle tail and starts the loop if it had parked.
   */
  fun onScrollActivity() {
    extendWindow(SETTLE_TAIL_MS)
  }

  /**
   * Requests a refresh window of at least [durationMs] (default the settle tail). The app passes the
   * duration of a discrete content change — e.g. a cross-fade between tabs — so the blur tracks the
   * whole animation instead of freezing partway once a short tail elapses ([Sky.invalidate]).
   */
  fun requestRefresh(durationMs: Long = SETTLE_TAIL_MS) {
    extendWindow(durationMs)
  }

  // Extends the refresh window to at least [durationMs] and (re)starts the loop. The deadline is set
  // off the loop's own frame time (folded in on the next frame), so it is always a real frame clock.
  private fun extendWindow(durationMs: Long) {
    pendingExtensionMs = maxOf(pendingExtensionMs, durationMs)
    ensurePump()
  }

  // Set by extendWindow, folded into refreshUntilNanos by the loop on its next frame (the only place
  // frame-time "now" is known). Cleared once applied.
  private var pendingExtensionMs: Long = 0L

  private fun ensurePump() {
    if (pumpJob?.isActive == true) return
    if (overlays.isEmpty()) return
    val scope = recorderScope ?: return
    pumpJob = scope.launch {
      try {
        var keepGoing = true
        while (isActive && keepGoing) {
          // withFrameNanos parks until the next window frame and hands back its frame time. It is the
          // only clock the loop trusts: deadline math and the exit test both use this `now`.
          keepGoing = withFrameNanos { now ->
            if (pendingExtensionMs > 0L) {
              refreshUntilNanos = maxOf(refreshUntilNanos, now + pendingExtensionMs * 1_000_000L)
              pendingExtensionMs = 0L
            }
            val active = now < refreshUntilNanos
            if (active) {
              // Re-capture the moved backdrop, then re-blur the overlays against it, this frame.
              recorderInvalidate?.invoke()
              for (i in overlays.indices) overlays[i]()
            }
            // Stay in the loop while still inside the window; the work above schedules the next frame.
            active
          }
        }
      } finally {
        pumpJob = null
      }
    }
  }
}
