package ai.pleosapp.drive.core.navi

import ai.pleosapp.drive.core.drive.DriveSignal
import ai.pleosapp.drive.core.drive.GeoPoint
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** 가짜 주행 한 점: 들를 곳 구간 시작부터 [sec]초 뒤의 위치와 속도. */
data class DetourSample(val sec: Int, val point: GeoPoint, val kph: Double)

/**
 * 코스 주변 들를 곳 하나 (PLEOS-63). `android/tools/demo/build_demo_stops.py`가 만든다.
 *
 * 코스 위 [exitAtM] 지점(= 왕복 도로 거리가 가장 짧은 코스 점)에서 기록을 멈추고 [outbound]로 장소까지 간 뒤,
 * [inbound]로 같은 지점에 코스 방향으로 돌아와 기록을 이어 간다.
 */
data class CourseStop(
    val id: String,
    val name: String,
    val address: String,
    val location: GeoPoint,
    /** 코스 경로(route.json의 course.geometry)를 따라 잰, 코스 시작에서 이탈 지점까지 거리 */
    val exitAtM: Double,
    val exit: GeoPoint,
    /** 가는 길 + 돌아오는 길 도로 거리 */
    val extraDistanceM: Double,
    /** 가는 길 + 돌아오는 길 OSRM 예상 시간(머무는 시간 제외) */
    val extraDurationSec: Long,
    val outbound: RouteSection,
    val inbound: RouteSection,
    val outboundDrive: List<DetourSample>,
    val inboundDrive: List<DetourSample>,
)

/** [DetourSample] 목록을 재생 신호(속도·위치)로 바꾼다. [startMs]는 첫 점의 시각. */
fun List<DetourSample>.toSignals(startMs: Long): List<DriveSignal> = flatMap {
    val t = startMs + it.sec * 1000L
    listOf(DriveSignal.Speed(t, it.kph), DriveSignal.Position(t, it.point, 5.0))
}

/** `<코스 id>.stops.json` 읽기. 좌표 배열은 [경도, 위도]. 틀린 입력은 [IllegalArgumentException]. */
object DemoStopsCodec {
    const val FORMAT = "demo-stops"

    fun decode(text: String): List<CourseStop> {
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            throw IllegalArgumentException("들를 곳 JSON을 읽을 수 없습니다: ${e.message}", e)
        } ?: throw IllegalArgumentException("들를 곳 파일은 JSON 객체여야 합니다")
        val format = (root["format"] as? JsonPrimitive)?.content
        require(format == FORMAT) { "들를 곳 format이 $FORMAT 이 아닙니다: $format" }
        val arr = root["stops"] as? JsonArray ?: throw IllegalArgumentException("stops 배열이 없습니다")
        return arr.mapIndexed { i, e ->
            val o = e as? JsonObject ?: throw IllegalArgumentException("stops[$i]는 객체여야 합니다")
            val where = "stops[$i]"
            val exit = o["exit"] as? JsonObject ?: throw IllegalArgumentException("$where.exit 객체가 없습니다")
            CourseStop(
                id = o.string("id", where),
                name = o.string("name", where),
                address = (o["address"] as? JsonPrimitive)?.content.orEmpty(),
                location = lonLat(o["location"], "$where.location"),
                exitAtM = exit.number("atM", "$where.exit"),
                exit = lonLat(exit["location"], "$where.exit.location"),
                extraDistanceM = o.number("extraDistanceM", where),
                extraDurationSec = o.number("extraDurationSec", where).toLong(),
                outbound = DemoRouteCodec.section(o.obj("outbound", where), "$where.outbound"),
                inbound = DemoRouteCodec.section(o.obj("inbound", where), "$where.inbound"),
                outboundDrive = samples(o["outboundDrive"], "$where.outboundDrive"),
                inboundDrive = samples(o["inboundDrive"], "$where.inboundDrive"),
            )
        }.sortedBy { it.exitAtM }
    }

    /** [[초, 경도, 위도, km/h], ...] */
    private fun samples(e: kotlinx.serialization.json.JsonElement?, where: String): List<DetourSample> {
        val arr = e as? JsonArray ?: throw IllegalArgumentException("$where 배열이 없습니다")
        require(arr.size >= 2) { "$where 점이 2개 이상이어야 합니다" }
        var last = -1
        return arr.mapIndexed { i, row ->
            val r = row as? JsonArray ?: throw IllegalArgumentException("$where[$i]는 배열이어야 합니다")
            require(r.size >= 4) { "$where[$i]는 [초, 경도, 위도, km/h]여야 합니다" }
            val v = r.map { (it as? JsonPrimitive)?.takeUnless { p -> p.isString || p is JsonNull }?.doubleOrNull }
            require(v.all { it != null && it.isFinite() }) { "$where[$i]에 숫자가 아닌 값이 있습니다" }
            val sec = v[0]!!.toInt()
            require(sec > last) { "$where[$i] 시간이 앞으로 가지 않습니다" }
            last = sec
            DetourSample(sec, checked(v[2]!!, v[1]!!, "$where[$i]"), v[3]!!.coerceAtLeast(0.0))
        }
    }

    private fun lonLat(e: kotlinx.serialization.json.JsonElement?, where: String): GeoPoint {
        val pair = e as? JsonArray ?: throw IllegalArgumentException("$where 는 [경도, 위도] 배열이어야 합니다")
        val lon = (pair.getOrNull(0) as? JsonPrimitive)?.doubleOrNull
        val lat = (pair.getOrNull(1) as? JsonPrimitive)?.doubleOrNull
        if (lon == null || lat == null) throw IllegalArgumentException("$where 는 [경도, 위도] 숫자여야 합니다")
        return checked(lat, lon, where)
    }

    private fun checked(lat: Double, lon: Double, where: String): GeoPoint {
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "$where 좌표 범위 오류 (위도 $lat, 경도 $lon)" }
        return GeoPoint(lat, lon)
    }

    private fun JsonObject.obj(key: String, where: String): JsonObject =
        this[key] as? JsonObject ?: throw IllegalArgumentException("$where.$key 객체가 없습니다")

    private fun JsonObject.string(key: String, where: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("$where.$key 문자열이 없습니다")

    private fun JsonObject.number(key: String, where: String): Double =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull ?: throw IllegalArgumentException("$where.$key 숫자가 없습니다")
}
