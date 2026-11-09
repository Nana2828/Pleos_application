plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// 저장소가 한글 경로에 있으면 Windows에서 빌드나 단위 테스트가 실패할 수 있다.
// 그럴 때 PLEOS_BUILD_DIR에 영문 경로를 지정하면 빌드 결과물만 그곳으로 보낸다.
System.getenv("PLEOS_BUILD_DIR")?.takeIf { it.isNotBlank() }?.let { root ->
    allprojects {
        layout.buildDirectory.set(file("$root/${project.name}"))
    }
}
