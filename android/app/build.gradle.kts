plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.ryzik.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.ryzik.chat"
        minSdk = 26
        targetSdk = 35
        versionCode = 37
        versionName = "0.8.17"
        // Адрес сервера по умолчанию: 10.0.2.2 — это компьютер, на котором запущен эмулятор.
        buildConfigField("String", "DEFAULT_SERVER", "\"https://ryzik3489-ryzikchat.hf.space\"")
    }

    // Один и тот же ключ для всех сборок: без этого обновление поверх старой версии невозможно.
    // Пароли можно переопределить переменными окружения RYZIK_STORE_PASSWORD / RYZIK_KEY_PASSWORD.
    signingConfigs {
        create("ryzik") {
            storeFile = file(System.getenv("RYZIK_KEYSTORE") ?: "ryzik-release.jks")
            storePassword = System.getenv("RYZIK_STORE_PASSWORD") ?: "ryzikchat"
            keyAlias = "ryzik"
            keyPassword = System.getenv("RYZIK_KEY_PASSWORD") ?: "ryzikchat"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ryzik")
        }
        debug {
            signingConfig = signingConfigs.getByName("ryzik")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.datastore.preferences)
    implementation(libs.tink)
    // Звонки: WebRTC (голос и видео напрямую между устройствами)
    implementation("io.getstream:stream-webrtc-android:1.3.10")
    // Квадратики: запись видео с камеры
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    // Эмодзи в стиле iOS: свой шрифт через EmojiCompat
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")
    debugImplementation(libs.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}
