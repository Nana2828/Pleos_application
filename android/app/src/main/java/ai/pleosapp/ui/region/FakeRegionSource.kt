package ai.pleosapp.ui.region

/**
 * 가짜 지역 검색. core/ 가 생기면 그 인터페이스로 바꿔 끼워요.
 * 시안(docs/ui/page-1.png)의 "수원" 검색 결과와 같게 맞췄어요.
 */
object FakeRegionSource {
    private val all = listOf(
        RegionSuggestion("수원시", "경기도"),
        RegionSuggestion("수원역", "경기 수원시 팔달구"),
        RegionSuggestion("수원화성", "경기 수원시 장안구"),
        RegionSuggestion("철원군", "강원특별자치도"),
        RegionSuggestion("서울시", "서울특별시"),
        RegionSuggestion("부산시", "부산광역시"),
    )

    const val MAX_RESULTS = 3

    fun search(query: String): List<RegionSuggestion> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return all.filter { it.name.contains(q) }.take(MAX_RESULTS)
    }
}
