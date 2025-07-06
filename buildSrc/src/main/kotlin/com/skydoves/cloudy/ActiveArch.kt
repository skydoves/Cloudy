package com.skydoves.cloudy

import org.gradle.api.Project
import java.util.Properties

enum class Arch(val arch: String?) {
  ARM("ARM64"),
  ARM_SIMULATOR_DEBUG("ARM64SIMULATOR_DEBUG"),
  X86_64("X86_64"),
  ALL(null);

  companion object {
    fun of(arch: String?): Arch {
      return values().firstOrNull { it.arch == arch } ?: ALL
    }
  }
}

val Project.activeArch
  get() = Arch.of(
    rootProject.layout.projectDirectory.file("local.properties").asFile.takeIf { it.exists() }
      ?.let {
        Properties().apply {
          load(it.reader(Charsets.UTF_8))
        }.getProperty("arch")
      } ?: System.getenv("arch")
  )
