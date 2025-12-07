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
package demo.model

/**
 * WASM implementation uses CORS-friendly image URLs (picsum.photos)
 * since browser security policies block cross-origin requests to GitHub user content.
 */
internal actual object MockUtil {
  actual fun getMockPosters(): List<Poster> = createMockPostersWithImages(
    images = listOf(
      "https://picsum.photos/seed/frozen2/400/600",
      "https://picsum.photos/seed/toystory4/400/600",
      "https://picsum.photos/seed/zootopia/400/600",
      "https://picsum.photos/seed/findingdory/400/600",
      "https://picsum.photos/seed/bambi/400/600",
      "https://picsum.photos/seed/coco/400/600",
      "https://picsum.photos/seed/alice/400/600",
      "https://picsum.photos/seed/lionking/400/600",
    ),
  )

  actual fun getMockPoster(): Poster = getMockPosters().shuffled().first()
}
