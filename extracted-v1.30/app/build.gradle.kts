plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.izadimbi.reportsaham"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.izadimbi.reportsaham"
        minSdk = 24
        targetSdk = 35
        versionCode = 20
        versionName = "1.28-report-banners"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
