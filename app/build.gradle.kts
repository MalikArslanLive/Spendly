import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.myexpenses.kfqrz"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    getByName("debug") {
      val debugKeystoreFile = file("${rootDir}/debug.keystore")
      if (!debugKeystoreFile.exists()) {
        val cmSecureFilesDir = System.getenv("CM_SECURE_FILES_DIR")
        val secureFile = if (cmSecureFilesDir != null) file("$cmSecureFilesDir/debug.keystore") else null
        if (secureFile?.exists() == true) {
          secureFile.copyTo(debugKeystoreFile, overwrite = true)
        } else {
          val b64 = System.getenv("DEBUG_KEYSTORE_BASE64")
            ?: (if (file("${rootDir}/debug.keystore.base64").exists()) file("${rootDir}/debug.keystore.base64").readText() else null)
            ?: "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"
          debugKeystoreFile.writeBytes(Base64.getDecoder().decode(b64.trim()))
        }
      }
      storeFile = debugKeystoreFile
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("GOOGLE_OAUTH_CLIENT_ID")
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.firestore)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.auth)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
