package ai.pleosapp.drive.ui.region

import ai.pleosapp.drive.ui.common.SampleRoute

/**
 * 가짜 지역 검색. core/ 가 생기면 그 인터페이스로 바꿔 끼워요.
 *
 * 지역은 도 단위("경기도")만 지원해요 (2026-11-09 팀 결정).
 * "경기도"를 검색하면 추천 코스 3개가 바로 나와요.
 */
object FakeRegionSource {
    private const val REGION = "경기도"

    // TEMP: A·C 코스 지도 사진이 아직 없어서 모양은 모두 B(하트) 경로를 써요.
    private val recommended = listOf(
        CourseItem("leopard", "A", "표범 코스", 26, 55, SampleRoute.heart),
        CourseItem("heart", "B", "하트 코스", 42, 80, SampleRoute.heart),
        CourseItem("candle", "C", "촛불 코스", 63, 95, SampleRoute.heart),
    )

    const val MAX_RESULTS = 3

    fun search(query: String): List<CourseItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val matchesRegion = REGION.startsWith(q) || q.startsWith(REGION)
        return if (matchesRegion) recommended.take(MAX_RESULTS) else emptyList()
    }
}
