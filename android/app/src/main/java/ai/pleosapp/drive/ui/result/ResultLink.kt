package ai.pleosapp.drive.ui.result

import ai.pleosapp.drive.ui.state.EfficiencyUnit
import ai.pleosapp.drive.ui.state.MapPoint
import ai.pleosapp.drive.ui.state.ResultUiState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 결과 화면 QR 에 넣을 주소를 만들어요 (PLEOS-46, 폰 웹은 은빈 님 PLEOS-48).
 *
 * 서버가 아직 없어서, 이번 주행 결과를 주소 안에 같이 담아요:
 * `{웹 기본 주소}?tripId={주행 ID}&d={결과 JSON 을 base64url 로 바꾼 글자}`
 * 웹은 `d` 가 있으면 샘플 데이터 대신 이 값으로 결과를 그려요. `d` 를 풀면 이런 JSON 이에요:
 *
 * | 키 | 뜻 | 예 |
 * |---|---|---|
 * | `v` | 형식 번호 | 1 |
 * | `id` | 주행 ID (tripId 와 같음) | "r1731234567890" |
 * | `course` / `region` | 코스 이름 / 지역 | "부천·인천 고양이 코스" / "부천시" |
 * | `start` / `end` | 시작·끝 시각 (한국 시각, 초까지) | "2026-11-10T11:57:00" |
 * | `km` / `sec` | 주행 거리(km) / 주행 시간(초) | 50.3 / 5427 |
 * | `eff` / `unit` | 평균 연비 숫자 / 단위("kwh" 또는 "l"). 모르면 둘 다 없음 | 6.0 / "kwh" |
 * | `pl` | 달린 길. 구글 인코딩 폴리라인(소수 5자리, 점 순서는 위도,경도) | "_p~iF~ps|U..." |
 *
 * QR 이 너무 빽빽해지지 않게 길은 최대 [MAX_POINTS] 개 점으로 줄여요.
 */
object ResultLink {
    const val MAX_POINTS = 80
    private val kst = ZoneId.of("Asia/Seoul")
    private val isoLocal = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /** 웹이 허용하는 ID 모양(영문·숫자·밑줄·하이픈 1~64자)으로 만든 주행 ID */
    fun tripId(s: ResultUiState): String = "r${s.startedAtEpochMs}"

    /** QR 에 넣을 전체 주소. [base] 는 재원 님 `resultUrl`(웹 기본 주소). */
    fun build(base: String, s: ResultUiState): String {
        val sep = if (base.contains('?')) "&" else "?"
        val d = Base64.getUrlEncoder().withoutPadding().encodeToString(payload(s).toString().toByteArray(Charsets.UTF_8))
        return "$base${sep}tripId=${tripId(s)}&d=$d"
    }

    internal fun payload(s: ResultUiState): JsonObject = buildJsonObject {
        put("v", 1)
        put("id", tripId(s))
        put("course", s.courseName)
        put("region", s.regionName)
        put("start", isoLocal.format(Instant.ofEpochMilli(s.startedAtEpochMs).atZone(kst)))
        put("end", isoLocal.format(Instant.ofEpochMilli(s.endedAtEpochMs).atZone(kst)))
        put("km", (s.distanceKm * 100).roundToLong() / 100.0)
        put("sec", s.durationSec)
        s.efficiency?.let {
            put("eff", (it.value * 10).roundToLong() / 10.0)
            put("unit", if (it.unit == EfficiencyUnit.KM_PER_KWH) "kwh" else "l")
        }
        put("pl", JsonPrimitive(encodePolyline(shrink(s.drivenPath, MAX_POINTS))))
    }

    /** 처음과 끝을 지키면서 일정 간격으로 [max] 개 이하로 줄여요. */
    internal fun shrink(points: List<MapPoint>, max: Int): List<MapPoint> {
        if (points.size <= max) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return List(max) { points[(it * step).roundToInt()] }
    }

    /** 구글 인코딩 폴리라인(소수 5자리). */
    internal fun encodePolyline(points: List<MapPoint>): String {
        val out = StringBuilder()
        var lastLat = 0L
        var lastLng = 0L
        fun put(value: Long) {
            var v = if (value < 0) (value shl 1).inv() else value shl 1
            while (v >= 0x20) {
                out.append((((v and 0x1f) or 0x20) + 63).toInt().toChar())
                v = v shr 5
            }
            out.append((v + 63).toInt().toChar())
        }
        for (p in points) {
            val lat = (p.lat * 1e5).roundToLong()
            val lng = (p.lng * 1e5).roundToLong()
            put(lat - lastLat)
            put(lng - lastLng)
            lastLat = lat
            lastLng = lng
        }
        return out.toString()
    }

    /** 시험용: [encodePolyline] 을 되돌려요. */
    internal fun decodePolyline(text: String): List<MapPoint> {
        val points = mutableListOf<MapPoint>()
        var i = 0
        var lat = 0L
        var lng = 0L
        fun next(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                val b = text[i++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
                if (b < 0x20) break
            }
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        while (i < text.length) {
            lat += next()
            lng += next()
            points += MapPoint(lat / 1e5, lng / 1e5)
        }
        return points
    }
}
