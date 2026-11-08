package ai.pleosapp.core

import kotlin.math.max

data class TrackPoint(val timeMs: Long, val point: GeoPoint)

/** 저장되는 주행 한 건. 거리에는 GPS가 끊긴 구간의 속도 적분 거리(gapDistanceM)가 포함된다. */
data class DriveRecord(
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val gapDistanceM: Double,
    val maxKph: Double,
    val movingMs: Long,
    val segments: List<List<TrackPoint>>,
    val origin: DriveOrigin,
    /** 앱이 비정상 종료되어 일지에서 복구한 주행이면 true. 끝 시각은 마지막 신호 기준이다. */
    val recovered: Boolean = false,
    /** 기록 시작·끝 순간의 누적 주행거리·남은 에너지. 효율 계산(`Efficiency.between`)에 쓴다. */
    val startSnapshot: Efficiency.Snapshot = Efficiency.Snapshot.EMPTY,
    val endSnapshot: Efficiency.Snapshot = Efficiency.Snapshot.EMPTY,
    /** 기록 중 차가 마지막으로 준 전비(km/kWh)·연비(L/100km). 없으면 null. */
    val vehicleKmPerKwh: Double? = null,
    val vehicleLitersPer100Km: Double? = null,
) {
    val id: String get() = "drive-$startMs"
    val durationMs: Long get() = endMs - startMs

    /** 구간을 이어 붙인 궤적. 코스 일치율 계산용. */
    val points: List<TrackPoint> get() = segments.flatten()
}

/** 💡 제안 값이다. 실차 주행으로 검증하지 않았다. */
data class DriveLimits(
    /** 이보다 짧은 주행은 주차장 이동 등으로 보고 저장하지 않는다. */
    val minDistanceM: Double = 300.0,
    val minDurationMs: Long = 60_000,
    /** 위치 정확도가 이보다 나쁘면 쓰지 않는다. */
    val maxAccuracyM: Double = 50.0,
    /** 이 속도(약 300km/h)를 넘겨야 도달하는 점은 튄 값으로 본다. */
    val maxPlausibleMps: Double = 83.0,
    /** 이보다 오래 위치가 끊기면 선을 잇지 않고 새 구간으로 시작한다. */
    val gapMs: Long = 30_000,
    /** 정차 중 흔들림이 거리에 쌓이지 않도록 이보다 가까운 점은 건너뛴다. */
    val minStepM: Double = 5.0,
    /** 출발 직전의 위치를 첫 점으로 쓸 수 있는 시간. */
    val seedMaxAgeMs: Long = 60_000,
    /** 속도 신호가 이보다 오래 끊기면 그 사이는 적분하지 않는다. */
    val speedStaleMs: Long = 10_000,
    /** 이 속도를 넘으면 움직이는 시간으로 센다. */
    val movingKph: Double = 3.0,
)

sealed interface SessionEvent {
    data class Started(val timeMs: Long) : SessionEvent
    data class Finished(val record: DriveRecord) : SessionEvent
    data class Discarded(val distanceM: Double, val durationMs: Long) : SessionEvent
}

/**
 * 기어 신호로 주행의 시작과 끝을 정한다. D에서 시작하고 P에서 끝난다.
 * 주행 중 R과 N은 끝으로 보지 않는다. 주차 중 후진까지 한 주행에 담기 위해서다.
 * 신호 순서대로 한 스레드에서만 호출한다.
 */
class DriveSession(
    private val origin: DriveOrigin,
    private val limits: DriveLimits = DriveLimits(),
) {
    private var startMs: Long? = null
    private val segments = ArrayList<ArrayList<TrackPoint>>()
    private var gpsDistanceM = 0.0
    private var gapDistanceM = 0.0
    private var maxKph = 0.0
    private var movingMs = 0L

    private var lastFixMs: Long? = null
    private var speedSinceFixM = 0.0
    private var lastSpeedKph: Double? = null
    private var lastSpeedMs = 0L
    private var lastIdlePosition: DriveSignal.Position? = null
    private var lastSignalMs: Long? = null

    // 차량 값은 기록 여부와 상관없이 최신 값을 들고 있다가 시작·끝 순간에 찍는다.
    private var latest = Efficiency.Snapshot.EMPTY
    private var startSnapshot = Efficiency.Snapshot.EMPTY
    private var vehicleKmPerKwh: Double? = null
    private var vehicleLitersPer100Km: Double? = null

    val isRecording: Boolean get() = startMs != null
    val liveDistanceM: Double get() = gpsDistanceM + gapDistanceM
    val liveStartMs: Long? get() = startMs

    fun handle(signal: DriveSignal): SessionEvent? {
        lastSignalMs = max(lastSignalMs ?: signal.timeMs, signal.timeMs)
        when (signal) {
            is DriveSignal.GearChange -> return handleGear(signal)
            is DriveSignal.Speed -> handleSpeed(signal)
            is DriveSignal.Position -> handlePosition(signal)
            is DriveSignal.Odometer -> if (signal.km.isFinite()) latest = latest.copy(odometerKm = signal.km)
            is DriveSignal.Battery -> if (signal.wh.isFinite()) latest = latest.copy(batteryWh = signal.wh)
            is DriveSignal.Fuel -> if (signal.ml.isFinite()) latest = latest.copy(fuelMl = signal.ml)
            is DriveSignal.EvEfficiency -> if (isRecording) vehicleKmPerKwh = signal.kmPerKwh
            is DriveSignal.FuelEconomy -> if (isRecording) vehicleLitersPer100Km = signal.litersPer100Km
        }
        return null
    }

    /** 일지에서 복구할 때 마지막 신호 시각으로 주행을 닫는다. */
    fun closeAtLastSignal(): SessionEvent? {
        val end = lastSignalMs ?: return null
        return if (isRecording) finish(end, recovered = true) else null
    }

    private fun handleGear(signal: DriveSignal.GearChange): SessionEvent? = when {
        signal.gear == Gear.DRIVE && !isRecording -> begin(signal.timeMs)
        signal.gear == Gear.PARK && isRecording -> finish(signal.timeMs, recovered = false)
        else -> null
    }

    private fun begin(time: Long): SessionEvent {
        startMs = time
        segments.clear()
        segments.add(ArrayList())
        gpsDistanceM = 0.0
        gapDistanceM = 0.0
        maxKph = 0.0
        movingMs = 0L
        speedSinceFixM = 0.0
        lastFixMs = null
        startSnapshot = latest
        vehicleKmPerKwh = null
        vehicleLitersPer100Km = null
        val idleSpeed = lastSpeedKph
        lastSpeedMs = time
        lastSpeedKph = idleSpeed
        lastIdlePosition?.takeIf { time - it.timeMs <= limits.seedMaxAgeMs }?.let { seed ->
            segments.last().add(TrackPoint(time, seed.point))
            lastFixMs = time
        }
        return SessionEvent.Started(time)
    }

    private fun finish(end: Long, recovered: Boolean): SessionEvent {
        val start = checkNotNull(startMs)
        startMs = null
        val total = gpsDistanceM + gapDistanceM
        val duration = end - start
        if (total < limits.minDistanceM || duration < limits.minDurationMs) {
            return SessionEvent.Discarded(total, duration)
        }
        return SessionEvent.Finished(
            DriveRecord(
                startMs = start,
                endMs = end,
                distanceM = total,
                gapDistanceM = gapDistanceM,
                maxKph = maxKph,
                movingMs = movingMs,
                // 점이 하나뿐인 구간은 선을 그릴 수 없다.
                segments = segments.filter { it.size >= 2 }.map { it.toList() },
                origin = origin,
                recovered = recovered,
                startSnapshot = startSnapshot,
                endSnapshot = latest,
                vehicleKmPerKwh = vehicleKmPerKwh,
                vehicleLitersPer100Km = vehicleLitersPer100Km,
            ),
        )
    }

    private fun handleSpeed(signal: DriveSignal.Speed) {
        val kph = signal.kph.coerceAtLeast(0.0)
        if (isRecording) {
            val previous = lastSpeedKph
            val elapsed = signal.timeMs - lastSpeedMs
            if (previous != null && elapsed in 1..limits.speedStaleMs) {
                val average = (previous + kph) / 2
                speedSinceFixM += average / 3.6 * elapsed / 1000.0
                if (average > limits.movingKph) movingMs += elapsed
            }
            // 튄 값이 최고 속도로 남지 않게 한다.
            if (kph / 3.6 <= limits.maxPlausibleMps) maxKph = max(maxKph, kph)
        }
        lastSpeedKph = kph
        lastSpeedMs = signal.timeMs
    }

    /** 최근 속도 신호가 "멈춤"이면 true. 속도 신호가 없거나 오래됐으면 모른다고 보고 false. */
    private fun isStopped(time: Long): Boolean {
        val kph = lastSpeedKph ?: return false
        return kph < limits.movingKph && time - lastSpeedMs <= limits.speedStaleMs
    }

    private fun handlePosition(signal: DriveSignal.Position) {
        val accuracy = signal.accuracyM
        if (accuracy != null && accuracy > limits.maxAccuracyM) return
        if (!isRecording) {
            lastIdlePosition = signal
            return
        }
        val fixMs = lastFixMs
        if (fixMs != null && signal.timeMs <= fixMs) return

        val current = segments.last()
        val previous = current.lastOrNull()
        if (previous == null) {
            current.add(TrackPoint(signal.timeMs, signal.point))
            lastIdlePosition = signal
            lastFixMs = signal.timeMs
            speedSinceFixM = 0.0
            return
        }
        val step = distanceM(previous.point, signal.point)
        val elapsedS = (signal.timeMs - previous.timeMs) / 1000.0
        if (elapsedS <= 0 || step / elapsedS > limits.maxPlausibleMps) return

        val gap = fixMs != null && signal.timeMs - fixMs > limits.gapMs
        when {
            gap -> {
                // 끊긴 구간은 직선으로 잇지 않고, 속도 신호가 있으면 그 거리만 더한다.
                gapDistanceM += speedSinceFixM
                segments.add(arrayListOf(TrackPoint(signal.timeMs, signal.point)))
            }
            // 속도가 "멈춤"이면 GPS가 흔들려도 거리를 더하지 않는다(신호 대기 중 흔들림이 거리로 쌓이는 문제).
            step >= limits.minStepM && !isStopped(signal.timeMs) -> {
                gpsDistanceM += step
                current.add(TrackPoint(signal.timeMs, signal.point))
            }
        }
        lastIdlePosition = signal
        lastFixMs = signal.timeMs
        speedSinceFixM = 0.0
    }
}
