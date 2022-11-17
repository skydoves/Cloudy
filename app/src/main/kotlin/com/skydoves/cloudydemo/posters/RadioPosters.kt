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
package com.skydoves.cloudydemo.posters

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.skydoves.cloudydemo.model.MockUtil
import com.skydoves.cloudydemo.model.Poster
import com.skydoves.cloudydemo.theme.PosterTheme
import com.skydoves.landscapist.glide.GlideImage

@Composable
fun RadioPosters(
  modifier: Modifier = Modifier,
  posters: List<Poster>
) {
  val listState = rememberLazyListState()
  LazyColumn(
    modifier = modifier,
    state = listState,
    contentPadding = PaddingValues(4.dp)
  ) {
    items(
      items = posters,
      key = { it.id },
      itemContent = { poster ->
        RadioPoster(
          poster = poster
        )
      }
    )
  }
}

@Composable
fun RadioPoster(
  modifier: Modifier = Modifier,
  poster: Poster
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(4.dp),
    color = MaterialTheme.colors.onBackground,
    elevation = 8.dp,
    shape = RoundedCornerShape(8.dp)
  ) {
    ConstraintLayout(
      modifier = Modifier.padding(8.dp)
    ) {
      val (image, title, content) = createRefs()

      GlideImage(
        modifier = Modifier
          .constrainAs(image) {
            centerVerticallyTo(parent)
            end.linkTo(title.start)
          }
          .height(64.dp)
          .aspectRatio(1f)
          .clip(RoundedCornerShape(4.dp)),
        imageModel = { poster.image }
      )

      Text(
        modifier = Modifier
          .constrainAs(title) {
            start.linkTo(image.end)
          }
          .padding(horizontal = 12.dp),
        text = poster.name,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.h2
      )

      Text(
        modifier = Modifier
          .constrainAs(content) {
            start.linkTo(image.end)
            top.linkTo(title.bottom)
          }
          .padding(start = 12.dp, top = 4.dp),
        text = poster.playtime,
        style = MaterialTheme.typography.body2
      )
    }
  }
}

@Composable
@Preview(name = "RadioPoster Light")
private fun RadioPosterPreviewLight() {
  PosterTheme(darkTheme = false) {
    RadioPoster(
      poster = MockUtil.getMockPoster()
    )
  }
}

@Composable
@Preview(name = "RadioPoster Dark")
private fun RadioPosterPreviewDark() {
  PosterTheme(darkTheme = true) {
    RadioPoster(
      poster = MockUtil.getMockPoster()
    )
  }
}
