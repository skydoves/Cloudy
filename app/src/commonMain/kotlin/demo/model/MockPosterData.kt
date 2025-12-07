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

internal data class PosterContent(
  val name: String,
  val release: String,
  val playtime: String,
  val description: String,
)

internal val posterContents = listOf(
  PosterContent(
    "Frozen II",
    "2019",
    "1 h 43 min",
    "Frozen II, also known as Frozen 2, is a 2019 American 3D computer-animated " +
      "musical fantasy film produced by Walt Disney Animation Studios. " +
      "The 58th animated film produced by the studio, it is the sequel to the 2013 film " +
      "Frozen and features the return of directors Chris Buck and Jennifer Lee.",
  ),
  PosterContent(
    "Toy Story 4",
    "2019",
    "1 h 40 min",
    "Toy Story 4 is a 2019 American computer-animated comedy film produced by Pixar " +
      "Animation Studios for Walt Disney Pictures. It is the fourth installment in Pixar's " +
      "Toy Story series and the sequel to Toy Story 3 (2010).",
  ),
  PosterContent(
    "Zootopia",
    "2016",
    "1 h 50 min",
    "Zootopia is a 2016 American 3D computer-animated comedy film produced by " +
      "Walt Disney Animation Studios and released by Walt Disney Pictures. It is the 55th " +
      "Disney animated feature film.",
  ),
  PosterContent(
    "Finding Dory",
    "2016",
    "1 h 45 min",
    "Finding Dory is a 2016 American 3D computer-animated adventure film produced by " +
      "Pixar Animation Studios and released by Walt Disney Pictures. The film is a " +
      "sequel to 2003's Finding Nemo.",
  ),
  PosterContent(
    "Bambi",
    "1942",
    "1 h 10 min",
    "Bambi is a 1942 American animated film produced by Walt Disney Productions and " +
      "based on the 1923 book Bambi, a Life in the Woods by Austrian author Felix Salten.",
  ),
  PosterContent(
    "Coco",
    "2017",
    "1 h 45 min",
    "Coco is a 2017 American 3D computer-animated fantasy film produced by Pixar " +
      "Animation Studios and released by Walt Disney Pictures. The film follows a " +
      "12-year-old boy named Miguel who is accidentally transported to the Land of the Dead.",
  ),
  PosterContent(
    "Alice in Wonderland",
    "2010",
    "1 h 48 min",
    "Alice in Wonderland is a 2010 American fantasy film directed by Tim Burton " +
      "from a screenplay written by Linda Woolverton. The film stars Mia Wasikowska " +
      "in the title role.",
  ),
  PosterContent(
    "The Lion King",
    "1994",
    "1 h 28 min",
    "The Lion King is a 1994 American animated musical drama film produced by " +
      "Walt Disney Feature Animation and released by Walt Disney Pictures. It is the " +
      "32nd Disney animated feature film.",
  ),
)

internal fun createMockPosters(): List<Poster> = createMockPostersWithImages(
  images = listOf(
    "https://user-images.githubusercontent.com/24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg",
    "https://user-images.githubusercontent.com/24237865/75087934-5a53dc00-553e-11ea-94f1-494c1c68a574.jpg",
    "https://user-images.githubusercontent.com/24237865/75087937-5c1d9f80-553e-11ea-8fc9-a7e520addde0.jpg",
    "https://user-images.githubusercontent.com/24237865/75088201-0ba84100-5542-11ea-8587-0c2823b05351.jpg",
    "https://user-images.githubusercontent.com/24237865/75087801-a56cef80-553c-11ea-9ae5-cf203c6ea8c2.jpg",
    "https://user-images.githubusercontent.com/24237865/75088277-dea85e00-5542-11ea-961b-7f0942cd8f47.jpg",
    "https://user-images.githubusercontent.com/24237865/75088202-0d720480-5542-11ea-85f3-8726e69a9a26.jpg",
    "https://user-images.githubusercontent.com/24237865/75087937-5c1d9f80-553e-11ea-8fc9-a7e520addde0.jpg",
  ),
)

internal fun createMockPostersWithImages(images: List<String>): List<Poster> =
  posterContents.mapIndexed { index, content ->
    Poster(
      name = content.name,
      release = content.release,
      playtime = content.playtime,
      description = content.description,
      image = images.getOrNull(index),
      gif = images.getOrNull(index),
    )
  }
