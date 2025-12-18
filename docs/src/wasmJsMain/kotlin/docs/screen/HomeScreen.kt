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
package docs.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cloudydemo.docs.generated.resources.Res
import cloudydemo.docs.generated.resources.cloudy_transparent
import docs.navigation.DocsRoute
import docs.theme.DocsTheme
import org.jetbrains.compose.resources.painterResource

@Composable
fun HomeScreen(onNavigate: (DocsRoute) -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    // Hero Section
    HeroSection(onNavigate = onNavigate)

    Spacer(modifier = Modifier.height(48.dp))

    // Features Section
    FeaturesSection()

    Spacer(modifier = Modifier.height(48.dp))

    // Quick Links Section
    QuickLinksSection(onNavigate = onNavigate)
  }
}

@Composable
private fun HeroSection(onNavigate: (DocsRoute) -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Logo
    Image(
      painter = painterResource(Res.drawable.cloudy_transparent),
      contentDescription = "Cloudy",
      modifier = Modifier.size(80.dp),
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = "Cloudy",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Cross-platform blur effects for Compose Multiplatform",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(32.dp))

    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Button(
        onClick = { onNavigate(DocsRoute.GettingStarted) },
        colors = ButtonDefaults.buttonColors(
          containerColor = DocsTheme.colors.primary,
        ),
      ) {
        Icon(
          imageVector = Icons.Default.Rocket,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Get Started")
      }

      OutlinedButton(
        onClick = { onNavigate(DocsRoute.Playground) },
      ) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Playground")
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeaturesSection() {
  Column {
    Text(
      text = "Features",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(24.dp))

    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      FeatureCard(
        icon = Icons.Default.PhoneIphone,
        title = "Cross-Platform",
        description = "Works on Android, iOS, macOS, Desktop (JVM), and Web (WASM)",
      )

      FeatureCard(
        icon = Icons.Default.BlurOn,
        title = "Smooth & Seamless",
        description = "Silky smooth blur animations with minimal overhead, " +
          "seamlessly integrating into your Compose UI",
      )

      FeatureCard(
        icon = Icons.Default.Gradient,
        title = "Progressive Blur",
        description = "Create gradient blur effects with TopToBottom, BottomToTop, and Edges modes",
      )

      FeatureCard(
        icon = Icons.Default.Android,
        title = "Legacy Support",
        description = "CPU-based blur for Android API 23-30 using native C++ SIMD optimizations",
      )

      FeatureCard(
        icon = Icons.Default.Code,
        title = "Kotlin First",
        description = "Built with Kotlin Multiplatform for seamless integration with Compose apps",
      )
    }
  }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, description: String) {
  Card(
    modifier = Modifier
      .width(280.dp)
      .height(180.dp),
    colors = CardDefaults.cardColors(
      containerColor = DocsTheme.colors.surface,
    ),
    shape = RoundedCornerShape(12.dp),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = DocsTheme.colors.primary,
        modifier = Modifier.size(32.dp),
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = title,
        style = DocsTheme.typography.h3,
        color = DocsTheme.colors.onSurface,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = description,
        style = DocsTheme.typography.bodySmall,
        color = DocsTheme.colors.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun QuickLinksSection(onNavigate: (DocsRoute) -> Unit) {
  Column {
    Text(
      text = "Quick Links",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(24.dp))

    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      QuickLinkCard(
        title = "Installation",
        description = "Add Cloudy to your project",
        onClick = { onNavigate(DocsRoute.Installation) },
      )

      QuickLinkCard(
        title = "API Reference",
        description = "Explore the full API",
        onClick = { onNavigate(DocsRoute.ApiCloudy) },
      )

      QuickLinkCard(
        title = "Playground",
        description = "Try blur effects live",
        onClick = { onNavigate(DocsRoute.Playground) },
      )
    }
  }
}

@Composable
private fun QuickLinkCard(title: String, description: String, onClick: () -> Unit) {
  Card(
    onClick = onClick,
    modifier = Modifier.width(200.dp),
    colors = CardDefaults.cardColors(
      containerColor = DocsTheme.colors.surfaceVariant,
    ),
    shape = RoundedCornerShape(12.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
    ) {
      Text(
        text = title,
        style = DocsTheme.typography.body,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.primary,
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = description,
        style = DocsTheme.typography.bodySmall,
        color = DocsTheme.colors.onSurfaceVariant,
      )
    }
  }
}
