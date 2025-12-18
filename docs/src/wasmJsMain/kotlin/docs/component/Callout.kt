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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import docs.theme.DocsTheme

enum class CalloutType {
  INFO,
  WARNING,
  NOTE,
}

@Composable
fun Callout(text: String, modifier: Modifier = Modifier, type: CalloutType = CalloutType.INFO) {
  val (backgroundColor, iconColor, icon) = when (type) {
    CalloutType.INFO -> Triple(
      DocsTheme.colors.primary.copy(alpha = 0.15f),
      DocsTheme.colors.primary,
      Icons.Default.Info,
    )
    CalloutType.WARNING -> Triple(
      DocsTheme.colors.warning.copy(alpha = 0.15f),
      DocsTheme.colors.warning,
      Icons.Default.Warning,
    )
    CalloutType.NOTE -> Triple(
      DocsTheme.colors.secondary.copy(alpha = 0.15f),
      DocsTheme.colors.secondary,
      Icons.Default.Info,
    )
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(backgroundColor)
      .padding(16.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = iconColor,
      modifier = Modifier.size(20.dp),
    )

    Spacer(modifier = Modifier.width(12.dp))

    Text(
      text = text,
      style = DocsTheme.typography.bodySmall,
      color = DocsTheme.colors.onSurface,
    )
  }
}
