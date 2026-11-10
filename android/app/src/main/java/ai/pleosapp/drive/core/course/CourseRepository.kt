package ai.pleosapp.drive.core.course

import ai.pleosapp.drive.core.drive.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** 지역 검색 결과 한 줄. 예: 경기도 / 부천시 */
data class Region(val id: String, val name: String, val detail: String)

/**
 * 코스를 어디서 가져오는지 감춘다. 지금은 시연용 고정 목록([DemoCourseRepository]),
 * 나중에는 서버(`GET /courses?near=`)에서 가져오는 구현으로 바꿔 끼운다.
 */
interface CourseRepository {
    /** 입력한 글자에 맞는 지역 후보. 없으면 빈 목록. */
    fun searchRegions(query: String): List<Region>

    /** 지역 근처 추천 코스(모양별 대표 한 개씩). */
    fun coursesIn(regionId: String): List<PresetCourse>

    /** 같은 모양의 길이별 코스(짧은 → 긴 순서). 코스 길이 선택 화면용. */
    fun lengthOptions(courseId: String): List<PresetCourse>
}

/**
 * 1차 제출 시연용 고정 목록.
 * - 지역: "경기도"(팀 결정: 도 단위) 또는 "부천"을 입력하면 같은 후보가 나온다.
 * - 코스: A 고양이(진짜 파일, 긴 코스만 선택 가능) + B·C 더미(목록에만 있고 고를 수 없음).
 * - 길이: 고양이 짧은 코스는 더미(고를 수 없음).
 *
 * @param catCourseJson assets/demo/bucheon-cat-91min.json 내용
 */
class DemoCourseRepository(catCourseJson: String) : CourseRepository {

    private val cat: PresetCourse = PresetCourseCodec.decode(catCourseJson, label = "A", place = "부천시")

    private val center = GeoPoint(37.4785, 126.76)

    private val heart = dummy(
        id = "demo-heart", label = "B", name = "하트 코스", km = 26.0, min = 55,
        shape = closedShape(72) { t ->
            val a = t + PI
            val x = 16 * sin(a).pow(3)
            val y = 13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)
            x / 16 * 0.040 to y / 17 * 0.028
        },
    )

    private val star = dummy(
        id = "demo-star", label = "C", name = "별 코스", km = 63.0, min = 105,
        shape = (0..10).map { i ->
            val r = if (i % 2 == 0) 1.0 else 0.45
            val a = PI / 2 + i * PI / 5
            GeoPoint(center.lat + r * sin(a) * 0.035, center.lon + r * cos(a) * 0.045)
        },
    )

    /** 고양이 코스의 짧은 버전(더미). 시작점을 기준으로 모양을 줄인다. */
    private val catShort = cat.copy(
        id = "${cat.id}-short",
        distanceKm = 29.0,
        durationMin = 55,
        preview = cat.preview.map { p ->
            GeoPoint(cat.start.lat + (p.lat - cat.start.lat) * 0.6, cat.start.lon + (p.lon - cat.start.lon) * 0.6)
        },
        enabled = false,
    )

    val regions = listOf(
        Region("gyeonggi", "경기도", "부천·인천 근처 코스"),
        Region("bucheon", "부천시", "경기도"),
    )

    /** 시연에서 실제로 달리는 코스 */
    val demoCourse: PresetCourse get() = cat

    override fun searchRegions(query: String): List<Region> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return regions.filter { it.name.startsWith(q) || q.startsWith(it.name.removeSuffix("시").removeSuffix("도")) }
    }

    override fun coursesIn(regionId: String): List<PresetCourse> =
        if (regions.any { it.id == regionId }) listOf(cat, heart, star) else emptyList()

    override fun lengthOptions(courseId: String): List<PresetCourse> = when (courseId) {
        cat.id, catShort.id -> listOf(catShort, cat)
        heart.id -> listOf(heart)
        star.id -> listOf(star)
        else -> emptyList()
    }

    private fun dummy(id: String, label: String, name: String, km: Double, min: Int, shape: List<GeoPoint>) =
        PresetCourse(
            id = id, label = label, name = name, shapeKind = id.removePrefix("demo-"), area = "부천 근처 (준비 중)",
            place = "부천시", distanceKm = km, durationMin = min, start = shape.first(), waypoints = emptyList(),
            preview = shape, enabled = false,
        )

    private fun closedShape(points: Int, offset: (Double) -> Pair<Double, Double>): List<GeoPoint> {
        val open = (0 until points).map { i ->
            val (dLng, dLat) = offset(2 * PI * i / points)
            GeoPoint(center.lat + dLat, center.lon + dLng)
        }
        return open + open.first()
    }
}
