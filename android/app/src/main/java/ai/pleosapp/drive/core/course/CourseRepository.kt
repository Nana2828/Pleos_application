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

    /** 지역 근처 추천 코스. 목록 앞 글자(A·B·C)는 목록 순서대로 붙어 있다. */
    fun coursesIn(regionId: String): List<PresetCourse>

    /** 같은 모양의 길이별 코스(짧은 → 긴 순서). 코스 길이 선택 화면용. */
    fun lengthOptions(courseId: String): List<PresetCourse>
}

/** 시연 코스 파일 이름. assets/demo 에 코스마다 세 개씩 있다(`tools/demo/build_demo_data.py`가 만든다). */
object DemoCourseFiles {
    const val CAT = "bucheon-cat-91min"
    const val HEART = "gyeonggi-heart-hwaseong-national-60min"
    const val DOG = "gyeonggi-dogtail-yangju-97min"

    /** 시연에서 실제로 달리는 코스 전부 */
    val ALL = listOf(CAT, HEART, DOG)

    /** 코스 원본 (B 형식) */
    fun course(id: String) = "$id.json"

    /** OSRM 접근·코스 경로와 회전 안내 */
    fun route(id: String) = "$id.route.json"

    /** 가짜 주행 신호 (가산 → 시작점 → 코스 한 바퀴) */
    fun drive(id: String) = "$id.drive.jsonl"

    /** 코스 주변 들를 곳과 왕복 경로 (PLEOS-63, `tools/demo/build_demo_stops.py`). 없는 코스도 있다 */
    fun stops(id: String) = "$id.stops.json"
}

/**
 * 1차 제출 시연용 고정 목록 (PLEOS-62).
 * - 시 이름(부천 / 화성 / 동두천·양주)을 입력하면 그 지역의 진짜 코스 1개 + 더미 2개가 나온다.
 * - "경기도"를 입력하면 진짜 코스 3개만 나온다.
 * - 더미(준비 중)는 목록에만 있고 고를 수 없다(`enabled = false`).
 *
 * @param read assets/demo 안의 파일 이름을 받아 내용을 돌려준다.
 */
class DemoCourseRepository(read: (fileName: String) -> String) : CourseRepository {

    private val cat = load(read, DemoCourseFiles.CAT, place = "부천시")
    private val heart = load(read, DemoCourseFiles.HEART, place = "화성시")
    private val dog = load(read, DemoCourseFiles.DOG, place = "양주시")

    /** 시연에서 실제로 달릴 수 있는 코스 (경기도 목록 순서) */
    val realCourses: List<PresetCourse> = listOf(cat, heart, dog)

    val regions = listOf(
        Region(GYEONGGI, "경기도", "시연 코스 3곳"),
        Region("bucheon", "부천시", "경기도"),
        Region("hwaseong", "화성시", "경기도"),
        Region("dongducheon", "동두천시", "경기도"),
        Region("yangju", "양주시", "경기도"),
    )

    /** 시 이름 → 그 지역의 진짜 코스와 더미 2개 */
    private val cityCourses: Map<String, List<PresetCourse>> = run {
        val bucheon = listOf(cat, dummyHeart(cat.start, "bucheon"), dummyStar(cat.start, "bucheon"))
        val hwaseong = listOf(heart, dummyStar(heart.start, "hwaseong"), dummyCat(heart.start, "hwaseong"))
        val north = listOf(dog, dummyHeart(dog.start, "north"), dummyStar(dog.start, "north"))
        mapOf("bucheon" to bucheon, "hwaseong" to hwaseong, "dongducheon" to north, "yangju" to north)
    }

    fun course(id: String): PresetCourse? = realCourses.firstOrNull { it.id == id }

    /** 지도에 크게 보여 줄 그 지역의 진짜 코스. "경기도"처럼 여러 코스가 있는 지역은 null */
    fun featuredCourse(regionId: String): PresetCourse? = cityCourses[regionId]?.firstOrNull()

    override fun searchRegions(query: String): List<Region> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return regions.filter { it.name.startsWith(q) || q.startsWith(it.name.removeSuffix("시").removeSuffix("도")) }
    }

    override fun coursesIn(regionId: String): List<PresetCourse> {
        val list = if (regionId == GYEONGGI) realCourses else cityCourses[regionId].orEmpty()
        return list.mapIndexed { i, c -> c.copy(label = ('A' + i).toString()) }
    }

    override fun lengthOptions(courseId: String): List<PresetCourse> =
        (realCourses + cityCourses.values.flatten()).filter { it.id == courseId }.take(1)

    // ---------- 더미 ----------

    private fun dummy(id: String, name: String, km: Double, min: Int, shape: List<GeoPoint>) = PresetCourse(
        id = id, label = "", name = name, shapeKind = id.substringAfter("dummy-").substringBefore('-'),
        area = "준비 중", place = "", distanceKm = km, durationMin = min, start = shape.first(), waypoints = emptyList(),
        preview = shape, enabled = false,
    )

    private fun dummyHeart(near: GeoPoint, region: String) = dummy(
        "dummy-heart-$region", "하트 코스", 26.0, 55,
        closedShape(near, 72) { t ->
            val a = t + PI
            val x = 16 * sin(a).pow(3)
            val y = 13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)
            x / 16 * 0.040 to y / 17 * 0.028
        },
    )

    private fun dummyStar(near: GeoPoint, region: String) = dummy(
        "dummy-star-$region", "별 코스", 63.0, 105,
        (0..10).map { i ->
            val r = if (i % 2 == 0) 1.0 else 0.45
            val a = PI / 2 + i * PI / 5
            GeoPoint(near.lat + r * sin(a) * 0.035, near.lon + r * cos(a) * 0.045)
        },
    )

    /** 고양이 코스 모양을 그대로 옮겨 쓴 더미 (목록 그림용) */
    private fun dummyCat(near: GeoPoint, region: String) = dummy(
        "dummy-cat-$region", "고양이 코스", 49.7, 91,
        cat.preview.map { p -> GeoPoint(near.lat + (p.lat - cat.start.lat), near.lon + (p.lon - cat.start.lon)) },
    )

    private fun closedShape(center: GeoPoint, points: Int, offset: (Double) -> Pair<Double, Double>): List<GeoPoint> {
        val open = (0 until points).map { i ->
            val (dLng, dLat) = offset(2 * PI * i / points)
            GeoPoint(center.lat + dLat, center.lon + dLng)
        }
        return open + open.first()
    }

    companion object {
        const val GYEONGGI = "gyeonggi"

        private fun load(read: (String) -> String, id: String, place: String): PresetCourse {
            val course = PresetCourseCodec.decode(read(DemoCourseFiles.course(id)), label = "A", place = place)
            require(course.id == id) { "${DemoCourseFiles.course(id)}의 id가 ${course.id}입니다" }
            return course
        }
    }
}
