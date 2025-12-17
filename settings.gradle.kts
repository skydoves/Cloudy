@file:Suppress("UnstableApiUsage")

include(":benchmark")


pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
    maven(url = "https://plugins.gradle.org/m2/")
  }
}
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    maven(url = "https://plugins.gradle.org/m2/")
  }
}

rootProject.name = "CloudyDemo"
include(":app")
include(":cloudy")
include(":docs")
