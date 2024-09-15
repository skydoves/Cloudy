package com.skydoves.cloudy

object Configuration {
  const val compileSdk = 34
  const val targetSdk = 34
  const val minSdk = 21
  const val majorVersion = 0
  const val minorVersion = 2
  const val patchVersion = 3
  const val versionName = "$majorVersion.$minorVersion.$patchVersion"
  const val versionCode = 7
  const val snapshotVersionName = "$majorVersion.$minorVersion.${patchVersion + 1}-SNAPSHOT"
  const val artifactGroup = "com.github.skydoves"
}
