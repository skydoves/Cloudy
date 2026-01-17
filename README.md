<p align="center">
  <img src="screenshots/cloudy_logo.png" width="280" height="305" alt="cloudy_logo" />
  <img src="screenshots/cloudy_logo_dark.png" width="280" height="305" alt="cloudy_logo_dark" />
</p>
<h1 align="center">Cloudy</h1></br>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
  <a href="https://android-arsenal.com/api?level=23"><img alt="API" src="https://img.shields.io/badge/API-23%2B-brightgreen.svg?style=flat"/></a>
  <a href="https://github.com/skydoves/cloudy/actions/workflows/android.yml"><img alt="Build Status" 
  src="https://github.com/skydoves/cloudy/actions/workflows/android.yml/badge.svg"/></a>
  <a href="https://androidweekly.net/issues/issue-545"><img alt="Android Weekly" src="https://skydoves.github.io/badges/android-weekly.svg"/></a>
  <a href="https://github.com/skydoves"><img alt="Profile" src="https://skydoves.github.io/badges/skydoves.svg"/></a>
</p><br>

<p align="center">
☁️ Kotlin Multiplatform blur effect library for Compose, with GPU-accelerated rendering and CPU fallback for older devices. See <a href="https://skydoves.github.io/Cloudy/">documentation</a> for more details.
</p><br>

> <p align="center">The `blur` modifier supports only Android 12 and higher, and `RenderScript` APIs are deprecated starting in Android 12.
> Cloudy is the backport of the blur effect for Jetpack Compose with cross-platform support.</p>

<p align="center">
<img src="preview/gif0.gif" width="268"/>
<img src="preview/img1.png" width="270"/>
<img src="preview/img2.png" width="268"/>
</p>

### Supported Platforms (Blur Effect)

| Platform | Implementation | Performance | State Type |
|----------|----------------|-------------|------------|
| Android 31+ | RenderEffect (GPU) | GPU-accelerated | `Success.Applied` |
| Android 30- | Native C++ (CPU) | NEON/SIMD optimized | `Success.Captured` |
| iOS | Skia BlurEffect (Metal GPU) | GPU-accelerated | `Success.Applied` |
| macOS | Skia BlurEffect (Metal GPU) | GPU-accelerated | `Success.Applied` |
| Desktop (JVM) | Skia BlurEffect (GPU) | GPU-accelerated | `Success.Applied` |
| WASM (Browser) | Skia BlurEffect (WebGL) | GPU-accelerated | `Success.Applied` |

> See [Liquid Glass Effect](#liquid-glass-effect) for liquid glass platform support.

## Download

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/cloudy.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.github.skydoves%22%20AND%20a:%22cloudy%22)

### Version Catalog

If you're using Version Catalog, you can configure the dependency by adding it to your `libs.versions.toml` file as follows:

```toml
[versions]
#...
cloudy = "0.5.0"

[libraries]
#...
compose-cloudy = { module = "com.github.skydoves:cloudy", version.ref = "cloudy" }
```

### Gradle
Add the dependency below to your **module**'s `build.gradle.kts` file:

```gradle
dependencies {
    implementation("com.github.skydoves:cloudy:0.5.0")
    
    // if you're using Version Catalog
    implementation(libs.compose.cloudy)
}
```

For Kotlin Multiplatform, add the dependency below to your **module**'s `build.gradle.kts` file:

```gradle
sourceSets {
    commonMain.dependencies {
        implementation("com.github.skydoves:cloudy:$version")
    }
}
```

## Usage

You can implement blur effect with `Modifier.cloudy()` composable function as seen below:

```kotlin
Text(
  modifier = Modifier.cloudy(),
  text = "This text is blurred"
)
```

<img align="right" src="preview/img2.png" width="290"/>

You can change the degree of the blur effect by changing the `radius` parameter of `Modifier.cloudy()` composable function.

```kotlin
Column(
  modifier = Modifier.cloudy(radius = 15)
) {
  Image(..)

  Text(
    modifier = Modifier
      .fillMaxWidth()
      .padding(8.dp),
    text = posterModel.name,
    fontSize = 40.sp,
    color = MaterialTheme.colors.onBackground,
    textAlign = TextAlign.Center
  )

  Text(
    modifier = Modifier
      .fillMaxWidth()
      .padding(8.dp),
    text = posterModel.description,
    color = MaterialTheme.colors.onBackground,
    textAlign = TextAlign.Center
  )
}
```

## Observing Blurring Status

You can monitor the status of the blurring effect by using the `onStateChanged` parameter, which provides `CloudyState`. This allows you to observe and respond to changes in the blurring effect's state effectively.

```kotlin
GlideImage(
  modifier = Modifier
    .size(400.dp)
    .cloudy(
      radius = 25,
      onStateChanged = { state ->
        when (state) {
          is CloudyState.Success.Applied -> {
            // GPU blur applied (iOS, Android 31+)
            // No bitmap available - blur rendered directly
          }
          is CloudyState.Success.Captured -> {
            // CPU blur completed (Android 30-)
            // Blurred bitmap available: state.bitmap
            val blurredBitmap = state.bitmap
          }
          is CloudyState.Loading -> {
            // Blur processing in progress
          }
          is CloudyState.Error -> {
            // Handle error: state.throwable
          }
          CloudyState.Nothing -> {
            // Initial state
          }
        }
      }
    ),
  ..
)
```

### CloudyState Types

| State | Description | Bitmap Available |
|-------|-------------|------------------|
| `Success.Applied` | GPU blur applied in rendering pipeline | No |
| `Success.Captured` | CPU blur completed with bitmap | Yes (`state.bitmap`) |
| `Loading` | Blur processing in progress | No |
| `Error` | Blur operation failed | No |
| `Nothing` | Initial state | No |

## Maintaining Blurring Effect on Responsive Composable

The `Modifier.cloudy` captures the bitmap of the composable node under the hood.

```kotlin
LazyVerticalGrid(
  state = rememberLazyGridState(),
  columns = GridCells.Fixed(2)
) {
  itemsIndexed(key = { index, item -> item.id }, items = posters) { index, item ->
    HomePoster(poster = item)
  }
}

@Composable
private fun HomePoster(poster: Poster) {
    ConstraintLayout {
      val (image, title, content) = createRefs()
      GlideImage(
        modifier = Modifier
          .cloudy(radius = 15)
          .aspectRatio(0.8f)
          .constrainAs(image) {
            centerHorizontallyTo(parent)
            top.linkTo(parent.top)
          }
          ..
```

## Blur Effect with Network Images

You can easily implement blur effect with [Landscapist](https://github.com/skydoves/landscapist), which is a Jetpack Compose image loading library that fetches and displays network images with Glide, Coil, and Fresco. For more information, see the [Transformation](https://github.com/skydoves/landscapist#transformation) section.

## Liquid Glass Effect

![image](screenshots/liquidglass.gif)

Cloudy provides a `Modifier.liquidGlass()` that creates a realistic glass lens effect with SDF-based crisp edges, normal-based refraction, and chromatic dispersion.

**Note:** For blur effects, use `Modifier.cloudy()` separately. The two modifiers are designed to work independently, giving you full control over each effect.

### Platform Support (Liquid Glass)

| Platform | Implementation | Features |
|----------|----------------|----------|
| Android 33+ | RuntimeShader (AGSL) | Full effect |
| Android 32- | Fallback | Tint + edge + shape (no lens refraction) |
| iOS | Skia RuntimeEffect | Full effect |
| macOS | Skia RuntimeEffect | Full effect |
| Desktop (JVM) | Skia RuntimeEffect | Full effect |
| WASM | Skia RuntimeEffect | Full effect |

### Basic Usage

```kotlin
var lensCenter by remember { mutableStateOf(Offset(100f, 100f)) }

Box(
  modifier = Modifier
    .fillMaxSize()
    .pointerInput(Unit) {
      detectDragGestures { change, _ ->
        lensCenter = change.position
      }
    }
    .cloudy(radius = 15) // Use Cloudy for blur (independent)
    .liquidGlass(lensCenter = lensCenter) // Lens distortion effect
) {
  Image(
    painter = painterResource(R.drawable.photo),
    contentDescription = null,
    modifier = Modifier.fillMaxSize()
  )
}
```

### Combining with Cloudy Blur

The `liquidGlass()` and `cloudy()` modifiers are independent and composable:

```kotlin
// Blur only
.cloudy(radius = 20)

// Lens effect only
.liquidGlass(lensCenter = lensCenter)

// Both effects combined
.cloudy(radius = 15)
.liquidGlass(lensCenter = lensCenter)
```

This separation gives you full access to Cloudy's blur API (`radius`, `onStateChanged`, `CloudyState`) while using the liquid glass lens effect.

### Customization

You can customize the liquid glass effect with various parameters:

```kotlin
.liquidGlass(
  lensCenter = lensCenter,
  lensSize = Size(350f, 350f),  // Size of the glass lens
  cornerRadius = 50f,           // Rounded corners
  refraction = 0.25f,           // Distortion amount
  curve = 0.25f,                // Lens curvature strength
  dispersion = 0.0f,            // Chromatic dispersion (RGB separation)
  saturation = 1.0f,            // Color saturation
  contrast = 1.0f,              // Light/dark contrast
  tint = Color.Transparent,     // Optional color tint
  edge = 0.2f,                  // Edge lighting width
)
```

### Parameters

| Parameter | Default | Description | Fallback (Android 32-) |
|-----------|---------|-------------|------------------------|
| `lensCenter` | - | Center position of the glass lens (required) | Yes |
| `lensSize` | 350x350 | Size of the lens in pixels | Yes |
| `cornerRadius` | 50f | Corner radius for rounded rectangle shape | Yes |
| `refraction` | 0.25f | Controls how much background distorts through lens | No (requires API 33+) |
| `curve` | 0.25f | Controls how strongly lens curves at center vs edges | No (requires API 33+) |
| `dispersion` | 0.0f | Chromatic dispersion/aberration intensity | No (requires API 33+) |
| `saturation` | 1.0f | Color saturation (1.0 = normal) | Approximation |
| `contrast` | 1.0f | Light/dark contrast (1.0 = normal) | Approximation |
| `tint` | Transparent | Optional color tint overlay | Yes |
| `edge` | 0.2f | Edge lighting width (0 = none) | Yes (as stroke) |
| `enabled` | true | Enable/disable the effect | Yes |

> **Note:** On Android 32 and below, the lens refraction effect is not available since it requires `RuntimeShader` (API 33+). The fallback draws a visible lens shape with tint, edge lighting, and color adjustments. For blur effects, use `Modifier.cloudy()` separately.

## Find this repository useful? :heart:
Support it by joining __[stargazers](https://github.com/skydoves/cloudy/stargazers)__ for this repository. :star: <br>
Also, __[follow me](https://github.com/skydoves)__ on GitHub for my next creations! 🤩

# License
```xml
Designed and developed by 2022 skydoves (Jaewoong Eum)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
