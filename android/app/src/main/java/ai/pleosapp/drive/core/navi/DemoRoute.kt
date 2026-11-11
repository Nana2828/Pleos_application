package ai.pleosapp.drive.core.navi

import ai.pleosapp.drive.core.drive.GeoPoint
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 회전 안내 한 개. OSRM `steps[]`를 `android/tools/demo/build_demo_data.py`가 줄인 것.
 * [atM]은 구간 경로(geometry)를 따라 잰, 구간 시작점에서 안내 지점까지의 거리.
 */
data class RouteStep(
    val atM: Double,
    /** OSRM maneuver.type. 예: "turn", "continue", "roundabout", "arrive" */
    val type: String,
    /** OSRM maneuver.modifier. 예: "left", "slight right", "uturn". 없으면 "" */
    val modifier: String,
    /** 이 안내 다음에 달릴 도로 이름. 없으면 "" */
    val name: String,
    /** 회전교차로 출구 번호. 없으면 null */
    val exit: Int?,
    /** 이 안내부터 다음 안내까지 OSRM 거리(m) */
    val lengthM: Double,
    /** 이 안내부터 다음 안내까지 OSRM 예상 시간(초) */
    val durationSec: Double,
    val location: GeoPoint,
)

/** 경로 한 구간(접근 또는 코스). [geometry]는 촘촘한 도로 좌표. */
data class RouteSection(
    val distanceM: Double,
    val durationSec: Long,
    val geometry: List<GeoPoint>,
    val steps: List<RouteStep>,
)

/**
 * 시연용 경로 파일(`assets/demo/demo-route.json`).
 * 접근 구간 = 가짜 현재 위치 → 코스 시작점, 코스 구간 = 코스 한 바퀴.
 * 에뮬레이터 내비가 위치를 따라가지 않아서 시연 때는 이 안내를 앱이 재생한다. 최종 버전은 NaviHelper(PLEOS-36).
 * 경로 데이터 출처: © OpenStreetMap contributors (ODbL), OSRM.
 */
data class DemoRoute(
    val courseId: String,
    val origin: GeoPoint,
    val approach: RouteSection,
    val course: RouteSection,
)

/** `demo-route.json` 읽기. 좌표 배열은 [경도, 위도], 점 객체는 {"lat", "lon"}. 틀린 입력은 [IllegalArgumentException]. */
object DemoRouteCodec {
    const val FORMAT = "demo-route"

    private val json = Json

    fun decode(text: String): DemoRoute {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            throw IllegalArgumentException("시연 경로 JSON을 읽을 수 없습니다: ${e.message}", e)
        } ?: throw IllegalArgumentException("시연 경로는 JSON 객체여야 합니다")

        root.optString("format")?.let {
            require(it == FORMAT) { "시연 경로 format이 $FORMAT 이 아닙니다: $it" }
        }
        val originObj = root.obj("origin")
        return DemoRoute(
            courseId = root.string("courseId"),
            origin = point(originObj.number("lat"), originObj.number("lon"), "origin"),
            approach = section(root.obj("approach"), "approach"),
            course = section(root.obj("course"), "course"),
        )
    }

    /** 구간 하나(접근·코스, PLEOS-63 들를 곳의 가는 길·돌아오는 길). `DemoStopsCodec`도 쓴다. */
    internal fun section(obj: JsonObject, where: String): RouteSection {
        val geometry = coordinates(obj["geometry"], "$where.geometry")
        require(geometry.size >= 2) { "$where.geometry 점이 2개 이상이어야 합니다" }
        val stepsArr = obj["steps"] as? JsonArray ?: throw IllegalArgumentException("$where.steps 배열이 없습니다")
        val steps = stepsArr.mapIndexed { i, e ->
            val s = e as? JsonObject ?: throw IllegalArgumentException("$where.steps[$i]는 객체여야 합니다")
            val loc = coordinate(s["location"], "$where.steps[$i].location")
            RouteStep(
                atM = s.number("atM"),
                type = s.string("type"),
                modifier = s.optString("modifier").orEmpty(),
                name = s.optString("name").orEmpty(),
                exit = (s["exit"] as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }?.intOrNull,
                lengthM = s.optNumber("lengthM") ?: 0.0,
                durationSec = s.optNumber("durationSec") ?: 0.0,
                location = loc,
            )
        }
        for (i in 1 until steps.size) {
            require(steps[i].atM >= steps[i - 1].atM) { "$where.steps의 atM은 앞에서 뒤로 커져야 합니다 (${i}번째)" }
        }
        return RouteSection(
            distanceM = obj.number("distanceM"),
            durationSec = obj.number("durationSec").toLong(),
            geometry = geometry,
            steps = steps,
        )
    }

    private fun coordinates(element: JsonElement?, where: String): List<GeoPoint> {
        val arr = element as? JsonArray ?: throw IllegalArgumentException("$where 배열이 없습니다")
        return arr.mapIndexed { i, e -> coordinate(e, "$where[$i]") }
    }

    /** [경도, 위도] 한 개 */
    private fun coordinate(element: JsonElement?, where: String): GeoPoint {
        val pair = element as? JsonArray ?: throw IllegalArgumentException("$where 는 [경도, 위도] 배열이어야 합니다")
        require(pair.size >= 2) { "$where 는 [경도, 위도] 두 값이 있어야 합니다" }
        val lon = pair[0].numberOrNull() ?: throw IllegalArgumentException("$where 경도가 숫자가 아닙니다")
        val lat = pair[1].numberOrNull() ?: throw IllegalArgumentException("$where 위도가 숫자가 아닙니다")
        return point(lat, lon, where)
    }

    private fun point(lat: Double, lon: Double, where: String): GeoPoint {
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "$where 좌표 범위 오류 (위도 $lat, 경도 $lon). [경도, 위도] 순서인지 확인" }
        return GeoPoint(lat, lon)
    }

    private fun JsonElement.numberOrNull(): Double? =
        (this as? JsonPrimitive)?.takeUnless { it.isString || it is JsonNull }?.doubleOrNull?.takeIf { it.isFinite() }

    private fun JsonObject.obj(key: String): JsonObject =
        this[key] as? JsonObject ?: throw IllegalArgumentException("$key 객체가 없습니다")

    private fun JsonObject.string(key: String): String =
        optString(key) ?: throw IllegalArgumentException("$key 문자열이 없습니다")

    private fun JsonObject.optString(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Double =
        optNumber(key) ?: throw IllegalArgumentException("$key 숫자가 없습니다")

    private fun JsonObject.optNumber(key: String): Double? = this[key]?.numberOrNull()
}
