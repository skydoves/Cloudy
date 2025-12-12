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
package demo.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cloudydemo.app.generated.resources.Res
import cloudydemo.app.generated.resources.poster
import demo.component.GridPosterItemLayout
import demo.component.MenuCardLayout
import demo.theme.PosterTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun GridPosterItemPreview() {
  PosterTheme {
    Box(modifier = Modifier.width(200.dp)) {
      GridPosterItemLayout(
        name = "Frozen",
        blurRadius = 10,
      ) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview
@Composable
private fun GridPosterItemNoBlurPreview() {
  PosterTheme {
    Box(modifier = Modifier.width(200.dp)) {
      GridPosterItemLayout(
        name = "Moana",
        blurRadius = 0,
      ) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview
@Composable
private fun GridPosterItemHighBlurPreview() {
  PosterTheme {
    Box(modifier = Modifier.width(200.dp)) {
      GridPosterItemLayout(
        name = "High Blur",
        blurRadius = 25,
      ) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview
@Composable
private fun MenuCardPreview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      MenuCardLayout(
        title = "Blur Demo",
        description = "Explore various blur effects",
        onClick = {},
        blurRadius = 10,
      ) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview
@Composable
private fun MenuCardHighBlurPreview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      MenuCardLayout(
        title = "High Blur",
        description = "Strong blur effect applied",
        onClick = {},
        blurRadius = 25,
      ) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview
@Composable
private fun MenuCardDarkPreview() {
  PosterTheme(darkTheme = true) {
    Box(modifier = Modifier.padding(16.dp)) {
      MenuCardLayout(
        title = "Dark Theme",
        description = "Menu card in dark mode",
        onClick = {},
      ) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}
