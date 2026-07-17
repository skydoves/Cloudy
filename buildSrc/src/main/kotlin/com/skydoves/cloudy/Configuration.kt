package com.skydoves.cloudy

object Configuration {
  const val compileSdk = 37
  const val targetSdk = 37
  const val minSdk = 23
  const val majorVersion = 1
  const val minorVersion = 0
  const val patchVersion = 0
  const val preRelease = "-alpha01"
  const val versionName = "$majorVersion.$minorVersion.$patchVersion$preRelease"
  const val versionCode = 19
  const val snapshotVersionName = "$majorVersion.$minorVersion.$patchVersion$preRelease-SNAPSHOT"
  const val artifactGroup = "com.github.skydoves"
}
