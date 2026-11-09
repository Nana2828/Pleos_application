package ai.pleosapp.drive.core.course

import ai.pleosapp.drive.core.drive.Course
import ai.pleosapp.drive.core.drive.GeoPoint
import ai.pleosapp.drive.core.drive.NamedPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToInt

/** 코스 길이 구분. 목록의 "짧은 코스 / 긴 코스" 필터에 쓴다. */
enum class CourseLength { SHORT, LONG }

/**
 * 미리 만들어 둔(프리셋) 코스 한 개. 목록·미리보기·안내·판정에 필요한 값을 모은다.
 *
 * 좌표는 모두 [GeoPoint](위도·경도). B가 만든 파일은 `{lat, lng}`와 `[위도, 경도]` 배열을 쓰므로
 * [PresetCourseCodec]이 읽을 때 바꾼다.
 */
data class PresetCourse(
    val id: String,
    /** 목록 앞 글자. 예: "A" */
    val label: String,
    /** 예: "부천·인천 고양이 코스" */
    val name: String,
    /** 모양 이름. 예: "cat" */
    val shapeKind: String,
    /** 사람이 읽는 지역 설명. 예: "부천 소사구·오정구, 인천 남동구·부평구" */
    val area: String,
    /** 결과 화면 "위치" 칸에 쓸 짧은 지역 이름. 예: "부천시" */
    val place: String,
    val distanceKm: Double,
    val durationMin: Int,
    val start: GeoPoint,
    /** 지나는 지점들(시작점 제외) */
    val waypoints: List<NamedPoint>,
    /** 지도 미리보기에 그릴 모양 */
    val preview: List<GeoPoint>,
    /** 시연에서 고를 수 있으면 true. 더미(준비 중) 코스는 false */
    val enabled: Boolean = true,
) {
    val length: CourseLength get() = if (distanceKm < SHORT_LIMIT_KM) CourseLength.SHORT else CourseLength.LONG

    /**
     * 진입·완주 판정용 코스. [trackShape]가 있으면(시연: OSRM 도로 경로) 그것을 기준선으로 쓰고,
     * 없으면 미리보기 모양을 쓴다. 미리보기 모양은 점 간격이 넓어 도로와 어긋날 수 있다.
     */
    fun toCourse(trackShape: List<GeoPoint>? = null, region: String = place): Course = Course(
        courseId = id,
        shape = trackShape?.takeIf { it.size >= 2 } ?: preview,
        start = NamedPoint(start, "시작점"),
        destination = NamedPoint(start, "시작점"),
        waypoints = waypoints,
        region = region,
        label = label,
        name = name,
        distanceM = distanceKm * 1000,
        estDurationSec = durationMin * 60L,
    )

    companion object {
        /** 이보다 짧으면 "짧은 코스". 💡 제안 값, 팀 합의 전 */
        const val SHORT_LIMIT_KM = 30.0
    }
}

/**
 * B가 만든 코스 파일 읽기 (예: assets/demo/bucheon-cat-91min.json).
 * 팀 규격 ① course/1과 다른 형식이다: 좌표가 `{lat, lng}`, `routePreview`는 `[위도, 경도]` 배열.
 */
object PresetCourseCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String, label: String, place: String, enabled: Boolean = true): PresetCourse {
        val obj = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("코스 파일을 JSON으로 읽지 못했습니다: ${e.message}", e)
        } ?: throw IllegalArgumentException("코스 파일이 JSON 객체가 아닙니다")

        val start = point(obj["start"], "start")
        val points = (obj["points"] as? JsonArray).orEmpty().mapIndexed { i, e ->
            val o = e as? JsonObject ?: throw IllegalArgumentException("points[$i]가 객체가 아닙니다")
            NamedPoint(point(o, "points[$i]"), (o["name"] as? JsonPrimitive)?.content.orEmpty())
        }
        val preview = (obj["routePreview"] as? JsonArray).orEmpty().mapIndexed { i, e ->
            val pair = e as? JsonArray ?: throw IllegalArgumentException("routePreview[$i]가 배열이 아닙니다")
            val lat = (pair.getOrNull(0) as? JsonPrimitive)?.doubleOrNull
            val lng = (pair.getOrNull(1) as? JsonPrimitive)?.doubleOrNull
            if (lat == null || lng == null) throw IllegalArgumentException("routePreview[$i]는 [위도, 경도]여야 합니다")
            checked(lat, lng, "routePreview[$i]")
        }
        require(preview.size >= 2) { "routePreview 점이 2개 이상이어야 합니다" }

        return PresetCourse(
            id = string(obj, "id"),
            label = label,
            name = string(obj, "name"),
            shapeKind = (obj["shape"] as? JsonPrimitive)?.content.orEmpty(),
            area = (obj["area"] as? JsonPrimitive)?.content.orEmpty(),
            place = place,
            distanceKm = number(obj, "distanceKm"),
            durationMin = number(obj, "durationMin").roundToInt(),
            start = start,
            // points[0]은 시작점이라 뺀다
            waypoints = points.drop(1),
            preview = preview,
            enabled = enabled,
        )
    }

    private fun point(e: JsonElement?, where: String): GeoPoint {
        val o = e as? JsonObject ?: throw IllegalArgumentException("$where 가 없거나 객체가 아닙니다")
        val lat = (o["lat"] as? JsonPrimitive)?.doubleOrNull
        val lng = (o["lng"] as? JsonPrimitive)?.doubleOrNull ?: (o["lon"] as? JsonPrimitive)?.doubleOrNull
        if (lat == null || lng == null) throw IllegalArgumentException("$where 에 lat·lng가 없습니다")
        return checked(lat, lng, where)
    }

    private fun checked(lat: Double, lng: Double, where: String): GeoPoint {
        require(lat in -90.0..90.0 && lng in -180.0..180.0) { "$where 좌표가 범위를 벗어났습니다: $lat, $lng" }
        return GeoPoint(lat, lng)
    }

    private fun string(o: JsonObject, key: String): String =
        (o[key] as? JsonPrimitive)?.content ?: throw IllegalArgumentException("$key 가 없습니다")

    private fun number(o: JsonObject, key: String): Double =
        (o[key] as? JsonPrimitive)?.doubleOrNull ?: throw IllegalArgumentException("$key 숫자가 없습니다")
}
