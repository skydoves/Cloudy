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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.theme.Dimens

/**
 * Reproduction of https://github.com/skydoves/Cloudy/issues/112.
 *
 * Layout that crashed (native RenderThread `SIGSEGV` on API 31+):
 *   - `Modifier.sky(sky)` on the OUTER container that holds a scrolling [LazyColumn].
 *   - A bottom navigation bar that applies `Modifier.cloudy(sky = sky, ...)` to blur whatever
 *     scrolls behind it.
 *
 * On the pre-fix library this layout builds a cyclic RenderNode graph: the sky capture records
 * the cloudy overlay (because the overlay is a descendant of the sky container), while the
 * overlay's blur layer records the sky layer. The two layers reference each other
 * (skyLayer -> blurLayer -> skyLayer), so `RenderNode::prepareTreeImpl` recurses on the render
 * thread until the stack overflows. The list auto-scrolls on entry so the crash (or, post-fix,
 * the smooth blur) is easy to capture without a precisely-timed manual swipe.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Issue112BottomNavScreen(onBackClick: () -> Unit) {
  // sky on the OUTER container — the exact placement that triggers #112.
  val sky = rememberSky()
  val listState = rememberLazyListState()
  var selectedTab by remember { mutableIntStateOf(0) }

  // Auto-scroll up and down a few times so the backdrop blur is redrawn continuously while the
  // screen is being recorded. On the pre-fix library this is when the RenderThread crashes.
  LaunchedEffect(Unit) {
    repeat(6) {
      listState.animateScrollToItem(ITEM_COUNT - 1)
      listState.animateScrollToItem(0)
    }
  }

  CollapsingAppBarScaffold(
    title = "Issue #112 — BottomNav",
    onBackClick = onBackClick,
  ) { paddingValues, _ ->
    MaxWidthContainer(modifier = Modifier.padding(paddingValues)) {
      Box(modifier = Modifier.fillMaxSize().sky(sky)) {
        // Background content captured by sky: a long, scrolling list.
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(Dimens.contentPadding),
          contentPadding = PaddingValues(
            start = Dimens.contentPadding,
            end = Dimens.contentPadding,
            top = Dimens.contentPadding,
            // Leave room so the last rows can scroll out from under the blurred bottom bar.
            bottom = BOTTOM_BAR_HEIGHT.dp + Dimens.contentPadding,
          ),
        ) {
          items(ITEM_COUNT) { index ->
            ColorRow(index)
          }
        }

        // Bottom navigation bar: a fixed, blurred backdrop over the scrolling content.
        Row(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(BOTTOM_BAR_HEIGHT.dp)
            .cloudy(sky = sky, radius = 24)
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          repeat(TAB_COUNT) { tabIndex ->
            TabDot(
              selected = tabIndex == selectedTab,
              onClick = { selectedTab = tabIndex },
            )
          }
        }
      }
    }
  }
}

private const val ITEM_COUNT = 60
private const val TAB_COUNT = 4
private const val BOTTOM_BAR_HEIGHT = 72

@Composable
private fun ColorRow(index: Int) {
  // Alternating colored rows give the blur something high-contrast to sample as it scrolls.
  val colors = remember {
    listOf(
      Color(0xFF1565C0),
      Color(0xFFC62828),
      Color(0xFF2E7D32),
      Color(0xFF6A1B9A),
      Color(0xFFEF6C00),
    )
  }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(72.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(colors[index % colors.size]),
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(
      text = "Item #$index",
      modifier = Modifier.padding(start = 16.dp),
      color = Color.White,
      fontSize = 16.sp,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun TabDot(selected: Boolean, onClick: () -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = Modifier.clickable(onClick = onClick),
  ) {
    Box(
      modifier = Modifier
        .size(if (selected) 28.dp else 22.dp)
        .clip(CircleShape)
        .background(if (selected) Color.White else Color.White.copy(alpha = 0.5f)),
    )
  }
}
