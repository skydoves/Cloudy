package com.skydoves.cloudy

object Configuration {
  const val compileSdk = 36
  const val targetSdk = 36
  const val minSdk = 23
  const val majorVersion = 0
  const val minorVersion = 5
  const val patchVersion = 0
  const val versionName = "$majorVersion.$minorVersion.$patchVersion"
  const val versionCode = 14
  const val snapshotVersionName = "$majorVersion.$minorVersion.${patchVersion + 1}-SNAPSHOT"
  const val artifactGroup = "com.github.skydoves"
}
