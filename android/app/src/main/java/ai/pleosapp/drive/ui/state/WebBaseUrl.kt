package ai.pleosapp.drive.ui.state

/**
 * 결과 QR에 넣을 폰 웹 기본 주소 확인 (PLEOS-64).
 *
 * 값은 빌드할 때 `android/local.properties`의 `pleos.webUrl`에서 온다(`BuildConfig.RESULT_WEB_URL`).
 * 노트북 IP는 와이파이마다 바뀌어서 코드에 넣지 않는다. 예: `pleos.webUrl=http://192.168.0.12:5173/`
 * 폰 웹 규격(은빈 `web/drive-run-web/README.md`): `{웹 기본 주소}?tripId=...` 이므로 기본 주소에는 쿼리(?)와 #이 없어야 한다.
 */
object WebBaseUrl {
    private val pattern = Regex("""^https?://[A-Za-z0-9.\-]+(:\d{1,5})?(/[^?#\s]*)?$""")

    /** 쓸 수 있는 주소면 앞뒤 공백을 뺀 값, 비었거나 형식이 틀리면 null */
    fun parse(raw: String?): String? {
        val url = raw?.trim().orEmpty()
        if (url.isEmpty() || !pattern.matches(url)) return null
        return url
    }
}
