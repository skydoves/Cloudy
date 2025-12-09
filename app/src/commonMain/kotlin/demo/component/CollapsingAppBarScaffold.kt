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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import demo.theme.disneyBluePrimary

/**
 * A scaffold with a Material3 LargeTopAppBar that collapses on scroll.
 * Provides a modern, dynamic app bar experience.
 *
 * @param title The title to display in the app bar.
 * @param onBackClick Optional callback for back navigation. If null, no back button is shown.
 * @param modifier Modifier to apply to the scaffold.
 * @param content The content to display below the app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollapsingAppBarScaffold(
  title: String,
  onBackClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  content: @Composable (PaddingValues, TopAppBarScrollBehavior) -> Unit,
) {
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  Scaffold(
    modifier = modifier
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      LargeTopAppBar(
        title = {
          Text(
            text = title,
            fontWeight = FontWeight.Bold,
          )
        },
        navigationIcon = {
          if (onBackClick != null) {
            BackButton(
              onClick = onBackClick,
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = disneyBluePrimary,
          scrolledContainerColor = disneyBluePrimary,
          titleContentColor = MaterialTheme.colorScheme.onPrimary,
          navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { paddingValues ->
    content(paddingValues, scrollBehavior)
  }
}
