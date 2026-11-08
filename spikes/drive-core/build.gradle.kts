// 화면·안드로이드와 분리된 계산 코드. PC에서 `./gradlew test`로 검증한다.
plugins {
    kotlin("jvm") version "2.0.21"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging {
        events("failed")
        showStandardStreams = false
    }
}
