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
package demo.theme

import androidx.compose.ui.unit.dp

/**
 * Shared dimension tokens for the demo app.
 */
internal object Dimens {
  /** Maximum content width for responsive layout on large screens */
  val maxContentWidth = 600.dp

  /** Card corner radius — softer, more rounded for a polished feel */
  val cardCornerRadius = 22.dp

  /** Corner radius for inner thumbnails / media */
  val thumbCornerRadius = 16.dp

  /** Card elevation — light, since depth comes from the glass border, not a hard shadow */
  val cardElevation = 2.dp

  /** Outer screen padding */
  val screenPadding = 20.dp

  /** Standard content padding */
  val contentPadding = 16.dp

  /** Standard item spacing */
  val itemSpacing = 12.dp

  /** Vertical spacing between list items */
  val listItemSpacing = 14.dp

  /** Leading thumbnail size in list rows */
  val thumbnailSize = 72.dp

  /** Hairline border width for glass cards */
  val hairline = 1.dp
}
