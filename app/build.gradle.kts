plugins {
  id("com.android.application")
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.sonvideo.player"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.sonvideo.player"
    minSdk = 28
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0-beta"

    ndk {
      abiFilters += listOf("armeabi-v7a", "arm64-v8a")
    }
  }

  androidResources {
    localeFilters += listOf("en")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  signingConfigs {
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    getByName("debug") {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
    getByName("release") {
      isShrinkResources = true
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
    resources {
      excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "META-INF/INDEX.LIST",
        "META-INF/DEPENDENCIES",
        "META-INF/NOTICE*",
        "META-INF/LICENSE*",
        "META-INF/*.version",
        "DebugProbesKt.bin",
        "kotlin-tooling-metadata.json",
      )
    }
  }
}

dependencies {
  val media3Version = libs.versions.releaseVersion.get()

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.kotlinx.serialization.json)

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  // implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Media3 + NextLib FFmpeg/dav1d Codec & Engine (Offline-Only Lite)
  implementation("androidx.media3:media3-exoplayer:$media3Version")
  // implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
  // implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
  // implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
  implementation("androidx.media3:media3-ui:$media3Version")
  implementation(libs.nextlib.media3ext)
  implementation(libs.nextlib.mediainfo)
}
