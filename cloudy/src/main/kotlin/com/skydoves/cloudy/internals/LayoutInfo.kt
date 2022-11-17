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
package com.skydoves.cloudy.internals

/**
 * A wrapper class that contains layout information to draw bitmap.
 *
 * @param xOffset The x-offset of the layout.
 * @param yOffset The y-offset of the layout.
 * @param width The measured width size of the layout.
 * @param height The measured height size of the layout.
 */
internal data class LayoutInfo constructor(
  val xOffset: Int = 0,
  val yOffset: Int = 0,
  val width: Int = 0,
  val height: Int = 0
)
