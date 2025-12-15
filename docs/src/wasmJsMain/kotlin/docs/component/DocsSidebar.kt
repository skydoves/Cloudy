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
package docs.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import cloudydemo.docs.generated.resources.Res
import cloudydemo.docs.generated.resources.cloudy_transparent
import org.jetbrains.compose.resources.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import docs.navigation.DocsRoute
import docs.theme.DocsTheme

@Composable
fun DocsSidebar(
  currentRoute: DocsRoute,
  onNavigate: (DocsRoute) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .width(260.dp)
      .fillMaxHeight()
      .background(DocsTheme.colors.sidebarBackground)
      .verticalScroll(rememberScrollState())
      .padding(vertical = 16.dp),
  ) {
    // Logo/Header
    SidebarHeader(onNavigate = onNavigate)

    Spacer(modifier = Modifier.height(24.dp))

    // Home
    SidebarItem(
      route = DocsRoute.Home,
      icon = Icons.Default.Home,
      isSelected = currentRoute == DocsRoute.Home,
      onClick = { onNavigate(DocsRoute.Home) },
    )

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = DocsTheme.colors.divider, thickness = 1.dp)
    Spacer(modifier = Modifier.height(16.dp))

    // Guide Section
    SidebarSection(
      title = "Guide",
      icon = Icons.Default.Book,
    )
    DocsRoute.guideRoutes.forEach { route ->
      SidebarItem(
        route = route,
        isSelected = currentRoute == route,
        onClick = { onNavigate(route) },
        indented = true,
      )
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = DocsTheme.colors.divider, thickness = 1.dp)
    Spacer(modifier = Modifier.height(16.dp))

    // API Reference Section
    SidebarSection(
      title = "API Reference",
      icon = Icons.Default.Code,
    )
    DocsRoute.apiRoutes.forEach { route ->
      SidebarItem(
        route = route,
        isSelected = currentRoute == route,
        onClick = { onNavigate(route) },
        indented = true,
      )
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = DocsTheme.colors.divider, thickness = 1.dp)
    Spacer(modifier = Modifier.height(16.dp))

    // Playground
    SidebarItem(
      route = DocsRoute.Playground,
      icon = Icons.Default.PlayArrow,
      isSelected = currentRoute == DocsRoute.Playground,
      onClick = { onNavigate(DocsRoute.Playground) },
    )
  }
}

@Composable
private fun SidebarHeader(onNavigate: (DocsRoute) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onNavigate(DocsRoute.Home) }
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Image(
      painter = painterResource(Res.drawable.cloudy_transparent),
      contentDescription = "Cloudy",
      modifier = Modifier.size(32.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = "Cloudy",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onSurface,
    )
  }
}

@Composable
private fun SidebarSection(
  title: String,
  icon: ImageVector,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = DocsTheme.colors.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = title,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = DocsTheme.colors.onSurfaceVariant,
      letterSpacing = 0.5.sp,
    )
  }
}

@Composable
private fun SidebarItem(
  route: DocsRoute,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  indented: Boolean = false,
) {
  val backgroundColor = when {
    isSelected -> DocsTheme.colors.sidebarActive
    else -> DocsTheme.colors.sidebarBackground
  }

  val textColor = when {
    isSelected -> DocsTheme.colors.primary
    else -> DocsTheme.colors.onSurface
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 2.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(backgroundColor)
      .clickable(onClick = onClick)
      .padding(
        start = if (indented) 32.dp else 16.dp,
        end = 16.dp,
        top = 10.dp,
        bottom = 10.dp,
      ),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = textColor,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
      }
      Text(
        text = route.title,
        style = DocsTheme.typography.bodySmall,
        color = textColor,
        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
      )
    }
  }
}
