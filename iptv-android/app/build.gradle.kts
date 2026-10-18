plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes -Pvc=<run number> so every published build is newer than the last (needed for in-app updates).
val buildNumber = (project.findProperty("vc") as String?)?.toIntOrNull() ?: 2
// -Pbeta=true builds the beta channel: installs next to the stable app and updates from its own releases.
val isBeta = project.findProperty("beta") == "true"

android {
    namespace = "com.greenslocks.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.greenslocks.iptv"
        minSdk = 23
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.2.$buildNumber" + if (isBeta) "-beta" else ""
        if (isBeta) applicationIdSuffix = ".beta"
        resValue("string", "app_name", if (isBeta) "Vance TV Beta" else "Vance TV")
        buildConfigField("boolean", "BETA", isBeta.toString())
        // The software audio decoder ships native code; only the chips real TV boxes use.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
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
    testOptions { unitTests.isReturnDefaultValues = true }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    // Software decoder for Dolby Digital / Digital+ / DTS and more, so 5.1 works even when the device decoder only gives stereo.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")
    // Network stack for streaming: connection reuse, redirects, IPv4-first lookups.
    implementation("androidx.media3:media3-datasource-okhttp:1.5.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.tvprovider:tvprovider:1.0.0")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
