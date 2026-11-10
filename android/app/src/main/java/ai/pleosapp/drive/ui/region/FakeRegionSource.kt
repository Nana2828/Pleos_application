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

    // TEMP: 코스마다 지도 사진과 그 위의 경로를 가져요(SampleRoute).
    private val recommended = listOf(
        CourseItem("leopard", "A", "표범 코스", 26, 55, SampleRoute.leopard, SampleRoute.LEOPARD_MAP),
        CourseItem("heart", "B", "하트 코스", 42, 80, SampleRoute.heart, SampleRoute.HEART_MAP),
        CourseItem("candle", "C", "촛불 코스", 63, 95, SampleRoute.candle, SampleRoute.CANDLE_MAP),
    )

    const val MAX_RESULTS = 3

    fun search(query: String): List<CourseItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val matchesRegion = REGION.startsWith(q) || q.startsWith(REGION)
        return if (matchesRegion) recommended.take(MAX_RESULTS) else emptyList()
    }
}
