package ai.pleosapp.drive.ui.region

/**
 * 가짜 지역 검색. core/ 가 생기면 그 인터페이스로 바꿔 끼워요.
 *
 * 지역은 도 단위("경기도")만 지원해요 (2026-11-09 팀 결정).
 * "경기도"를 검색하면 추천 코스 3개가 바로 나와요.
 * TEMP: 재원 님의 UiState/FakeUiStates(PLEOS-58)가 오면 그쪽 값으로 바꿔요.
 */
object FakeRegionSource {
    private const val REGION = "경기도"

    private val recommended = listOf(
        RegionSuggestion("A. 표범 코스", "$REGION · 26km · 약 55분"),
        RegionSuggestion("B. 하트 코스", "$REGION · 42km · 약 80분"),
        RegionSuggestion("C. 촛불 코스", "$REGION · 63km · 약 95분"),
    )

    const val MAX_RESULTS = 3

    fun search(query: String): List<RegionSuggestion> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val matchesRegion = REGION.startsWith(q) || q.startsWith(REGION)
        return if (matchesRegion) recommended.take(MAX_RESULTS) else emptyList()
    }
}
