// Файл сборки самого приложения.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Читает app/google-services.json и подключает приложение к вашему проекту Firebase
    alias(libs.plugins.google.services)
}

android {
    // Имя пакета. Точно такое же нужно указать в консоли Firebase (README.md, шаг 3)
    namespace = "com.svyaz.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.svyaz.app"
        // Android 8.0 и новее
        minSdk = 26
        // Не поднимайте до 37 без доработки: начиная с 37 для звонков между телефонами
        // в одной сети Wi-Fi нужно отдельное разрешение на доступ к локальной сети.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.emoji2.emojipicker)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.coil.compose)
    implementation(libs.stream.webrtc.android)
}
