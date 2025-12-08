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
package demo.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import demo.theme.Dimens

/**
 * A container that limits content width to [Dimens.maxContentWidth] and centers it horizontally.
 * This ensures consistent content width across large screens (Desktop, WASM, tablets).
 *
 * @param modifier Modifier to apply to the outer container.
 * @param content The content to display within the constrained width.
 */
@Composable
internal fun MaxWidthContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Box(
      modifier = Modifier.widthIn(max = Dimens.maxContentWidth),
    ) {
      content()
    }
  }
}
