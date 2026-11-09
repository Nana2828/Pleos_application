package ai.pleosapp.drive.ui.region

import ai.pleosapp.drive.ui.common.SampleShapes

/**
 * 가짜 지역 검색. core/ 가 생기면 그 인터페이스로 바꿔 끼워요.
 *
 * 지역은 도 단위("경기도")만 지원해요 (2026-11-09 팀 결정).
 * "경기도"를 검색하면 추천 코스 3개가 바로 나와요.
 */
object FakeRegionSource {
    private const val REGION = "경기도"

    // TEMP: 모양은 하트 하나를 크기만 바꿔 쓴 임시값이에요.
    private val recommended = listOf(
        CourseItem("leopard", "A", "표범 코스", 26, 55, SampleShapes.heart(0.8f)),
        CourseItem("heart", "B", "하트 코스", 42, 80, SampleShapes.heart(1f)),
        CourseItem("candle", "C", "촛불 코스", 63, 95, SampleShapes.heart(0.9f)),
    )

    const val MAX_RESULTS = 3

    fun search(query: String): List<CourseItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val matchesRegion = REGION.startsWith(q) || q.startsWith(REGION)
        return if (matchesRegion) recommended.take(MAX_RESULTS) else emptyList()
    }
}
