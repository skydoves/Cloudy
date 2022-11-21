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
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.renderscript.RenderScript
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.core.view.drawToBitmap
import com.skydoves.cloudy.internals.CloudyModifier
import com.skydoves.cloudy.internals.InternalLaunchedEffect
import com.skydoves.cloudy.internals.LayoutInfo
import com.skydoves.cloudy.internals.getActivity
import com.skydoves.cloudy.internals.render.RenderScriptToolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Cloudy is a replacement of the [blur] modifier (under Android 12),
 * which draws [content] blurred with the specified [radius].
 *
 * History: The [blur] modifier supports only Android 12 and higher, and [RenderScript] was also deprecated.
 *
 * @param modifier Adjust the drawing layout or drawing decoration of the content.
 * @param radius Radius of the blur along both the x and y axis. It must be in 0 to 25.
 * @param key1 Key value for trigger recomposition.
 * @param key2 Key value for trigger recomposition.
 * @param onStateChanged Lambda function that will be invoked when the blur process has been updated.
 * @param content Composable content that will be applied blur effect.
 */
@Composable
public fun Cloudy(
  modifier: Modifier = Modifier,
  @androidx.annotation.IntRange(from = 0, to = 25) radius: Int = 10,
  key1: Any? = null,
  key2: Any? = null,
  allowAccumulate: (CloudyState) -> Boolean = { false },
  onStateChanged: (CloudyState) -> Unit = {},
  content: @Composable BoxScope.() -> Unit
) {
  val context = LocalContext.current
  var initialBitmap by remember { mutableStateOf<Bitmap?>(null) }
  AndroidView(
    factory = { ComposeView(context) },
    update = {
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
    }
  )
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
  @androidx.annotation.IntRange(from = 0, to = 25) radius: Int,
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
        .cloudy(
          view = this,
          key1 = key1,
          key2 = key2,
          key3 = key3,
          radius = radius,
          layoutInfo = layoutInfo,
          initialBitmap = initialBitmap,
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
 * @param view Target view that will be rendered with blur effects.
 * @param radius Radius of the blur along both the x and y axis. It must be in 0 to 25.
 * @param key1 Key value for trigger recomposition.
 * @param key2 Key value for trigger recomposition.
 * @param key3 Key value for trigger recomposition.
 * @param layoutInfo The [LayoutInfo] contains global layout information to decide the rendering position and scale.
 * @param onStateChanged Lambda function that will be invoked when the blur process has been updated.
 */
private fun Modifier.cloudy(
  view: View,
  key1: Any? = null,
  key2: Any? = null,
  key3: Any? = null,
  initialBitmap: Bitmap? = null,
  @androidx.annotation.IntRange(from = 0, to = 25) radius: Int,
  layoutInfo: LayoutInfo,
  onStateChanged: (CloudyState) -> Unit
): Modifier = composed(
  inspectorInfo = debugInspectorInfo {
    name = "cloudy"
    properties["cloudy"] = radius
  },
  factory = {
    val window = LocalContext.current.getActivity()!!.window
    var blurredBitmap: Bitmap? by remember(key1 = key1, key2 = key2, key3 = key3) {
      mutableStateOf(initialBitmap)
    }

    InternalLaunchedEffect(key1 = key1, key2 = key2, key3 = key3, key4 = layoutInfo, block = {
      if (layoutInfo.width > 0 && layoutInfo.height > 0) {
        launch {
          onStateChanged.invoke(CloudyState.Loading)
          withContext(Dispatchers.IO) {
            val targetBitmap = blurredBitmap ?: view.drawToBitmapPostLaidOut(
              layoutInfo = layoutInfo,
              window = window
            )

            blurredBitmap = RenderScriptToolkit.blur(
              inputBitmap = targetBitmap,
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

/**
 * Return [Bitmap] from the given [window] based on [layoutInfo] information.
 *
 * @param layoutInfo The [LayoutInfo] contains global layout information to decide the rendering position and scale.
 * @param window The given window will be used to extract bitmap information.
 */
private suspend fun View.drawToBitmapPostLaidOut(
  window: Window,
  layoutInfo: LayoutInfo
): Bitmap? {
  return suspendCoroutine { continuation ->
    doOnLayout {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        drawBitmapWithPixelCopy(
          window = window,
          layoutInfo = layoutInfo,
          onSuccess = { bitmap -> continuation.resume(bitmap) },
          onError = { error -> continuation.resumeWithException(error) }
        )
      } else {
        continuation.resume(this.drawToBitmap())
      }
    }
  }
}

/**
 * Extract [Bitmap] as pixels from the given [window] based on [layoutInfo] information.
 *
 * @param window The given window will be used to extract bitmap information.
 * @param layoutInfo The [LayoutInfo] contains global layout information to decide the rendering position and scale.
 * @param onSuccess This lambda will be called when extracting process has been succeed.
 * @param onError This lambda will be called when extracting process has been failed.
 */
@RequiresApi(Build.VERSION_CODES.O)
private fun drawBitmapWithPixelCopy(
  window: Window,
  layoutInfo: LayoutInfo,
  onSuccess: (Bitmap) -> Unit,
  onError: (Throwable) -> Unit
) {
  val rect = Rect(
    layoutInfo.xOffset,
    layoutInfo.yOffset,
    layoutInfo.xOffset + layoutInfo.width,
    layoutInfo.yOffset + layoutInfo.height
  )

  val bitmap = Bitmap.createBitmap(layoutInfo.width, layoutInfo.height, Bitmap.Config.ARGB_8888)
  PixelCopy.request(
    window,
    rect,
    bitmap,
    { copyResult ->
      if (copyResult == PixelCopy.SUCCESS) {
        onSuccess.invoke(bitmap)
      } else {
        onError.invoke(
          RuntimeException("Failed to copy pixels of the given bitmap!")
        )
      }
    },
    Handler(Looper.getMainLooper())
  )
}
