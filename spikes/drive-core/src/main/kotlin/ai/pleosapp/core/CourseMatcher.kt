package ai.pleosapp.core

import kotlin.math.ceil
import kotlin.math.max

/**
 * 코스 진입·완주·일치율 판정 값. 💡 제안 값이며 실차로 검증하지 않았다.
 * 에뮬레이터 가상주행(1초 간격 위치)을 기준으로 정했다.
 */
data class CourseMatchParams(
    /** 출발점에서 이 거리 안에 들어와야 진입 준비가 된다. */
    val startRadiusM: Double = 50.0,
    /** 출발점 근처에서 코스 방향으로 이만큼 나아가면 진입으로 본다. 반대 방향 주행을 거른다. */
    val entryProgressM: Double = 30.0,
    /** 진입 판정 때 코스 앞부분 몇 m 안에서만 위치를 맞춰 본다. 고리 코스의 끝부분과 헷갈리지 않게 한다. */
    val entryWindowM: Double = 300.0,
    /** 도착점에서 이 거리 안에 들어오면 도착 후보. */
    val finishRadiusM: Double = 50.0,
    /** 코스 길이의 이 비율 이상 왔어야 도착으로 본다. 고리 코스 출발 직후 "완주" 오판을 막는다. */
    val minFinishProgressRatio: Double = 0.8,
    /** 진행 위치를 찾을 때 뒤로 볼 거리. GPS 흔들림 대비. */
    val backtrackM: Double = 100.0,
    /** 진행 위치를 찾을 때 앞으로 볼 기본 거리. */
    val lookAheadM: Double = 400.0,
    /** 위치가 끊긴 동안 이 속도로 갔다고 보고 앞으로 볼 거리를 늘린다(터널 등). */
    val maxCourseSpeedMps: Double = 40.0,
    /** 커버리지를 잴 때 코스 선을 이 간격으로 나눈다. */
    val coverageStepM: Double = 10.0,
)

sealed interface CourseEvent {
    /** [timeMs]는 출발점 반경 안에 마지막으로 있던 시각(= 출발점 통과). [confirmedAtMs]는 진입이 확정된 시각. */
    data class Entered(val timeMs: Long, val confirmedAtMs: Long) : CourseEvent
    data class Finished(val timeMs: Long, val progressRatio: Double) : CourseEvent
}

/**
 * 주행 중 위치를 하나씩 받아 코스 진입·완주를 판정한다. 화면이 "기록 중"으로 바뀌는 시점을 정하는 데 쓴다.
 * 신호 순서대로 한 스레드에서만 호출한다.
 */
class CourseTracker(
    val course: Course,
    private val params: CourseMatchParams = CourseMatchParams(),
) {
    enum class Phase { WAITING, ON_COURSE, FINISHED }

    private val shape = course.shape
    private val corridor = course.corridorM
    /** shape 각 꼭짓점까지 선을 따라 잰 거리. */
    private val cumulative: DoubleArray = DoubleArray(shape.size).also { cum ->
        for (i in 1 until shape.size) cum[i] = cum[i - 1] + distanceM(shape[i - 1], shape[i])
    }
    private val totalM = cumulative.last()

    var phase: Phase = Phase.WAITING
        private set
    /** 코스를 따라 가장 멀리 온 거리(m). */
    var progressM: Double = 0.0
        private set
    var enteredAtMs: Long? = null
        private set
    var finishedAtMs: Long? = null
        private set

    val progressRatio: Double get() = if (totalM > 0) (progressM / totalM).coerceIn(0.0, 1.0) else 0.0

    /** 진입 후 위치 중 코스 폭 안에 있던 비율. 아직 진입 전이면 null. */
    val onCourseRatio: Double? get() = if (pointsTotal == 0) null else pointsInCorridor.toDouble() / pointsTotal

    private var armed = false
    private var armedMinAlongM = Double.MAX_VALUE
    private var lastNearStartMs = 0L
    private var lastMatchMs = 0L
    private var pointsTotal = 0
    private var pointsInCorridor = 0

    fun update(timeMs: Long, point: GeoPoint): CourseEvent? = when (phase) {
        Phase.WAITING -> waitForEntry(timeMs, point)
        Phase.ON_COURSE -> follow(timeMs, point)
        Phase.FINISHED -> null
    }

    private fun waitForEntry(timeMs: Long, point: GeoPoint): CourseEvent? {
        val nearStart = distanceM(point, course.start.point) <= params.startRadiusM
        val match = project(point, 0.0, params.entryWindowM)
        val inCorridor = match != null && match.distanceM <= corridor
        if (nearStart) {
            if (!armed) armedMinAlongM = Double.MAX_VALUE
            armed = true
            lastNearStartMs = timeMs
        }
        if (!armed) return null
        if (inCorridor) armedMinAlongM = minOf(armedMinAlongM, match!!.alongM)
        // "코스 방향으로 나아갔다" = 준비된 뒤 가장 뒤에 있던 곳보다 entryProgressM 이상 앞. 출발점 쪽으로 거꾸로 오는 차를 거른다.
        if (inCorridor && match!!.alongM - armedMinAlongM >= params.entryProgressM) {
            phase = Phase.ON_COURSE
            enteredAtMs = lastNearStartMs
            progressM = match.alongM
            lastMatchMs = timeMs
            countPoint(point)
            return CourseEvent.Entered(lastNearStartMs, timeMs)
        }
        // 출발점을 벗어났는데 코스 앞부분 위에도 없으면 다시 출발점에 올 때까지 기다린다.
        if (!nearStart && !inCorridor) armed = false
        return null
    }

    private fun follow(timeMs: Long, point: GeoPoint): CourseEvent? {
        countPoint(point)
        val elapsedS = max(0L, timeMs - lastMatchMs) / 1000.0
        val ahead = params.lookAheadM + elapsedS * params.maxCourseSpeedMps
        val match = project(point, progressM - params.backtrackM, progressM + ahead)
        if (match != null && match.distanceM <= corridor) {
            progressM = max(progressM, match.alongM)
            lastMatchMs = timeMs
        }
        val atDestination = distanceM(point, course.destination.point) <= params.finishRadiusM
        if (atDestination && progressRatio >= params.minFinishProgressRatio) {
            phase = Phase.FINISHED
            finishedAtMs = timeMs
            return CourseEvent.Finished(timeMs, progressRatio)
        }
        return null
    }

    private fun countPoint(point: GeoPoint) {
        pointsTotal++
        if (pointToLineM(point, shape) <= corridor) pointsInCorridor++
    }

    private data class Match(val alongM: Double, val distanceM: Double)

    /** 코스 선의 [minAlong, maxAlong] 구간에서 point에 가장 가까운 곳. 구간 밖으로 떨어지면 후보에서 뺀다. */
    private fun project(point: GeoPoint, minAlong: Double, maxAlong: Double): Match? {
        var best: Match? = null
        for (i in 0 until shape.lastIndex) {
            val segStart = cumulative[i]
            val segEnd = cumulative[i + 1]
            if (segStart > maxAlong || segEnd < minAlong) continue
            val p = projectToSegment(point, shape[i], shape[i + 1])
            val along = segStart + p.t * (segEnd - segStart)
            if (along < minAlong || along > maxAlong) continue
            if (best == null || p.distanceM < best.distanceM) best = Match(along, p.distanceM)
        }
        return best
    }
}

/** 한 주행을 코스와 비교한 결과. */
data class CourseMatch(
    val entered: Boolean,
    val enteredAtMs: Long?,
    val finished: Boolean,
    val finishedAtMs: Long?,
    /** 코스 길이 중 어디까지 왔나 0~1 */
    val progressRatio: Double,
    /** 규격 ③ `on_course_ratio`: 진입~도착 사이 위치 중 코스 폭 안에 있던 비율 0~1. 진입 못 했으면 null */
    val onCourseRatio: Double?,
    /** 코스 선 중 실제로 지나간 부분의 비율 0~1. 지름길을 거르는 데 쓴다. (💡 규격 ③에는 아직 없음) */
    val coverageRatio: Double,
)

object CourseMatcher {
    /** 기록된 궤적 전체를 한 번에 판정한다. 결과 화면·서버 전송용. */
    fun evaluate(
        course: Course,
        track: List<TrackPoint>,
        params: CourseMatchParams = CourseMatchParams(),
    ): CourseMatch {
        val tracker = CourseTracker(course, params)
        var firstIndex = -1
        var lastIndex = track.lastIndex
        for ((i, tp) in track.withIndex()) {
            when (tracker.update(tp.timeMs, tp.point)) {
                is CourseEvent.Entered -> firstIndex = i
                is CourseEvent.Finished -> {
                    lastIndex = i
                    break
                }
                null -> Unit
            }
        }
        val onCourse = if (firstIndex < 0) emptyList() else track.subList(firstIndex, lastIndex + 1).map { it.point }
        return CourseMatch(
            entered = firstIndex >= 0,
            enteredAtMs = tracker.enteredAtMs,
            finished = tracker.phase == CourseTracker.Phase.FINISHED,
            finishedAtMs = tracker.finishedAtMs,
            progressRatio = tracker.progressRatio,
            onCourseRatio = tracker.onCourseRatio,
            coverageRatio = coverageRatio(course, onCourse, params.coverageStepM),
        )
    }

    /** 코스 선을 stepM 간격 점으로 나눠, 궤적에서 코스 폭 안에 있는 점의 비율. */
    fun coverageRatio(course: Course, track: List<GeoPoint>, stepM: Double = CourseMatchParams().coverageStepM): Double {
        if (track.isEmpty()) return 0.0
        val count = max(2, ceil(course.shapeLengthM / stepM).toInt() + 1)
        val samples = resample(course.shape, count)
        val covered = samples.count { pointToLineM(it, track) <= course.corridorM }
        return covered.toDouble() / samples.size
    }
}
