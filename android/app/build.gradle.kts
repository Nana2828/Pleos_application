plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ai.pleosapp.drive"
    compileSdk = 34
    // AAOS 표준 Car API (android.car)
    useLibrary("android.car")

    defaultConfig {
        applicationId = "ai.pleosapp.drive"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    // 단위 시험에서 시연 데이터(assets/demo)를 그대로 읽는다
    sourceSets["test"].resources.srcDir("src/main/assets")
}

dependencies {
    // Pleos Vehicle SDK. 2026-11-08 문서 project-setup 기준 최신 2.3.2 (시험 앱에서 동작 확인)
    implementation("ai.pleos.playground:Vehicle:2.3.2")

    // 주행 계산 코어(core/drive)의 규격 JSON 읽기·쓰기
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Jetpack Compose + MVVM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    testImplementation("junit:junit:4.13.2")
}
