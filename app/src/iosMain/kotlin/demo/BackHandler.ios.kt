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
package demo

import androidx.compose.runtime.Composable

/**
 * No-op back handler for iOS kept for cross-platform compatibility.
 *
 * This composable intentionally does nothing on iOS because the platform has no system back button.
 *
 * @param enabled Whether the back handler is active (ignored on iOS).
 * @param onBack Callback invoked when a back action occurs (never called on iOS).
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
  // iOS doesn't have a system back button, uses swipe gestures instead
}