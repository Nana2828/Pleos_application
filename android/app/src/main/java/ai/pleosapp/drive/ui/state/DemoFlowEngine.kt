package ai.pleosapp.drive.ui.state

import ai.pleosapp.drive.core.course.DemoCourseRepository
import ai.pleosapp.drive.core.course.PresetCourse
import ai.pleosapp.drive.core.drive.ContractCodec
import ai.pleosapp.drive.core.drive.CourseEvent
import ai.pleosapp.drive.core.drive.CourseTracker
import ai.pleosapp.drive.core.drive.DriveOrigin
import ai.pleosapp.drive.core.drive.DriveResult
import ai.pleosapp.drive.core.drive.DriveSession
import ai.pleosapp.drive.core.drive.DriveSignal
import ai.pleosapp.drive.core.drive.Efficiency
import ai.pleosapp.drive.core.drive.EnergyType
import ai.pleosapp.drive.core.drive.GeoPoint
import ai.pleosapp.drive.core.drive.ReplayPlayer
import ai.pleosapp.drive.core.drive.ReplayScript
import ai.pleosapp.drive.core.drive.distanceM
import ai.pleosapp.drive.core.drive.thin
import ai.pleosapp.drive.core.navi.DemoRoute
import ai.pleosapp.drive.core.navi.GuidanceSnapshot
import ai.pleosapp.drive.core.navi.GuidanceTracker
import ai.pleosapp.drive.core.navi.ManeuverKind
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 시연 흐름의 화면 단계. E의 화면 순서와 같다. */
enum class DemoStep {
    /** 1·2. 지역 입력 + 코스 선택 (한 화면) */
    REGION,

    /** 2-1. 코스 길이 선택 */
    LENGTH,

    /** 3. 선택 확인 */
    CONFIRM,

    /** 4. 시작점까지 안내 */
    APPROACH,

    /** 5. 주행 기록 중 */
    DRIVING,

    /** 6. 도착 결과 + QR */
    RESULT,
}

/** 시연 조작 상태 (화면 구석의 숨김 조작이나 디버그 화면용) */
data class DemoInfo(
    /** 지금 재생 배속 (단계 기본 배속 × [boost]) */
    val speed: Double = 1.0,
    /** 발표자가 바꾼 추가 배속. 1이면 기본 */
    val boost: Double = 1.0,
    val playing: Boolean = false,
    /** 재생 파일 안 현재 시각(초) */
    val scriptSec: Long = 0,
    /** 재생 파일 전체 길이(초) */
    val scriptTotalSec: Long = 0,
    /** 코스 진행률 0~1 (진입 전 0) */
    val courseProgress: Double = 0.0,
    /** 결과 화면에서 서버로 보낼 규격 ③ drive-result JSON (시연: 보내지 않고 보관만) */
    val driveResultJson: String? = null,
    /** 합성 데이터임을 화면 구석에 표시할 때 */
    val synthetic: Boolean = true,
)

/** 화면 전부의 상태. 화면은 [step]에 맞는 UiState 하나만 그리면 된다. */
data class DemoFlowState(
    val step: DemoStep = DemoStep.REGION,
    val region: RegionUiState = RegionUiState(),
    val courses: CoursesUiState = CoursesUiState(),
    val length: CourseLengthUiState = CourseLengthUiState(),
    val confirm: ConfirmUiState = ConfirmUiState(),
    val approach: ApproachUiState = ApproachUiState(),
    val driving: DrivingUiState = DrivingUiState(),
    val result: ResultUiState = ResultUiState(),
    val demo: DemoInfo = DemoInfo(),
)

/** 시연 설정. 💡 값은 발표 리허설 후 조정 */
data class DemoConfig(
    /** 시작점 안내 구간 배속. 약 7분 → 약 14초 */
    val approachSpeed: Double = 30.0,
    /** 코스 주행 배속. 약 91분 → 약 1분 */
    val drivingSpeed: Double = 90.0,
    /** 결과 QR에 넣을 주소. ⬜ 시연용 폰 웹 주소가 정해지면 바꾼다 */
    val resultUrl: String = "https://pleos-demo.example/r/demo-001",
    val appVersion: String = "0.1.0-demo",
    /** 시작점까지 이만큼 남으면 "도착"으로 표시 */
    val arrivedAtStartM: Double = 40.0,
    /** 지도에 그릴 달린 길의 점 간격(m). 화면 갱신을 가볍게 */
    val pathSpacingM: Double = 25.0,
    /** "도착 직전으로" 조작이 건너뛸 곳 (코스 진행률) */
    val nearFinishProgress: Double = 0.95,
)

/**
 * 1차 제출 목업 시연의 흐름. 정해진 선택지(경기도/부천 → A 고양이 → 긴 코스)를 고르면
 * 가짜 주행(assets/demo/demo-drive.jsonl)을 배속 재생하며 시작점 안내 → 코스 진입 시 기록 → 완주 시 결과로 간다.
 *
 * - Android에 의존하지 않는다. 시간은 [tick]으로 받는다(실제로 흐른 ms). ViewModel이 타이머로 부른다.
 * - 진입·완주 판정은 주행 코어(`CourseTracker`), 회전 안내는 OSRM 미리 받은 경로(`GuidanceTracker`)를 쓴다.
 * - 결과는 접근 구간을 뺀 코스 구간만 계산한다.
 * - 시계: 안내를 시작한 순간의 [wallClock]을 재생 파일 0초에 맞춘다. 그래서 결과의 시작·끝 시각과 도착 예정 시각이
 *   실제 주행처럼 보인다(91분 코스면 91분 차이).
 */
class DemoFlowEngine(
    private val repository: DemoCourseRepository,
    private val route: DemoRoute,
    private val replay: ReplayScript,
    private val config: DemoConfig = DemoConfig(),
    private val wallClock: () -> Long = System::currentTimeMillis,
) : RegionActions, CoursesActions, CourseLengthActions, ConfirmActions, ApproachActions, DrivingActions, ResultActions {

    var state: DemoFlowState = DemoFlowState()
        private set

    /** 상태가 바뀔 때마다 부른다. */
    var listener: ((DemoFlowState) -> Unit)? = null

    private val energyType: EnergyType = replay.energyType ?: EnergyType.EV
    private val demoCourse: PresetCourse = repository.demoCourse
    private val courseRoad: List<GeoPoint> = route.course.geometry
    private val approachRoad: List<GeoPoint> = route.approach.geometry

    private var drive: DriveRun? = null
    private var regionId: String? = null
    private var boost = 1.0

    init {
        state = state.copy(demo = state.demo.copy(scriptTotalSec = durationSec(), synthetic = replay.synthetic))
    }

    // ---------- 1·2. 지역 + 코스 ----------

    override fun onQueryChange(query: String) {
        val regions = repository.searchRegions(query)
        val first = regions.firstOrNull()
        regionId = first?.id
        update {
            it.copy(
                region = it.region.copy(
                    query = query,
                    suggestions = regions.map { r -> RegionSuggestion(r.id, r.name, r.detail) },
                    errorMessage = null,
                ),
                // E 화면은 지역 입력과 코스 목록이 한 화면이라, 맞는 지역이 있으면 바로 목록을 채운다.
                courses = coursesFor(first?.id, first?.name),
            )
        }
    }

    override fun onClearQuery() = onQueryChange("")

    override fun onRegionSelected(regionId: String) {
        val region = repository.regions.firstOrNull { it.id == regionId } ?: return
        this.regionId = region.id
        update { it.copy(courses = coursesFor(region.id, region.name)) }
    }

    override fun onCoursePreview(courseId: String) {
        val course = state.courses.courses.firstOrNull { it.id == courseId } ?: return
        update {
            it.copy(
                courses = if (course.enabled) {
                    it.courses.copy(selectedCourseId = courseId, errorMessage = null)
                } else {
                    it.courses.copy(errorMessage = NOT_READY)
                },
            )
        }
    }

    override fun onCourseChosen(courseId: String) {
        val course = state.courses.courses.firstOrNull { it.id == courseId } ?: return
        if (!course.enabled) {
            update { it.copy(courses = it.courses.copy(errorMessage = NOT_READY)) }
            return
        }
        val presets = repository.lengthOptions(courseId)
        val options = presets.map { p ->
            val long = p.length == ai.pleosapp.drive.core.course.CourseLength.LONG
            CourseLengthOption(
                id = p.id,
                title = if (long) "긴 코스" else "짧은 코스",
                description = when {
                    !p.enabled -> NOT_READY
                    long -> "${shortName(p)} 모양을 크게 그려요"
                    else -> "가볍게 한 바퀴"
                },
                course = summary(p),
                recommended = p.enabled && long,
            )
        }
        update {
            it.copy(
                step = DemoStep.LENGTH,
                courses = it.courses.copy(selectedCourseId = courseId, errorMessage = null),
                length = CourseLengthUiState(
                    course = course,
                    options = options,
                    selectedId = options.lastOrNull { o -> o.course.enabled }?.id,
                ),
            )
        }
    }

    // ---------- 2-1. 길이 ----------

    override fun onLengthSelected(optionId: String) {
        val option = state.length.options.firstOrNull { it.id == optionId } ?: return
        if (!option.course.enabled) return
        update { it.copy(length = it.length.copy(selectedId = optionId)) }
    }

    override fun onLengthConfirmed() {
        val option = state.length.selected ?: return
        if (!option.course.enabled) return
        update {
            it.copy(
                step = DemoStep.CONFIRM,
                confirm = ConfirmUiState(
                    regionName = it.courses.regionName,
                    course = option.course,
                    canStartGuidance = option.course.id == demoCourse.id,
                    errorMessage = if (option.course.id == demoCourse.id) null else NOT_READY,
                ),
            )
        }
    }

    override fun onLengthBack() = update { it.copy(step = DemoStep.REGION) }

    // ---------- 3. 확인 ----------

    override fun onCancel() = update { it.copy(step = DemoStep.LENGTH) }

    override fun onStartGuidance() {
        if (!state.confirm.canStartGuidance) return
        val run = DriveRun(wallAnchorMs = wallClock())
        drive = run
        val summary = summary(demoCourse)
        val approachTotal = route.approach
        update {
            it.copy(
                step = DemoStep.APPROACH,
                approach = ApproachUiState(
                    course = summary,
                    startPoint = demoCourse.start.toMap(),
                    currentPosition = approachRoad.firstOrNull()?.toMap(),
                    distanceToStartKm = approachTotal.distanceM / 1000,
                    minutesToStart = ceilMinutes(approachTotal.durationSec),
                    guidance = GuidanceStatus.GUIDING,
                    showRecordingHint = true,
                    routeToStart = approachRoad.map { p -> p.toMap() },
                ),
                driving = DrivingUiState(courseName = summary.name, courseShape = courseRoad.map { p -> p.toMap() }),
                result = ResultUiState(),
                demo = it.demo.copy(playing = true, driveResultJson = null, courseProgress = 0.0),
            )
        }
        applySpeed()
    }

    // ---------- 4. 시작점 안내 ----------

    override fun onDismissRecordingHint() = update { it.copy(approach = it.approach.copy(showRecordingHint = false)) }

    override fun onCancelGuidance() {
        drive = null
        update { it.copy(step = DemoStep.CONFIRM, demo = it.demo.copy(playing = false)) }
    }

    // ---------- 5. 주행 ----------

    override fun onEndGuidance() = restart()

    // ---------- 6. 결과 ----------

    override fun onRetryUpload() = update { it.copy(result = it.result.copy(upload = UploadStatus.DONE, resultUrl = config.resultUrl)) }

    override fun onDone() = restart()

    // ---------- 시연 조작 ----------

    /** 실제로 흐른 시간만큼 재생한다. ViewModel 타이머가 부른다. */
    fun tick(realElapsedMs: Long) {
        val run = drive ?: return
        if (!state.demo.playing) return
        run.player.advanceBy(realElapsedMs, ::handle)
        finishIfReplayEnded(run)
        afterSignals()
    }

    /**
     * 재생 파일이 끝났는데 완주 판정이 안 났으면(도착 반경 밖으로 지나간 경우 등) 그 자리에서 결과를 만든다.
     * 시연 중 주행 화면에서 멈춰 버리지 않게 하기 위한 안전장치.
     */
    private fun finishIfReplayEnded(run: DriveRun) {
        if (!run.player.isFinished || run.finished || run.enteredAtMs == null) return
        finishCourse(run, run.player.positionMs)
    }

    fun setPlaying(playing: Boolean) {
        if (drive == null) return
        update { it.copy(demo = it.demo.copy(playing = playing)) }
    }

    /** 발표자 추가 배속 (예: 0.5, 1, 2, 4) */
    fun setBoost(value: Double) {
        require(value > 0) { "배속은 0보다 커야 합니다" }
        boost = value
        applySpeed()
    }

    /** 시작점 안내를 건너뛰고 코스 진입 순간까지 간다. */
    fun skipToCourseStart() {
        val run = drive ?: return
        if (state.step != DemoStep.APPROACH) return
        run.player.seekUntil(::handle) { state.step != DemoStep.APPROACH }
        afterSignals()
    }

    /** 코스 끝부분(진행률 [DemoConfig.nearFinishProgress])까지 건너뛴다. */
    fun skipToNearFinish() {
        val run = drive ?: return
        if (state.step != DemoStep.APPROACH && state.step != DemoStep.DRIVING) return
        run.player.seekUntil(::handle) {
            state.step == DemoStep.RESULT ||
                (state.step == DemoStep.DRIVING && run.tracker.progressRatio >= config.nearFinishProgress)
        }
        finishIfReplayEnded(run)
        afterSignals()
    }

    /** 처음(지역 입력)으로 */
    fun restart() {
        drive = null
        regionId = null
        boost = 1.0
        state = DemoFlowState(demo = DemoInfo(scriptTotalSec = durationSec(), synthetic = replay.synthetic))
        listener?.invoke(state)
    }

    // ---------- 신호 처리 ----------

    /** 주행 한 번에 필요한 것들. 처음부터 하면 새로 만든다. */
    private inner class DriveRun(val wallAnchorMs: Long) {
        val player = ReplayPlayer(replay.signals)
        val session = DriveSession(DriveOrigin(DriveOrigin.Kind.REPLAY, replay.name, replay.synthetic))
        val tracker = CourseTracker(demoCourse.toCourse(trackShape = courseRoad))
        val approachGuide = GuidanceTracker(route.approach)
        val courseGuide = GuidanceTracker(route.course)
        val scriptAnchorMs = player.startMs

        var position: GeoPoint? = null
        var speedKph: Double? = null
        var latest = Efficiency.Snapshot.EMPTY
        var guide: GuidanceSnapshot? = null

        // 코스 구간 기록
        var enteredAtMs: Long? = null
        var enteredDistanceM = 0.0
        var enteredSnapshot = Efficiency.Snapshot.EMPTY
        var courseMaxKph = 0.0
        var courseMovingMs = 0L
        var lastSpeedMs: Long? = null
        val coursePath = ArrayList<GeoPoint>()
        val drawnPath = ArrayList<GeoPoint>()
        /** 화면에 넘길 달린 길. 점이 늘었을 때만 새로 만든다(틱마다 복사하지 않게) */
        var drawnMap: List<MapPoint> = emptyList()
            get() {
                if (field.size != drawnPath.size) field = drawnPath.map { it.toMap() }
                return field
            }
        var finished = false

        fun wallAt(scriptMs: Long): Long = wallAnchorMs + (scriptMs - scriptAnchorMs)
    }

    private fun handle(signal: DriveSignal) {
        val run = drive ?: return
        if (run.finished) return
        run.session.handle(signal)
        when (signal) {
            is DriveSignal.Speed -> {
                val prev = run.lastSpeedMs
                if (run.enteredAtMs != null) {
                    run.courseMaxKph = maxOf(run.courseMaxKph, signal.kph)
                    if (prev != null && signal.kph > MOVING_KPH) run.courseMovingMs += signal.timeMs - prev
                }
                run.lastSpeedMs = signal.timeMs
                run.speedKph = signal.kph
            }
            is DriveSignal.Odometer -> run.latest = run.latest.copy(odometerKm = signal.km)
            is DriveSignal.Battery -> run.latest = run.latest.copy(batteryWh = signal.wh)
            is DriveSignal.Fuel -> run.latest = run.latest.copy(fuelMl = signal.ml)
            is DriveSignal.Position -> handlePosition(run, signal)
            else -> Unit
        }
    }

    private fun handlePosition(run: DriveRun, signal: DriveSignal.Position) {
        val p = signal.point
        run.position = p
        val event = run.tracker.update(signal.timeMs, p)
        if (run.enteredAtMs == null) {
            run.guide = run.approachGuide.update(p)
            if (event is CourseEvent.Entered) enterCourse(run, event)
        } else {
            run.guide = run.courseGuide.update(p)
            run.coursePath += p
            if (run.drawnPath.isEmpty() || distanceM(run.drawnPath.last(), p) >= config.pathSpacingM) run.drawnPath += p
            if (event is CourseEvent.Finished) finishCourse(run, signal.timeMs)
        }
    }

    private fun enterCourse(run: DriveRun, event: CourseEvent.Entered) {
        run.enteredAtMs = event.timeMs
        run.enteredDistanceM = run.session.liveDistanceM
        run.enteredSnapshot = run.latest
        run.position?.let {
            run.coursePath += it
            run.drawnPath += it
            // 진입한 신호에서 바로 코스 안내로 바꾼다. 안 그러면 한 프레임 동안 접근 구간 값(남은 0km)이 보인다.
            run.guide = run.courseGuide.update(it)
        }
        state = state.copy(step = DemoStep.DRIVING)
        applySpeed(notify = false)
    }

    private fun finishCourse(run: DriveRun, timeMs: Long) {
        run.finished = true
        val start = run.enteredAtMs ?: timeMs
        val distanceKm = (run.session.liveDistanceM - run.enteredDistanceM) / 1000
        val efficiency = Efficiency.between(run.enteredSnapshot, run.latest, electric = energyType == EnergyType.EV)
        val result = DriveResult(
            courseId = demoCourse.id,
            startedAt = Instant.ofEpochMilli(run.wallAt(start)).atOffset(DriveResult.KST),
            endedAt = Instant.ofEpochMilli(run.wallAt(timeMs)).atOffset(DriveResult.KST),
            durationSec = (timeMs - start) / 1000,
            movingSec = run.courseMovingMs / 1000,
            distanceKm = distanceKm,
            energyType = energyType,
            avgFuelEconomy = if (energyType == EnergyType.FUEL) efficiency?.value else null,
            avgEvEfficiency = if (energyType == EnergyType.EV) efficiency?.value else null,
            fuelEconomySource = efficiency?.let { Efficiency.Source.ESTIMATED },
            maxKph = run.courseMaxKph,
            onCourseRatio = run.tracker.onCourseRatio,
            polyline = thin(run.coursePath, DriveResult.POLYLINE_SPACING_M),
            recordSource = DriveOrigin.Kind.REPLAY,
            synthetic = replay.synthetic,
            appVersion = config.appVersion,
        )
        val json = ContractCodec.encodeDriveResult(result, prettyPrint = true)
        state = state.copy(
            step = DemoStep.RESULT,
            result = ResultUiState(
                courseName = demoCourse.name,
                regionName = demoCourse.place,
                distanceKm = distanceKm,
                durationSec = result.durationSec,
                efficiency = efficiency?.let {
                    Efficiency(
                        value = it.value,
                        unit = if (it.unit == ai.pleosapp.drive.core.drive.Efficiency.Unit.KM_PER_KWH) EfficiencyUnit.KM_PER_KWH else EfficiencyUnit.KM_PER_L,
                        source = EfficiencySource.CALCULATED,
                    )
                },
                startedAtEpochMs = run.wallAt(start),
                endedAtEpochMs = run.wallAt(timeMs),
                drivenPath = run.drawnMap + listOfNotNull(run.position?.toMap()),
                courseShape = courseRoad.map { it.toMap() },
                // 시연: 서버로 보내지 않고 성공 처리, QR은 고정 주소
                resultUrl = config.resultUrl,
                upload = UploadStatus.DONE,
            ),
            demo = state.demo.copy(playing = false, driveResultJson = json, courseProgress = 1.0),
        )
    }

    /** 신호를 처리한 뒤 화면 값을 한 번에 갱신한다. */
    private fun afterSignals() {
        val run = drive ?: return
        val s = state
        val guide = run.guide
        val scriptMs = run.player.positionMs
        val next = when (s.step) {
            DemoStep.APPROACH -> s.copy(
                approach = s.approach.copy(
                    currentPosition = run.position?.toMap() ?: s.approach.currentPosition,
                    distanceToStartKm = guide?.remainingM?.div(1000) ?: s.approach.distanceToStartKm,
                    minutesToStart = guide?.remainingSec?.let(::ceilMinutes) ?: s.approach.minutesToStart,
                    guidance = if ((guide?.remainingM ?: Double.MAX_VALUE) <= config.arrivedAtStartM) GuidanceStatus.ARRIVED else GuidanceStatus.GUIDING,
                    nextTurn = guide?.let(::turnOf),
                ),
            )
            DemoStep.DRIVING -> {
                val entered = run.enteredAtMs ?: scriptMs
                s.copy(
                    driving = s.driving.copy(
                        isRecording = true,
                        nextTurn = guide?.let(::turnOf),
                        arrivalAtEpochMs = guide?.let { run.wallAt(scriptMs) + it.remainingSec * 1000 },
                        remainingKm = guide?.remainingM?.div(1000),
                        remainingMinutes = guide?.remainingSec?.let(::ceilMinutes),
                        drivenKm = (run.session.liveDistanceM - run.enteredDistanceM) / 1000,
                        elapsedSec = ((scriptMs - entered) / 1000).coerceAtLeast(0),
                        drivenPath = run.drawnMap,
                        currentPosition = run.position?.toMap(),
                        speedKph = run.speedKph,
                    ),
                )
            }
            else -> s
        }
        state = next.copy(
            demo = next.demo.copy(
                scriptSec = (scriptMs - run.scriptAnchorMs) / 1000,
                courseProgress = when {
                    next.step == DemoStep.RESULT -> 1.0
                    run.enteredAtMs == null -> 0.0
                    else -> run.tracker.progressRatio
                },
                playing = next.demo.playing && !run.player.isFinished && next.step != DemoStep.RESULT,
            ),
        )
        listener?.invoke(state)
    }

    private fun applySpeed(notify: Boolean = true) {
        val run = drive
        val base = if (state.step == DemoStep.DRIVING) config.drivingSpeed else config.approachSpeed
        val speed = base * boost
        run?.player?.speed = speed
        state = state.copy(demo = state.demo.copy(speed = speed, boost = boost))
        if (notify) listener?.invoke(state)
    }

    private inline fun update(change: (DemoFlowState) -> DemoFlowState) {
        state = change(state)
        listener?.invoke(state)
    }

    // ---------- 바꾸기 ----------

    private fun coursesFor(regionId: String?, regionName: String?): CoursesUiState {
        if (regionId == null) return CoursesUiState()
        val list = repository.coursesIn(regionId).map(::summary)
        return CoursesUiState(regionName = regionName.orEmpty(), courses = list, selectedCourseId = null)
    }

    private fun summary(p: PresetCourse) = CourseSummary(
        id = p.id,
        label = p.label,
        name = shortName(p) + " 코스",
        distanceKm = p.distanceKm,
        estimatedMinutes = p.durationMin,
        shape = p.preview.map { it.toMap() },
        enabled = p.enabled,
    )

    /** "부천·인천 고양이 코스" → "고양이" */
    private fun shortName(p: PresetCourse): String =
        p.name.removeSuffix(" 코스").substringAfterLast(' ').ifBlank { p.name }

    private fun turnOf(g: GuidanceSnapshot): TurnInfo? {
        val m = g.next ?: return null
        val distance = g.distanceToNextM ?: return null
        val dir = when (m.kind) {
            ManeuverKind.LEFT, ManeuverKind.SLIGHT_LEFT, ManeuverKind.SHARP_LEFT -> TurnDirection.LEFT
            ManeuverKind.RIGHT, ManeuverKind.SLIGHT_RIGHT, ManeuverKind.SHARP_RIGHT -> TurnDirection.RIGHT
            ManeuverKind.U_TURN -> TurnDirection.U_TURN
            ManeuverKind.STRAIGHT, ManeuverKind.ROUNDABOUT, ManeuverKind.ARRIVE -> TurnDirection.STRAIGHT
        }
        return TurnInfo(direction = dir, distanceM = distance.roundToInt().coerceAtLeast(0), text = m.text, roadName = m.roadName)
    }

    private fun durationSec(): Long = (replay.signals.lastOrNull()?.timeMs ?: 0L) / 1000

    private companion object {
        const val NOT_READY = "준비 중인 코스예요"
        const val MOVING_KPH = 3.0

        fun GeoPoint.toMap() = MapPoint(lat, lon)

        fun ceilMinutes(sec: Long): Int = ceil(sec / 60.0).toInt()
    }
}
