<h1 align="center">Cloudy</h1></br>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
  <a href="https://android-arsenal.com/api?level=21"><img alt="API" src="https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat"/></a>
  <a href="https://github.com/skydoves/cloudy/actions/workflows/android.yml"><img alt="Build Status" 
  src="https://github.com/skydoves/cloudy/actions/workflows/android.yml/badge.svg"/></a>
  <a href="https://github.com/skydoves"><img alt="Profile" src="https://skydoves.github.io/badges/skydoves.svg"/></a>
</p><br>

<p align="center">
☁️ Jetpack Compose blur process library, which supports all Android versions.
</p><br>

> <p align="center">The `blur` modifier supports only Android 12 and higher, and `RenderScript` APIs are deprecated starting in Android 12.
> Cloudy is the backport of the blur effect for Jetpack Compose.</p>

<p align="center">
<img src="preview/gif0.gif" width="268"/>
<img src="preview/img1.png" width="270"/>
<img src="preview/img2.png" width="268"/>
</p>

## Download
[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/cloudy.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.github.skydoves%22%20AND%20a:%22cloudy%22)

### Gradle

Add the dependency below to your **module**'s `build.gradle` file:
```gradle
dependencies {
    implementation "com.github.skydoves:cloudy:0.1.0"
}
```

## Usage

You can implement blur effect with `Cloudy` composable function as seen in the below:

```kotlin
Cloudy {
    Text(text = "This text is blurred")
}
```

<img align="right" src="preview/img2.png" width="290"/>

You can change the degree of the blur effect by changing the `radius` parameter of `Cloudy` composable function. You can only set between **0..25** integer values.

```kotlin
Cloudy(radius = 15) {
    Column {
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
}
```

### Blur Effects Depending on States

If you need to load your network image or do something heavy business logic first, you should re-execute the blur effect depending on your state. 
Basically, you can re-execute the blur calculation by changing the `radius` value, but you can also re-execute the blurring process by giving the `key1` parameter to trigger internal processes.

```kotlin
var glideState by rememberGlideImageState()
Cloudy(
  radius = 15,
  key1 = glideState, // re-execute the blurring process whenever the state value is changed.
) {
  GlideImage(
    modifier = Modifier.size(400.dp),
    imageModel = { poster.image },
    onImageStateChanged = { glideState = it }
  )
}
```

You can also utilize the `key2` parameter to trigger the blurring process.

### Accumulate Blur Radius

You can accumulate the blur radius and keep adding the degree of blur effect whenever you change the `radius` parameter by giving the `allowAccumulate` condition. 
For example, if you want to accumulate the degree of blur gradually, you can set the condition of the `allowAccumulate` parameter depending on your states like the below:

<img align="right" src="preview/gif0.gif" width="268"/>

```kotlin
var animationPlayed by remember { mutableStateOf(false) }
val radius by animateIntAsState(
  targetValue = if (animationPlayed) 10 else 0,
  animationSpec = tween(
    durationMillis = 3000,
    delayMillis = 100,
    easing = LinearOutSlowInEasing
  )
)

// Accumulate the degree of blur gradually for the network image.
var glideState by rememberGlideImageState()
Cloudy(
  radius = radius,
  key1 = glideState,
  allowAccumulate = { it is CloudyState.Success && glideState is GlideImageState.Success }
) {
  GlideImage(
    modifier = Modifier.size(400.dp),
    imageModel = { poster.image },
    onImageStateChanged = { glideState = it }
  )
}
```

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
