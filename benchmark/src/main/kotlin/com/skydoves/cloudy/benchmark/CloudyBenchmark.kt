package com.skydoves.cloudy.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun measureRendering() = benchmarkRule.measureRepeated(
      packageName = "com.skydoves.cloudydemo",
      compilationMode = CompilationMode.Full(),
      metrics = listOf(
        TraceSectionMetric(sectionName = "blurScope", mode = TraceSectionMetric.Mode.Average),
        TraceSectionMetric(sectionName = "renderImage", mode = TraceSectionMetric.Mode.Average),
        TraceSectionMetric(sectionName = "fetchBitmap", mode = TraceSectionMetric.Mode.Average),
        TraceSectionMetric(sectionName = "iterativeBlur", mode = TraceSectionMetric.Mode.Average),
        TraceSectionMetric(sectionName = "blur", mode = TraceSectionMetric.Mode.Average),
        TraceSectionMetric(sectionName = "nativeBlurBitmap", mode = TraceSectionMetric.Mode.Average),
      ),
      iterations = 4,
      startupMode = StartupMode.WARM
    ) {
      pressHome()
      startActivityAndWait()
      device.awaitComposeIdle()
    }

  private fun UiDevice.awaitComposeIdle(timeout: Long = 3000) {
    wait(Until.findObject(By.desc("COMPOSE-IDLE")), timeout)
  }
}