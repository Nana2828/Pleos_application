package ai.pleosapp.drive.core.drive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * 재생 파일(규격 ② drive-replay)의 신호를 배속으로 내보낸다. 시연·가상주행 모드용.
 *
 * 시계를 직접 갖지 않는다. 부르는 쪽이 "실제로 흐른 시간"을 [advanceBy]로 알려 주면
 * 그만큼 × [speed]의 재생 시간까지 신호를 순서대로 내보낸다. 그래서 스레드 없이 시험할 수 있다.
 * 실제 타이머로 돌릴 때는 [ReplaySignalSource]를 쓴다.
 *
 * 건너뛰기([seekTo])도 건너뛴 신호를 모두 순서대로 내보낸다. 주행 기록·코스 판정이 중간 값을 놓치지 않게 하기 위해서다.
 * 한 스레드에서만 호출한다.
 */
class ReplayPlayer(signals: List<DriveSignal>) {
    private val signals: List<DriveSignal> = signals.sortedBy { it.timeMs }
    private var index = 0

    /** 재생 파일 안에서의 현재 위치(ms, 파일 시간 기준). */
    var positionMs: Long = this.signals.firstOrNull()?.timeMs ?: 0L
        private set

    /** 재생 배속. 1이면 실제 시간 그대로. */
    var speed: Double = 1.0
        set(value) {
            require(value > 0 && value.isFinite()) { "배속은 0보다 커야 합니다: $value" }
            field = value
        }

    val startMs: Long = this.signals.firstOrNull()?.timeMs ?: 0L
    val endMs: Long = this.signals.lastOrNull()?.timeMs ?: 0L
    val durationMs: Long get() = endMs - startMs

    /** 모든 신호를 내보냈으면 true. */
    val isFinished: Boolean get() = index >= signals.size

    /** 아직 안 보낸 첫 신호. 다음에 무엇이 올지 볼 때 쓴다. */
    fun peek(): DriveSignal? = signals.getOrNull(index)

    /** 실제로 [realElapsedMs]만큼 흘렀을 때 재생 시간을 그만큼 × 배속 진행하고 그 사이 신호를 내보낸다. */
    fun advanceBy(realElapsedMs: Long, sink: (DriveSignal) -> Unit): Int = advanceBy(realElapsedMs, NEVER, sink)

    /**
     * [advanceBy]와 같지만 [stopAfter]가 true가 되면 그 신호에서 멈춘다(재생 위치도 그 신호 시각).
     * 들를 곳 이탈 지점에서 코스 재생을 멈출 때 쓴다(PLEOS-63).
     */
    fun advanceBy(realElapsedMs: Long, stopAfter: () -> Boolean, sink: (DriveSignal) -> Unit): Int {
        if (realElapsedMs <= 0 || isFinished) return 0
        return seekTo(positionMs + (realElapsedMs * speed).roundToLong(), stopAfter, sink)
    }

    /** 파일 시간 [targetMs]까지 신호를 모두 내보낸다. 뒤로는 가지 않는다(처음부터는 [reset]). 보낸 신호 수를 돌려준다. */
    fun seekTo(targetMs: Long, sink: (DriveSignal) -> Unit): Int = seekTo(targetMs, NEVER, sink)

    /** [seekTo]와 같지만 [stopAfter]가 true가 되면 그 신호까지만 보내고 멈춘다. 다음 호출은 그다음 신호부터 이어 간다. */
    fun seekTo(targetMs: Long, stopAfter: () -> Boolean, sink: (DriveSignal) -> Unit): Int {
        var sent = 0
        while (index < signals.size && signals[index].timeMs <= targetMs) {
            val s = signals[index++]
            sink(s)
            sent++
            if (stopAfter()) {
                positionMs = maxOf(positionMs, s.timeMs)
                return sent
            }
        }
        positionMs = maxOf(positionMs, minOf(targetMs, endMs))
        return sent
    }

    /** 조건을 처음 만족하는 신호를 내보낸 곳까지 건너뛴다. 못 찾으면 끝까지 간다. "도착 직전으로" 같은 조작용. */
    fun seekUntil(sink: (DriveSignal) -> Unit, stop: (DriveSignal) -> Boolean): Boolean {
        while (index < signals.size) {
            val s = signals[index++]
            positionMs = maxOf(positionMs, s.timeMs)
            sink(s)
            if (stop(s)) return true
        }
        return false
    }

    /** 처음으로 되돌린다. 받는 쪽(주행 기록 등)도 새로 만들어야 한다. */
    fun reset() {
        index = 0
        positionMs = startMs
    }

    private companion object {
        val NEVER: () -> Boolean = { false }
    }
}

/**
 * [ReplayPlayer]를 실제 타이머로 돌리는 신호 소스. [tickMs]마다 흐른 시간만큼 진행한다.
 * 신호는 [scope]의 디스패처(보통 Main)에서 [start]에 넘긴 sink로 들어온다.
 */
class ReplaySignalSource(
    val player: ReplayPlayer,
    private val scope: CoroutineScope,
    private val tickMs: Long = 100,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    /** 끝까지 재생하면 한 번 부른다. */
    private val onFinished: () -> Unit = {},
) : DriveSignalSource {
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    override fun start(sink: (DriveSignal) -> Unit) {
        if (isRunning) return
        job = scope.launch {
            var last = nowMs()
            while (isActive && !player.isFinished) {
                delay(tickMs)
                val now = nowMs()
                player.advanceBy(now - last, sink)
                last = now
            }
            if (player.isFinished) onFinished()
        }
    }

    /** 멈춘다. 다시 [start]하면 멈춘 곳부터 이어 간다. */
    override fun stop() {
        job?.cancel()
        job = null
    }
}
