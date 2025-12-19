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
package demo.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.skydoves.cloudy.CloudyProgressive
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens

/**
 * Demo screen showcasing progressive blur effects with background blur.
 * Demonstrates TopToBottom, BottomToTop, and Edges progressive blur types.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressiveBlurDemoScreen(onBackClick: () -> Unit) {
  val posters = remember { MockUtil.getMockPosters() }

  CollapsingAppBarScaffold(
    title = "Progressive Blur",
    onBackClick = onBackClick,
  ) { paddingValues, _ ->
    MaxWidthContainer(modifier = Modifier.padding(paddingValues)) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.contentPadding),
        contentPadding = PaddingValues(Dimens.contentPadding),
      ) {
        item {
          ProgressiveBlurCard(
            title = "Top to Bottom",
            description = "Blur fades from top to bottom",
            imageUrl = posters[0].image,
            progressive = CloudyProgressive.TopToBottom(),
          )
        }
        item {
          ProgressiveBlurCard(
            title = "Bottom to Top",
            description = "Blur fades from bottom to top",
            imageUrl = posters[1].image,
            progressive = CloudyProgressive.BottomToTop(),
          )
        }
        item {
          ProgressiveBlurCard(
            title = "Edges",
            description = "Blur fades from edges toward center",
            imageUrl = posters[2].image,
            progressive = CloudyProgressive.Edges(),
          )
        }
        item {
          ProgressiveBlurCard(
            title = "Custom Top to Bottom",
            description = "start=0.2, end=0.8",
            imageUrl = posters[3].image,
            progressive = CloudyProgressive.TopToBottom(start = 0.2f, end = 0.8f),
          )
        }
        item {
          ProgressiveBlurCard(
            title = "With Tint Color",
            description = "Progressive blur with blue tint overlay",
            imageUrl = posters[4].image,
            progressive = CloudyProgressive.TopToBottom(),
            tintColor = Color.Blue.copy(alpha = 0.2f),
          )
        }
      }
    }
  }
}

/**
 * A card demonstrating progressive blur on an image with text overlay.
 */
@Composable
private fun ProgressiveBlurCard(
  title: String,
  description: String,
  imageUrl: String?,
  progressive: CloudyProgressive,
  tintColor: Color = Color.Transparent,
) {
  val sky = rememberSky()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surface),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f),
    ) {
      // Background image captured by sky
      AsyncImage(
        model = imageUrl,
        contentDescription = title,
        modifier = Modifier
          .fillMaxSize()
          .sky(sky),
        contentScale = ContentScale.Crop,
      )

      // Progressive blur overlay at the bottom
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(100.dp)
          .align(Alignment.BottomCenter)
          .cloudy(
            sky = sky,
            radius = 25,
            progressive = progressive,
            tint = tintColor,
          ),
        contentAlignment = Alignment.BottomStart,
      ) {
        Text(
          text = title,
          modifier = Modifier.padding(16.dp),
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White,
        )
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = description,
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
      }
      Spacer(modifier = Modifier.width(16.dp))
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
          .padding(horizontal = 12.dp, vertical = 6.dp),
      ) {
        Text(
          text = progressive::class.simpleName ?: "Progressive",
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}
