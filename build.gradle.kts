// Общий файл сборки. Здесь только перечислены плагины;
// подключаются они в модуле приложения (app/build.gradle.kts).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}
