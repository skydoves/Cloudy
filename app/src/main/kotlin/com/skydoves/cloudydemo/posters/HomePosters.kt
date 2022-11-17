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

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.skydoves.cloudy.Cloudy
import com.skydoves.cloudydemo.model.MockUtil
import com.skydoves.cloudydemo.model.Poster
import com.skydoves.cloudydemo.theme.PosterTheme
import com.skydoves.landscapist.glide.GlideImage
import kotlinx.coroutines.delay

@Composable
fun HomePosters(
  posters: List<Poster>
) {
  var applyBlur by remember { mutableStateOf(false) }

  LaunchedEffect(key1 = Unit) {
    delay(500)
    applyBlur = true
  }

  Cloudy(
    radius = 20,
    key1 = applyBlur
  ) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(2)
    ) {
      items(key = { it.id }, items = posters) {
        HomePoster(poster = it)
      }
    }
  }
}

@Composable
private fun HomePoster(
  modifier: Modifier = Modifier,
  poster: Poster
) {
  Surface(
    modifier = modifier.padding(4.dp),
    color = MaterialTheme.colors.background,
    elevation = 8.dp,
    shape = RoundedCornerShape(8.dp)
  ) {
    ConstraintLayout {
      val (image, title, content) = createRefs()
      GlideImage(
        modifier = Modifier
          .aspectRatio(0.8f)
          .constrainAs(image) {
            centerHorizontallyTo(parent)
            top.linkTo(parent.top)
          },
        imageModel = { poster.image }
      )

      Text(
        modifier = Modifier
          .constrainAs(title) {
            centerHorizontallyTo(parent)
            top.linkTo(image.bottom)
          }
          .padding(8.dp),
        text = poster.name,
        style = MaterialTheme.typography.h2,
        textAlign = TextAlign.Center
      )

      Text(
        modifier = Modifier
          .constrainAs(content) {
            centerHorizontallyTo(parent)
            top.linkTo(title.bottom)
          }
          .padding(horizontal = 8.dp)
          .padding(bottom = 12.dp),
        text = poster.playtime,
        style = MaterialTheme.typography.body1,
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
@Preview(name = "HomePoster Light Theme")
private fun HomePosterPreviewLight() {
  PosterTheme(darkTheme = false) {
    HomePoster(
      poster = MockUtil.getMockPoster()
    )
  }
}

@Composable
@Preview(name = "HomePoster Dark Theme")
private fun HomePosterPreviewDark() {
  PosterTheme(darkTheme = true) {
    HomePoster(
      poster = MockUtil.getMockPoster()
    )
  }
}
