package ai.pleosapp.drive.core.navi

import java.io.File

/** 시험에서 실제 시연 assets(`app/src/main/assets/demo/`)를 읽는다. 단위 시험의 작업 폴더는 보통 app 모듈 폴더다. */
object DemoAssets {
    private val candidates = listOf(
        "src/main/assets/demo",
        "app/src/main/assets/demo",
        "android/app/src/main/assets/demo",
    )

    val dir: File by lazy {
        var base: File? = File(System.getProperty("user.dir")).absoluteFile
        while (base != null) {
            candidates.map { File(base, it) }.firstOrNull { File(it, "bucheon-cat-91min.route.json").isFile }?.let { return@lazy it }
            base = base.parentFile
        }
        error("시연 assets 폴더(app/src/main/assets/demo)를 찾지 못했습니다. user.dir=${System.getProperty("user.dir")}")
    }

    fun text(name: String): String = File(dir, name).readText(Charsets.UTF_8)
}
