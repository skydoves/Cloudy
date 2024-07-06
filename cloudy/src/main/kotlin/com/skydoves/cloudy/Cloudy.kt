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
package com.skydoves.cloudy

import android.graphics.Bitmap
import android.renderscript.RenderScript
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.viewinterop.AndroidView
import com.skydoves.cloudy.internals.CloudyModifier
import com.skydoves.cloudy.internals.InternalLaunchedEffect
import com.skydoves.cloudy.internals.LayoutInfo
import com.skydoves.cloudy.internals.render.iterativeBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cloudy is a replacement of the [blur] modifier (under Android 12),
 * which draws [content] blurred with the specified [radius].
 *
 * History: The [blur] modifier supports only Android 12 and higher, and [RenderScript] was also deprecated.
 *
 * @param modifier Adjust the drawing layout or drawing decoration of the content.
 * @param radius Radius of the blur along both the x and y axis.
 * @param key1 Key value for trigger recomposition.
 * @param key2 Key value for trigger recomposition.
 * @param onStateChanged Lambda function that will be invoked when the blur process has been updated.
 * @param content Composable content that will be applied blur effect.
 */
@Composable
public fun Cloudy(
  modifier: Modifier = Modifier,
  radius: Int = 10,
  key1: Any? = null,
  key2: Any? = null,
  allowAccumulate: (CloudyState) -> Boolean = { false },
  onStateChanged: (CloudyState) -> Unit = {},
  content: @Composable BoxScope.() -> Unit
) {
  val context = LocalContext.current
  var initialBitmap by rememberSaveable(key1, key2) { mutableStateOf<Bitmap?>(null) }
  AndroidView(factory = { ComposeView(context) }, update = {
    it.composeCloudy(
      modifier = modifier,
      key1 = radius,
      key2 = key1,
      key3 = key2,
      radius = radius,
      initialBitmap = initialBitmap,
      onStateChanged = { state ->
        onStateChanged.invoke(state)
        if (allowAccumulate.invoke(state) && state is CloudyState.Success) {
          initialBitmap = state.bitmap
        }
      },
      content = content
    )
  })
}

/**
 * Wrap the [content] with [Box] to apply [cloudy] modifier extension and calculate global layout
 * positions.
 *
 * @param modifier Adjust the drawing layout or drawing decoration of the content.
 * @param radius Radius of the blur along both the x and y axis. It must be in 0 to 25.
 * @param key1 Key value for trigger recomposition.
 * @param key2 Key value for trigger recomposition.
 * @param key3 Key value for trigger recomposition.
 * @param onStateChanged Lambda function that will be invoked when the blur process has been updated.
 * @param content Composable content that will be applied blur effect.
 */
private fun ComposeView.composeCloudy(
  modifier: Modifier,
  radius: Int,
  key1: Any? = null,
  key2: Any? = null,
  key3: Any? = null,
  initialBitmap: Bitmap? = null,
  onStateChanged: (CloudyState) -> Unit,
  content: @Composable BoxScope.() -> Unit
) = apply {
  setContent {
    var layoutInfo by remember(
      key1 = key1,
      key2 = key2,
      key3 = key3
    ) { mutableStateOf(LayoutInfo()) }

    val graphicsLayer = rememberGraphicsLayer()

    Box(
      modifier = modifier
        .onGloballyPositioned {
          layoutInfo = LayoutInfo(
            xOffset = it.positionInWindow().x.toInt(),
            yOffset = it.positionInWindow().y.toInt(),
            width = it.size.width,
            height = it.size.height
          )
        }
        .drawWithContent {
          // call record to capture the content in the graphics layer
          graphicsLayer.record {
            // draw the contents of the composable into the graphics layer
            this@drawWithContent.drawContent()
          }
          // draw the graphics layer on the visible canvas
          drawLayer(graphicsLayer)
        }
        .cloudy(
          key1 = key1,
          key2 = key2,
          key3 = key3,
          radius = radius,
          layoutInfo = layoutInfo,
          initialBitmap = initialBitmap,
          graphicsLayer = graphicsLayer,
          onStateChanged = onStateChanged
        ),
      content = content
    )
  }
}

/**
 * Composition of a [Modifier] to apply a blur effect to the given [view] and lunch blur rendering
 * process on the [Dispatchers.IO].
 *
 * @param radius Radius of the blur along both the x and y axis. It must be in 0 to 25.
 * @param key1 Key value for trigger recomposition.
 * @param key2 Key value for trigger recomposition.
 * @param key3 Key value for trigger recomposition.
 * @param layoutInfo The [LayoutInfo] contains global layout information to decide the rendering position and scale.
 * @param onStateChanged Lambda function that will be invoked when the blur process has been updated.
 */
private fun Modifier.cloudy(
  key1: Any? = null,
  key2: Any? = null,
  key3: Any? = null,
  initialBitmap: Bitmap? = null,
  graphicsLayer: GraphicsLayer,
  radius: Int,
  layoutInfo: LayoutInfo,
  onStateChanged: (CloudyState) -> Unit
): Modifier = composed(
  inspectorInfo = debugInspectorInfo {
    name = "cloudy"
    properties["cloudy"] = radius
  },
  factory = {
    var blurredBitmap: Bitmap? by rememberSaveable(key1, key2, key3) {
      mutableStateOf(initialBitmap)
    }

    InternalLaunchedEffect(key1 = key1, key2 = key2, key3 = key3, key4 = layoutInfo, block = {
      if (layoutInfo.width > 0 && layoutInfo.height > 0) {
        launch {
          onStateChanged.invoke(CloudyState.Loading)
          withContext(Dispatchers.IO) {
            val targetBitmap = blurredBitmap ?: graphicsLayer.toImageBitmap().asAndroidBitmap()
              .copy(Bitmap.Config.ARGB_8888, true)

            blurredBitmap = iterativeBlur(
              androidBitmap = targetBitmap,
              radius = radius
            )
          }
        }.invokeOnCompletion { throwable ->
          if (throwable == null) {
            onStateChanged.invoke(CloudyState.Success(blurredBitmap))
          } else {
            onStateChanged.invoke(CloudyState.Error(throwable))
          }
        }
      }
    })

    if (blurredBitmap != null) {
      CloudyModifier(blurredBitmap)
    } else {
      this
    }
  }
)
