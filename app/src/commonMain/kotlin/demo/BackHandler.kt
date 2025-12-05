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
 * Registers a composable back navigation handler that invokes the given callback when the system back action occurs.
 *
 * When `enabled` is true the handler is active and `onBack` will be called in response to a platform back/navigation event;
 * when `enabled` is false the handler is inactive and will not intercept back events.
 *
 * @param enabled Controls whether the back handler is active. Defaults to `true`.
 * @param onBack Callback invoked when the system back action is triggered while this handler is active.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)