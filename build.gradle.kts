import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.nexus.plugin)
  alias(libs.plugins.spotless)
  alias(libs.plugins.dokka)
  alias(libs.plugins.kotlinBinaryCompatibilityValidator)
}

apiValidation {
  ignoredProjects.addAll(listOf("app"))
  // Members behind these opt-in markers are excluded from the committed .api dumps: the open
  // shader-recipe API is experimental, and the motion marker's members (liquidGlassTuned,
  // rememberGyroLightSource, rememberTransformLightSource) were leaking into the android/desktop
  // dumps despite being opt-in.
  nonPublicMarkers.add("com.skydoves.cloudy.ExperimentalShaderEffect")
  nonPublicMarkers.add("com.skydoves.cloudy.ExperimentalLiquidGlassMotion")
}

subprojects {
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    compilerOptions.freeCompilerArgs.addAll(
      listOf(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
          project.layout.buildDirectory.asFile.get().absolutePath + "/compose_metrics"
      )
    )
    compilerOptions.freeCompilerArgs.addAll(
      listOf(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" +
          project.layout.buildDirectory.asFile.get().absolutePath + "/compose_metrics"
      )
    )
    compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
  }

  apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)
  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
      target("**/*.kt")
      targetExclude("${layout.buildDirectory.asFile.get()}/**/*.kt")
      ktlint()
        .editorConfigOverride(
        mapOf(
          "indent_size" to "2",
          "continuation_indent_size" to "2",
          "ktlint_standard_function-naming" to "disabled"
        )
      )
      licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
      trimTrailingWhitespace()
      endWithNewline()
    }
    format("kts") {
      target("**/*.kts")
      targetExclude("${layout.buildDirectory.asFile.get()}/**/*.kts")
      licenseHeaderFile(rootProject.file("spotless/copyright.kt"), "(^(?![\\/ ]\\*).*$)")
      trimTrailingWhitespace()
      endWithNewline()
    }
  }
}
