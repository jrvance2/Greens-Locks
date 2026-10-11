plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI passes -Pvc=<run number> so every published build is newer than the last (needed for in-app updates).
val buildNumber = (project.findProperty("vc") as String?)?.toIntOrNull() ?: 2

android {
    namespace = "com.greenslocks.iptv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.greenslocks.iptv"
        minSdk = 23
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.2.$buildNumber"
    }

    // CI supplies a stable signing key (so updates install over the old app); without one the
    // build falls back to the temporary debug key and can still be installed fresh.
    signingConfigs {
        create("vancetv") {
            val keystore = System.getenv("VANCETV_KEYSTORE")
            if (!keystore.isNullOrBlank() && file(keystore).exists()) {
                storeFile = file(keystore)
                storePassword = System.getenv("VANCETV_STORE_PASSWORD")
                keyAlias = System.getenv("VANCETV_KEY_ALIAS")
                keyPassword = System.getenv("VANCETV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Optimized, non-debuggable build: Compose is several times faster than in debug.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val stable = signingConfigs.getByName("vancetv")
            signingConfig = if (stable.storeFile != null) stable else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.zxing:core:3.5.3")
}
