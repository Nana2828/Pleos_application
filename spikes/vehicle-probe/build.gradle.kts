plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

// 빌드 산출물이 한글 경로에 있으면 Windows에서 단위 테스트가 실패한 적이 있다.
// PROBE_BUILD_DIR에 영문 경로를 지정하면 빌드 산출물만 그곳으로 보낸다.
System.getenv("PROBE_BUILD_DIR")?.takeIf { it.isNotBlank() }?.let { root ->
    allprojects {
        layout.buildDirectory.set(file("$root/${project.name}"))
    }
}
