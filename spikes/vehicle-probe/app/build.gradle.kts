plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ai.pleosapp.probe"
    compileSdk = 34
    useLibrary("android.car")

    defaultConfig {
        applicationId = "ai.pleosapp.probe"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Pleos Vehicle SDK (Vehicle State). 문서 project-setup 기준 2.3.2 (2026-11-08 확인)
    implementation("ai.pleos.playground:Vehicle:2.3.2")

    testImplementation("junit:junit:4.13.2")
}
