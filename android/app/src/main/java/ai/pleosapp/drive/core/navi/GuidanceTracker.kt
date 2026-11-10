package ai.pleosapp.drive.core.navi

import ai.pleosapp.drive.core.drive.GeoPoint
import ai.pleosapp.drive.core.drive.bearingDeg
import ai.pleosapp.drive.core.drive.distanceM
import ai.pleosapp.drive.core.drive.projectToSegment
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** 위치 한 번에 대한 안내 상태. 거리는 m, 시간은 초. */
data class GuidanceSnapshot(
    /** 구간 경로를 따라 온 거리 */
    val progressM: Double,
    val remainingM: Double,
    /** OSRM 예상 시간으로 잰 남은 시간. 지금 안내 구간 안에서는 거리 비율로 나눈다. */
    val remainingSec: Long,
    /** 앞으로 올 첫 안내. 구간 끝에서는 ARRIVE */
    val next: Maneuver?,
    /** 다음 안내까지 남은 거리. next가 없으면 null */
    val distanceToNextM: Double?,
    /** 현재 위치와 맞춰 본 경로 위 점 사이 거리 */
    val offRouteM: Double,
)

/**
 * 위치를 받아 경로 위 진행 거리와 다음 회전 안내를 계산한다. 시연에서 내비 대신 쓴다.
 *
 * 경로 위치 찾기 규칙 (core/drive의 CourseTracker와 같은 생각):
 * - 처음에는 경로 앞부분([INITIAL_WINDOW_M])에서만 찾는다. 고리 코스는 시작점과 끝점이 같아서, 전체에서 찾으면 끝으로 붙는다.
 * - 그다음부터는 지금 진행 거리 뒤로 [BACKTRACK_M], 앞으로 [LOOK_AHEAD_M] + 직전 위치에서 움직인 거리만큼만 본다.
 *   재생 배속으로 프레임을 건너뛰어 위치가 멀리 뛰어도 따라간다.
 * - 차 방향과 경로 방향이 [MAX_HEADING_DIFF_DEG]보다 다르면 그 구간과 맞추지 않는다. 유턴 구간처럼 같은 도로를
 *   갔다가 돌아오는 곳에서 돌아오는 쪽에 먼저 붙지 않게 한다.
 * - 거의 같은 거리의 후보가 한참 떨어진 두 곳(같은 도로를 두 번 지남)이면 앞쪽(진행 거리가 작은 쪽)을 고른다.
 *   방향을 모를 때(멀리 뛴 위치)도 앞질러 가지 않게 한다. 대신 그 한 번은 왕복 길이만큼 늦을 수 있다.
 * - 진행 거리는 줄어들지 않는다.
 *
 * 신호 순서대로 한 스레드에서만 호출한다.
 */
class GuidanceTracker(val section: RouteSection) {

    private val line = section.geometry
    private val cumulative: DoubleArray = DoubleArray(line.size).also { cum ->
        for (i in 1 until line.size) cum[i] = cum[i - 1] + distanceM(line[i - 1], line[i])
    }
    private val totalM = cumulative.last()
    private val segmentBearings: Array<Int?> = Array(line.size - 1) { i ->
        if (cumulative[i + 1] - cumulative[i] > 0.0) bearingDeg(line[i], line[i + 1]) else null
    }

    /** 알릴 안내만 경로 순서대로. 끝 안내(ARRIVE)가 없으면 구간 끝에 하나 붙인다. */
    private val maneuvers: List<Maneuver> = section.steps.mapNotNull(ManeuverText::of).let { list ->
        if (list.any { it.kind == ManeuverKind.ARRIVE }) list
        else list + Maneuver(ManeuverKind.ARRIVE, "목적지 도착", "", totalM)
    }

    /**
     * 시간 계산용: 안내 지점 i에서 다음 안내 지점까지 걸리는 시간(초).
     * 데이터 생성 때 중간 경유지의 출발 안내를 빼서 step 시간의 합이 구간 시간보다 작다. 그래서 각 안내의
     * 빠르기(초/m)로 다음 안내까지의 실제 거리를 잰 뒤, 합이 구간 OSRM 시간([RouteSection.durationSec])이 되게 맞춘다.
     */
    private val stepStartM: DoubleArray = section.steps.map { it.atM }.toDoubleArray()
    private val spanSec: DoubleArray = run {
        val steps = section.steps
        val avgPace = if (totalM > 0) section.durationSec / totalM else 0.0
        val raw = DoubleArray(steps.size) { i ->
            val to = if (i + 1 < steps.size) steps[i + 1].atM else totalM
            val pace = if (steps[i].lengthM > 0 && steps[i].durationSec > 0) steps[i].durationSec / steps[i].lengthM else avgPace
            max(0.0, to - steps[i].atM) * pace
        }
        val sum = raw.sum()
        val scale = if (sum > 0 && section.durationSec > 0) section.durationSec / sum else 1.0
        DoubleArray(raw.size) { raw[it] * scale }
    }
    private val stepStartSec: DoubleArray = DoubleArray(section.steps.size).also { arr ->
        for (i in 1 until arr.size) arr[i] = arr[i - 1] + spanSec[i - 1]
    }
    private val totalSec: Double =
        if (section.steps.isEmpty()) section.durationSec.toDouble()
        else stepStartSec.last() + spanSec.last()

    private var progressM = 0.0
    private var started = false
    private var lastPoint: GeoPoint? = null
    private var headingFrom: GeoPoint? = null
    private var heading: Int? = null

    fun reset() {
        progressM = 0.0
        started = false
        lastPoint = null
        headingFrom = null
        heading = null
    }

    fun update(point: GeoPoint): GuidanceSnapshot {
        val jumpM = lastPoint?.let { distanceM(it, point) } ?: 0.0
        updateHeading(point, jumpM)
        lastPoint = point

        val (minAlong, maxAlong) = if (!started) {
            0.0 to INITIAL_WINDOW_M
        } else {
            (progressM - BACKTRACK_M) to (progressM + LOOK_AHEAD_M + jumpM * JUMP_FACTOR)
        }
        var match = project(point, minAlong, maxAlong, useHeading = true)
            ?: project(point, minAlong, maxAlong, useHeading = false)
        if (!started && (match == null || match.distanceM > START_SEARCH_ALL_M)) {
            // 경로 앞부분과 너무 멀면(경로 밖에서 시작) 전체에서 찾는다.
            match = project(point, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, useHeading = false) ?: match
        }
        started = true
        val offRoute = match?.distanceM ?: distanceToLine(point)
        if (match != null) progressM = max(progressM, match.alongM.coerceIn(0.0, totalM))
        return snapshot(offRoute)
    }

    private fun snapshot(offRouteM: Double): GuidanceSnapshot {
        val next = maneuvers.firstOrNull { it.atM > progressM + EPS_M }
            ?: maneuvers.lastOrNull()?.takeIf { it.kind == ManeuverKind.ARRIVE && progressM < totalM }
        return GuidanceSnapshot(
            progressM = progressM,
            remainingM = max(0.0, totalM - progressM),
            remainingSec = max(0.0, totalSec - timeAt(progressM)).roundToLong(),
            next = next,
            distanceToNextM = next?.let { max(0.0, it.atM - progressM) },
            offRouteM = offRouteM,
        )
    }

    /** 진행 거리 s까지 걸린 OSRM 예상 시간. 안내 구간 안에서는 거리 비율로 나눈다. */
    private fun timeAt(s: Double): Double {
        if (stepStartM.isEmpty()) return if (totalM > 0) totalSec * s / totalM else 0.0
        var i = 0
        while (i + 1 < stepStartM.size && stepStartM[i + 1] <= s) i++
        val from = stepStartM[i]
        val to = if (i + 1 < stepStartM.size) stepStartM[i + 1] else totalM
        val dur = spanSec[i]
        val f = if (to > from) ((s - from) / (to - from)).coerceIn(0.0, 1.0) else 1.0
        return stepStartSec[i] + dur * f
    }

    private fun updateHeading(point: GeoPoint, jumpM: Double) {
        val from = headingFrom
        when {
            from == null -> {
                headingFrom = point
                heading = null
            }
            // 프레임을 건너뛰어 멀리 뛴 두 점을 이은 방향은 도로 방향이 아니다.
            jumpM > HEADING_MAX_JUMP_M -> {
                headingFrom = point
                heading = null
            }
            distanceM(from, point) >= HEADING_MIN_MOVE_M -> {
                heading = bearingDeg(from, point)
                headingFrom = point
            }
        }
    }

    private data class Match(val alongM: Double, val distanceM: Double)

    private fun project(point: GeoPoint, minAlong: Double, maxAlong: Double, useHeading: Boolean): Match? {
        val candidates = ArrayList<Match>()
        for (i in 0 until line.lastIndex) {
            val segStart = cumulative[i]
            val segEnd = cumulative[i + 1]
            if (segStart > maxAlong || segEnd < minAlong) continue
            if (useHeading && !sameDirection(segmentBearings[i])) continue
            val p = projectToSegment(point, line[i], line[i + 1])
            val along = segStart + p.t * (segEnd - segStart)
            if (along < minAlong || along > maxAlong) continue
            candidates += Match(along, p.distanceM)
        }
        val best = candidates.minByOrNull { it.distanceM } ?: return null
        // 같은 도로를 두 번 지나는 곳(유턴 왕복)에서 거의 같은 거리의 후보가 한참 앞에 있으면 앞쪽(먼저 지나는 쪽)을 고른다.
        // 이웃 구간끼리는 비교하지 않는다(끝점에서 진행 거리가 조금 덜 나오는 것을 막는다).
        return candidates
            .filter { it.distanceM <= best.distanceM + TIE_M && it.alongM < best.alongM - SEPARATE_PASS_M }
            .minByOrNull { it.alongM } ?: best
    }

    private fun sameDirection(segmentBearing: Int?): Boolean {
        val h = heading ?: return true
        val b = segmentBearing ?: return true
        val diff = Math.floorMod(h - b + 180, 360) - 180
        return abs(diff) <= MAX_HEADING_DIFF_DEG
    }

    private fun distanceToLine(point: GeoPoint): Double =
        line.zipWithNext { a, b -> projectToSegment(point, a, b).distanceM }.minOrNull() ?: Double.POSITIVE_INFINITY

    companion object {
        /** 💡 제안 값. 시연 데이터(1초 간격, GPS 오차 약 2.5m)로 맞췄다. */
        const val INITIAL_WINDOW_M = 300.0
        const val START_SEARCH_ALL_M = 100.0
        const val BACKTRACK_M = 50.0
        const val LOOK_AHEAD_M = 300.0
        const val JUMP_FACTOR = 1.5
        const val MAX_HEADING_DIFF_DEG = 90.0
        const val HEADING_MIN_MOVE_M = 8.0
        const val HEADING_MAX_JUMP_M = 150.0
        const val TIE_M = 15.0
        const val SEPARATE_PASS_M = 60.0
        private const val EPS_M = 0.5
    }
}
