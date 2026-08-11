plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // Tetap gunakan compose bawaan proyekmu
}

android {
    namespace = "com.vortexsign_tv"
    compileSdk = 36 // ✨ DIUBAH DARI 34 JADI 36

    defaultConfig {
        applicationId = "com.vortexsign_tv"
        minSdk = 26
        targetSdk = 34 // Biarkan targetSdk tetap 34 tidak apa-apa
        versionCode = 1
        versionName = "1.0"
        // ...

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Pustaka bawaan (Biarkan Android Studio yang mengelolanya)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // 🚀 BAHAN BAKU UTAMA VORTEXSIGN TV:

    // Koneksi API ke Laravel (Retrofit & GSON)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Muat Gambar dari URL (Glide)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Pemutar Video Tanpa Jeda (Media3 ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Pengujian
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}