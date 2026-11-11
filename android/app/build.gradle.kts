import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * 결과 QR에 넣을 폰 웹 기본 주소 (PLEOS-64). 노트북 IP는 와이파이마다 바뀌어서 git에 올리지 않는
 * `android/local.properties`에 적는다. 예: `pleos.webUrl=http://192.168.0.12:5173/`
 * 명령줄 `-Ppleos.webUrl=...`이 있으면 그 값을 먼저 쓴다. 없으면 결과 화면에 "웹 주소가 설정되지 않았어요"가 나온다.
 */
val resultWebUrl: String = (findProperty("pleos.webUrl") as String?)
    ?: rootProject.file("local.properties").takeIf { it.isFile }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }.getProperty("pleos.webUrl")
    }
    ?: ""
if (resultWebUrl.isBlank()) {
    logger.warn("pleos.webUrl 이 없어요. 결과 QR을 쓰려면 android/local.properties 에 pleos.webUrl=http://<노트북 IP>:5173/ 을 넣으세요.")
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
        buildConfigField("String", "RESULT_WEB_URL", "\"" + resultWebUrl.trim().replace("\\", "").replace("\"", "") + "\"")
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
        buildConfig = true
    }
    // 단위 시험에서 시연 데이터(assets/demo)를 그대로 읽는다
    sourceSets["test"].resources.srcDir("src/main/assets")
}

dependencies {
    // Pleos Vehicle SDK. 2026-11-08 문서 project-setup 기준 최신 2.3.2 (시험 앱에서 동작 확인)
    implementation("ai.pleos.playground:Vehicle:2.3.2")

    // 주행 계산 코어(core/drive)의 규격 JSON 읽기·쓰기
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // 결과 화면의 QR 코드 그리기 (PLEOS-46)
    implementation("com.google.zxing:core:3.5.3")

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
