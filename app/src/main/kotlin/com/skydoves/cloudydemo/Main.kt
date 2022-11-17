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
package com.skydoves.cloudydemo

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.Cloudy
import com.skydoves.cloudy.CloudyState
import com.skydoves.cloudydemo.model.MockUtil
import com.skydoves.landscapist.glide.GlideImage
import com.skydoves.landscapist.glide.GlideImageState
import com.skydoves.landscapist.glide.rememberGlideImageState

@Composable
fun Main() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colors.background)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    var animationPlayed by remember { mutableStateOf(false) }
    val radius by animateIntAsState(
      targetValue = if (animationPlayed) 15 else 0,
      animationSpec = tween(
        durationMillis = 1000,
        delayMillis = 1000,
        easing = FastOutLinearInEasing
      )
    )

    LaunchedEffect(Unit) {
      animationPlayed = true
    }

    val poster = remember { MockUtil.getMockPoster() }
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

    Cloudy(
      radius = radius,
      allowAccumulate = { true }
    ) {
      Column {
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
          text = poster.name,
          fontSize = 40.sp,
          color = MaterialTheme.colors.onBackground,
          textAlign = TextAlign.Center
        )

        Text(
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
          text = poster.description,
          color = MaterialTheme.colors.onBackground,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}
