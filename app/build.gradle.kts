plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.tortugram"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.tortugram"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}
dependencies {
    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource)

    // Generación de QR
    implementation(libs.zxing.core)

    implementation("com.google.zxing:core:3.5.3")

    // TDLib Telegram
    implementation("dev.g000sha256:tdl-coroutines:13.0.0")

    // contrseña 2f
    implementation("androidx.compose.material3:material3:1.3.1")

    // Dependencia de Coil para Jetpack Compose
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.compose.material:material-icons-extended")

    // Servidor interno para Streaming (puente TDLib -> ExoPlayer)
    implementation("io.ktor:ktor-server-core:3.0.0")
    implementation("io.ktor:ktor-server-cio:3.0.0")
    // NUEVO: necesario para call.respondBytesWriter / streaming de bytes
    implementation("io.ktor:ktor-utils:3.0.0")

    // Jetpack Compose & TV
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Tooling Debug
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
