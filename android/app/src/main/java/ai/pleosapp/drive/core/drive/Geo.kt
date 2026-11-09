package ai.pleosapp.drive.core.drive

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 위도·경도 점. 규격 JSON의 좌표 배열은 [경도, 위도] 순서이니 읽을 때 주의한다(`ContractCodec`). */
data class GeoPoint(val lat: Double, val lon: Double)

private const val EARTH_RADIUS_M = 6_371_008.8
private const val DEG_TO_RAD = PI / 180.0

fun distanceM(a: GeoPoint, b: GeoPoint): Double {
    val dLat = (b.lat - a.lat) * DEG_TO_RAD
    val dLon = (b.lon - a.lon) * DEG_TO_RAD
    val s = sin(dLat / 2) * sin(dLat / 2) +
        cos(a.lat * DEG_TO_RAD) * cos(b.lat * DEG_TO_RAD) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_M * atan2(sqrt(s), sqrt(1 - s))
}

fun lengthM(line: List<GeoPoint>): Double =
    line.zipWithNext { a, b -> distanceM(a, b) }.sum()

/** 점 p를 선분 ab에 내린 결과. [t]는 a(0)에서 b(1)까지의 위치, [distanceM]은 p와의 거리. */
data class SegmentProjection(val t: Double, val distanceM: Double)

/** 짧은 구간 계산이므로 p 위도 기준의 평면 근사를 쓴다. */
fun projectToSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): SegmentProjection {
    val mPerLat = EARTH_RADIUS_M * DEG_TO_RAD
    val mPerLon = mPerLat * cos(p.lat * DEG_TO_RAD)
    val ax = (a.lon - p.lon) * mPerLon
    val ay = (a.lat - p.lat) * mPerLat
    val bx = (b.lon - p.lon) * mPerLon
    val by = (b.lat - p.lat) * mPerLat
    val dx = bx - ax
    val dy = by - ay
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0.0) return SegmentProjection(0.0, hypot(ax, ay))
    val t = min(1.0, max(0.0, -(ax * dx + ay * dy) / lenSq))
    return SegmentProjection(t, hypot(ax + t * dx, ay + t * dy))
}

fun pointToSegmentM(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double = projectToSegment(p, a, b).distanceM

fun pointToLineM(p: GeoPoint, line: List<GeoPoint>): Double {
    if (line.isEmpty()) return Double.POSITIVE_INFINITY
    if (line.size == 1) return distanceM(p, line[0])
    // 주행 중 위치마다 코스 전체(약 1000구간)를 훑으므로 목록을 새로 만들지 않고 최소값만 구한다.
    var best = Double.POSITIVE_INFINITY
    for (i in 0 until line.lastIndex) {
        val d = pointToSegmentM(p, line[i], line[i + 1])
        if (d < best) best = d
    }
    return best
}

/** a에서 b로 향하는 방위각(도, 0~359). 북쪽이 0이고 시계 방향으로 커진다. */
fun bearingDeg(a: GeoPoint, b: GeoPoint): Int {
    val lat1 = a.lat * DEG_TO_RAD
    val lat2 = b.lat * DEG_TO_RAD
    val dLon = (b.lon - a.lon) * DEG_TO_RAD
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return ((atan2(y, x) / DEG_TO_RAD + 360) % 360).toInt()
}

/** 각 점에서 선이 나아가는 방향. 앞뒤 점을 이어 구한다. */
fun headingsAlong(points: List<GeoPoint>): List<Int> =
    points.indices.map { i ->
        bearingDeg(points[maxOf(i - 1, 0)], points[minOf(i + 1, points.lastIndex)])
    }

/** 선을 일정 간격에 가깝게 count개 점으로 다시 나눈다. */
fun resample(line: List<GeoPoint>, count: Int): List<GeoPoint> {
    if (line.size < 2 || count < 2) return line
    val total = lengthM(line)
    if (total == 0.0) return listOf(line.first())
    val step = total / (count - 1)
    val out = ArrayList<GeoPoint>(count)
    out.add(line.first())
    var nextAt = step
    var walked = 0.0
    for ((a, b) in line.zipWithNext()) {
        val seg = distanceM(a, b)
        while (seg > 0 && walked + seg >= nextAt && out.size < count - 1) {
            val t = (nextAt - walked) / seg
            out.add(GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t))
            nextAt += step
        }
        walked += seg
    }
    out.add(line.last())
    return out
}

/** 앞 점에서 minSpacingM 이상 떨어진 점만 남긴다. 처음과 끝 점은 항상 남긴다. 결과 전송용 점 줄이기. */
fun thin(line: List<GeoPoint>, minSpacingM: Double): List<GeoPoint> {
    if (line.size <= 2) return line
    val out = ArrayList<GeoPoint>()
    out.add(line.first())
    for (i in 1 until line.lastIndex) {
        if (distanceM(out.last(), line[i]) >= minSpacingM) out.add(line[i])
    }
    val last = line.last()
    // 끝 점이 바로 앞 점과 너무 가까우면 앞 점을 끝 점으로 바꾼다. 간격 규칙을 지키면서 끝을 남긴다.
    if (out.size > 1 && distanceM(out.last(), last) < minSpacingM) out[out.lastIndex] = last else out.add(last)
    return out
}
