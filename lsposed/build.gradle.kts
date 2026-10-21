plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.huecoder.handleassist"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.huecoder.handleassist"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.material.symbols.rounded)
}
