package com.skydoves.cloudy

object Configuration {
  const val compileSdk = 35
  const val targetSdk = 35
  const val minSdk = 21
  const val majorVersion = 0
  const val minorVersion = 2
  const val patchVersion = 4
  const val versionName = "$majorVersion.$minorVersion.$patchVersion"
  const val versionCode = 8
  const val snapshotVersionName = "$majorVersion.$minorVersion.${patchVersion + 1}-SNAPSHOT"
  const val artifactGroup = "com.github.skydoves"
}
