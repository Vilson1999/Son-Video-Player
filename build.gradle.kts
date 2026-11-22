buildscript {
  repositories {
    google()
    mavenCentral()
  }
  dependencies {
    classpath(libs.strict.version.matcher.plugin)
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
  group = "com.sonvideo.player"
}
