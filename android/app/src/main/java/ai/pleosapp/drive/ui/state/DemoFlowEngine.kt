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
import ai.pleosapp.drive.core.navi.CourseStop
import ai.pleosapp.drive.core.navi.DemoRoute
import ai.pleosapp.drive.core.navi.GuidanceSnapshot
import ai.pleosapp.drive.core.navi.GuidanceTracker
import ai.pleosapp.drive.core.navi.ManeuverKind
import ai.pleosapp.drive.core.navi.toSignals
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 시연 흐름의 화면 단계. E의 화면 순서와 같다. */
enum class DemoStep {
    /** 1·2. 지역 입력 + 코스 선택 (한 화면) */
    REGION,

    /** 2-2. 코스 미리보기 + "안내 시작" (PLEOS-62) */
    PREVIEW,

    /** 2-1. 코스 길이 선택. PLEOS-62부터 시연 흐름에서 빠졌다(짧은 코스가 생기면 다시 연결) */
    LENGTH,

    /** 3. 선택 확인. PLEOS-62부터 시연 흐름에서 빠졌다(미리보기가 대신함) */
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
    /** 재생 파일 전체 길이(초). 코스를 고르기 전에는 0 */
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
    val preview: PreviewUiState = PreviewUiState(),
    val length: CourseLengthUiState = CourseLengthUiState(),
    val confirm: ConfirmUiState = ConfirmUiState(),
    val approach: ApproachUiState = ApproachUiState(),
    val driving: DrivingUiState = DrivingUiState(),
    val result: ResultUiState = ResultUiState(),
    val demo: DemoInfo = DemoInfo(),
)

/** 시연 설정. 💡 값은 발표 리허설 후 조정 */
data class DemoConfig(
    /**
     * 시작점 안내 구간을 몇 초 동안 보여 줄지. 코스마다 가산에서 시작점까지 거리가 달라서(15~48km)
     * 배속을 코스마다 이 시간에 맞춰 계산한다.
     */
    val approachSeconds: Double = 20.0,
    /** 코스 주행을 몇 초 동안 보여 줄지. 배속은 코스마다 계산 (고양이 약 91분 → 약 1분) */
    val drivingSeconds: Double = 60.0,
    /** 배속 하한. 아주 짧은 구간이 너무 느리게 재생되지 않게 */
    val minSpeed: Double = 10.0,
    /** 결과 QR에 넣을 주소. ⬜ 시연용 폰 웹 주소가 정해지면 바꾼다 */
    /**
     * 결과 QR에 넣을 폰 웹 기본 주소 (PLEOS-64). 앱에서는 `local.properties`의 `pleos.webUrl`([WebBaseUrl]).
     * null이면 QR 대신 "웹 주소가 설정되지 않았어요"를 보여 준다.
     */
    val resultUrl: String? = null,
    val appVersion: String = "0.1.0-demo",
    /** 시작점까지 이만큼 남으면 "도착"으로 표시 */
    val arrivedAtStartM: Double = 40.0,
    /** 지도에 그릴 달린 길의 점 간격(m). 화면 갱신을 가볍게 */
    val pathSpacingM: Double = 25.0,
    /** "도착 직전으로" 조작이 건너뛸 곳 (코스 진행률) */
    val nearFinishProgress: Double = 0.95,
    /** 들를 곳 가는 길·돌아오는 길을 각각 몇 초 동안 보여 줄지 (PLEOS-63) */
    val detourLegSeconds: Double = 10.0,
    /** 이탈 지점이 이만큼 남으면 "곧 빠집니다"를 보여 준다 (PLEOS-63) */
    val stopNoticeM: Double = 2_000.0,
    /** 장소에 머문 시간으로 칠 값. 결과의 시작·끝 시각에만 들어가고 기록 시간에는 안 들어간다 (PLEOS-63) */
    val stopStayMinutes: Int = 30,
    /** 코스에 합류한 뒤 "기록 다시 시작"을 보여 줄 실제 시간(ms) (PLEOS-63) */
    val resumedNoticeMs: Long = 3_000,
    /** "곧 빠집니다"가 떠 있는 동안 주행 배속을 이만큼 나눈다. 안 그러면 1~2초 만에 지나가 읽을 수 없다 (PLEOS-63) */
    val noticeSlowdown: Double = 4.0,
)

/**
 * 진짜 시연 코스 하나의 재생 데이터 (assets/demo/<코스 id>.route.json, .drive.jsonl, .stops.json)
 * [stops]는 코스 주변 들를 곳(PLEOS-63). 파일이 없는 코스는 빈 목록.
 */
class DemoCourseData(val route: DemoRoute, val replay: ReplayScript, val stops: List<CourseStop> = emptyList())

/**
 * 1차 제출 목업 시연의 흐름 (PLEOS-60, PLEOS-62).
 * 지역(부천·화성·동두천·양주·경기도)을 입력해 진짜 코스를 고르면 미리보기를 보여 주고, "안내 시작"을 누르면
 * 그 코스의 가짜 주행(assets/demo/<코스 id>.drive.jsonl)을 배속 재생하며 시작점 안내 → 코스 진입 시 기록 → 완주 시 결과로 간다.
 *
 * - Android에 의존하지 않는다. 시간은 [tick]으로 받는다(실제로 흐른 ms). ViewModel이 타이머로 부른다.
 * - 진입·완주 판정은 주행 코어(`CourseTracker`), 회전 안내는 OSRM 미리 받은 경로(`GuidanceTracker`)를 쓴다.
 * - 결과는 접근 구간을 뺀 코스 구간만 계산한다.
 * - 들를 곳(PLEOS-63): 미리보기에서 고른 장소의 이탈 지점에 오면 코스 재생을 그 자리에서 멈추고, 장소 왕복 재생으로 바꾼다.
 *   왕복 신호는 주행 기록(`DriveSession`)·코스 판정(`CourseTracker`)에 넣지 않는다. 돌아오면 코스 재생을 멈춘 곳부터 이어 가므로
 *   거리·시간·효율·일치율에 왕복 구간이 들어가지 않고, 결과의 시작·끝 시각에만 왕복 + 머문 시간이 더해진다.
 * - 시계: 안내를 시작한 순간의 [wallClock]을 재생 파일 0초에 맞춘다. 그래서 결과의 시작·끝 시각과 도착 예정 시각이
 *   실제 주행처럼 보인다(91분 코스면 91분 차이).
 *
 * @param data 진짜 코스 id → 재생 데이터. 여기 없는 코스는 미리보기에서 안내를 시작할 수 없다.
 */
class DemoFlowEngine(
    private val repository: DemoCourseRepository,
    private val data: Map<String, DemoCourseData>,
    private val config: DemoConfig = DemoConfig(),
    private val wallClock: () -> Long = System::currentTimeMillis,
) : RegionActions, CoursesActions, PreviewActions, CourseLengthActions, ConfirmActions, ApproachActions, DrivingActions, ResultActions {

    var state: DemoFlowState = DemoFlowState()
        private set

    /** 상태가 바뀔 때마다 부른다. */
    var listener: ((DemoFlowState) -> Unit)? = null

    /** 미리보기에서 고른 코스. 안내를 시작하면 이 코스로 재생한다. */
    private class Selected(val course: PresetCourse, val data: DemoCourseData?)

    private var selected: Selected? = null
    private var drive: DriveRun? = null
    private var boost = 1.0

    // ---------- 1·2. 지역 + 코스 ----------

    override fun onQueryChange(query: String) {
        val regions = repository.searchRegions(query)
        val first = regions.firstOrNull()
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
        update { it.copy(courses = coursesFor(region.id, region.name)) }
    }

    /** 진짜 코스면 지도에 그 코스를 띄운다. 더미(준비 중)는 아무 일도 없다. */
    override fun onCoursePreview(courseId: String) {
        val course = state.courses.courses.firstOrNull { it.id == courseId } ?: return
        if (!course.enabled) return
        update { it.copy(courses = it.courses.copy(selectedCourseId = courseId, mapCourse = course, errorMessage = null)) }
    }

    /** 진짜 코스를 누르면 미리보기(안내 시작 버튼)로 간다. 더미(준비 중)는 아무 일도 없다. */
    override fun onCourseChosen(courseId: String) {
        val item = state.courses.courses.firstOrNull { it.id == courseId } ?: return
        if (!item.enabled) return
        val course = repository.course(courseId) ?: return
        val courseData = data[courseId]
        selected = Selected(course, courseData)
        val route = courseData?.route
        update {
            it.copy(
                step = DemoStep.PREVIEW,
                courses = it.courses.copy(selectedCourseId = courseId, errorMessage = null),
                preview = PreviewUiState(
                    course = item,
                    courseRoute = route?.course?.geometry?.map { p -> p.toMap() } ?: item.shape,
                    startPoint = course.start.toMap(),
                    place = course.place,
                    distanceToStartKm = route?.approach?.distanceM?.div(1000),
                    minutesToStart = route?.approach?.durationSec?.let(::ceilMinutes),
                    canStartGuidance = courseData != null,
                    stops = courseData?.stops.orEmpty().map(::stopOption),
                ),
                // 미리보기에서 길이 선택을 건너뛰지만, 다른 화면이 같은 값을 쓸 수 있게 채워 둔다
                length = CourseLengthUiState(
                    course = item,
                    options = listOf(CourseLengthOption(item.id, "긴 코스", "${shortName(course)} 모양을 크게 그려요", item, recommended = true)),
                    selectedId = item.id,
                ),
                confirm = ConfirmUiState(
                    regionName = it.courses.regionName,
                    course = item,
                    canStartGuidance = courseData != null,
                    errorMessage = if (courseData != null) null else NOT_READY,
                ),
            )
        }
    }

    // ---------- 2-2. 미리보기 ----------

    override fun onPreviewBack() {
        selected = null
        update { it.copy(step = DemoStep.REGION) }
    }

    override fun onToggleStop(stopId: String) {
        if (state.step != DemoStep.PREVIEW || state.preview.stops.none { it.id == stopId }) return
        update {
            val ids = it.preview.selectedStopIds
            it.copy(preview = it.preview.copy(selectedStopIds = if (stopId in ids) ids - stopId else ids + stopId))
        }
    }

    // ---------- 2-1. 길이 (시연 흐름에서 빠짐) ----------

    override fun onLengthSelected(optionId: String) {
        val option = state.length.options.firstOrNull { it.id == optionId } ?: return
        if (!option.course.enabled) return
        update { it.copy(length = it.length.copy(selectedId = optionId)) }
    }

    override fun onLengthConfirmed() {
        if (state.length.selected == null || selected == null) return
        update { it.copy(step = DemoStep.CONFIRM) }
    }

    override fun onLengthBack() = update { it.copy(step = DemoStep.PREVIEW) }

    // ---------- 3. 확인 (시연 흐름에서 빠짐) ----------

    override fun onCancel() = update { it.copy(step = DemoStep.PREVIEW) }

    /** 미리보기의 "안내 시작"(또는 확인 창의 "확인"). 고른 코스의 가짜 주행을 처음부터 재생한다. */
    override fun onStartGuidance() {
        val sel = selected ?: return
        val courseData = sel.data ?: return
        if (state.step != DemoStep.PREVIEW && state.step != DemoStep.CONFIRM) return
        val stops = courseData.stops.filter { it.id in state.preview.selectedStopIds }
        val run = DriveRun(sel.course, courseData, wallAnchorMs = wallClock(), stops = stops)
        drive = run
        val summary = summary(sel.course)
        val approach = courseData.route.approach
        update {
            it.copy(
                step = DemoStep.APPROACH,
                approach = ApproachUiState(
                    course = summary,
                    startPoint = sel.course.start.toMap(),
                    currentPosition = run.approachRoad.firstOrNull()?.toMap(),
                    distanceToStartKm = approach.distanceM / 1000,
                    minutesToStart = ceilMinutes(approach.durationSec),
                    guidance = GuidanceStatus.GUIDING,
                    showRecordingHint = true,
                    routeToStart = run.approachRoad.map { p -> p.toMap() },
                ),
                driving = DrivingUiState(
                    courseName = summary.name,
                    courseShape = run.courseRoad.map { p -> p.toMap() },
                    stopPoints = run.stopPoints,
                ),
                result = ResultUiState(),
                demo = it.demo.copy(
                    playing = true,
                    driveResultJson = null,
                    courseProgress = 0.0,
                    scriptSec = 0,
                    scriptTotalSec = durationSec(courseData.replay),
                    synthetic = courseData.replay.synthetic,
                ),
            )
        }
        applySpeed()
    }

    // ---------- 4. 시작점 안내 ----------

    override fun onDismissRecordingHint() = update { it.copy(approach = it.approach.copy(showRecordingHint = false)) }

    /** 안내를 그만두면 미리보기로 돌아간다(다시 "안내 시작" 가능). */
    override fun onCancelGuidance() {
        drive = null
        update { it.copy(step = if (selected != null) DemoStep.PREVIEW else DemoStep.REGION, demo = it.demo.copy(playing = false)) }
    }

    // ---------- 5. 주행 ----------

    override fun onEndGuidance() = restart()

    /** 이탈 지점이 다가올 때 "지나가기": 이번 장소는 들르지 않는다. */
    override fun onSkipStop() {
        val run = drive ?: return
        if (state.step != DemoStep.DRIVING || run.detour != null) return
        if (upcomingStop(run) == null) return
        run.plannedStops.removeFirst()
        afterSignals()
    }

    /** 장소에 도착한 뒤 "코스로 돌아가기": 빠졌던 코스 지점까지 돌아오는 안내를 시작한다. */
    override fun onReturnToCourse() {
        val run = drive ?: return
        val d = run.detour ?: return
        if (d.phase != StopPhase.ARRIVED) return
        d.startReturn()
        applySpeed(notify = false)
        afterSignals()
    }

    // ---------- 6. 결과 ----------

    override fun onRetryUpload() = update { it.copy(result = it.result.copy(upload = UploadStatus.DONE, resultUrl = config.resultUrl)) }

    override fun onDone() = restart()

    // ---------- 시연 조작 ----------

    /** 실제로 흐른 시간만큼 재생한다. ViewModel 타이머가 부른다. */
    fun tick(realElapsedMs: Long) {
        val run = drive ?: return
        if (!state.demo.playing) return
        if (run.resumedNoticeMs > 0) run.resumedNoticeMs -= realElapsedMs
        val d = run.detour
        if (d != null) {
            tickDetour(run, d, realElapsedMs)
        } else {
            // 들를 곳 이탈 지점에 닿으면 그 신호에서 멈춘다. 남은 시간은 버린다(한 틱이라 화면에 티 나지 않음).
            run.player.advanceBy(realElapsedMs, stopAfter = { run.detour != null }, sink = ::handle)
            finishIfReplayEnded(run)
        }
        afterSignals()
    }

    /** 들를 곳 왕복 재생. 가는 길이 끝나면 도착(버튼 대기), 돌아오는 길이 끝나면 코스 기록을 이어 간다. */
    private fun tickDetour(run: DriveRun, d: Detour, realElapsedMs: Long) {
        if (d.phase != StopPhase.GOING && d.phase != StopPhase.RETURNING) return
        d.player.advanceBy(realElapsedMs, sink = ::handleDetour)
        if (!d.player.isFinished) return
        if (d.phase == StopPhase.GOING) {
            d.arrive()
            run.speedKph = 0.0
        } else {
            endDetour(run, d)
        }
    }

    /**
     * 재생 파일이 끝났는데 완주 판정이 안 났으면(도착 반경 밖으로 지나간 경우 등) 그 자리에서 결과를 만든다.
     * 시연 중 주행 화면에서 멈춰 버리지 않게 하기 위한 안전장치.
     */
    private fun finishIfReplayEnded(run: DriveRun) {
        if (!run.player.isFinished || run.finished || run.enteredAtMs == null || run.detour != null) return
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
        // 들를 곳에 다녀오는 중에는 건너뛰지 않는다. 가는 도중 이탈 지점이 있으면 거기서 멈춘다.
        if (run.detour != null) return
        run.player.seekUntil(::handle) {
            state.step == DemoStep.RESULT || run.detour != null ||
                (state.step == DemoStep.DRIVING && run.tracker.progressRatio >= config.nearFinishProgress)
        }
        finishIfReplayEnded(run)
        afterSignals()
    }

    /** 처음(지역 입력)으로 */
    fun restart() {
        drive = null
        selected = null
        boost = 1.0
        state = DemoFlowState()
        listener?.invoke(state)
    }

    // ---------- 신호 처리 ----------

    /** 주행 한 번에 필요한 것들. 처음부터 하면 새로 만든다. */
    private inner class DriveRun(
        val course: PresetCourse,
        courseData: DemoCourseData,
        val wallAnchorMs: Long,
        stops: List<CourseStop> = emptyList(),
    ) {
        val replay: ReplayScript = courseData.replay
        val energyType: EnergyType = replay.energyType ?: EnergyType.EV
        val courseRoad: List<GeoPoint> = courseData.route.course.geometry
        val approachRoad: List<GeoPoint> = courseData.route.approach.geometry
        val player = ReplayPlayer(replay.signals)
        val session = DriveSession(DriveOrigin(DriveOrigin.Kind.REPLAY, replay.name, replay.synthetic))
        val tracker = CourseTracker(course.toCourse(trackShape = courseRoad))
        val approachGuide = GuidanceTracker(courseData.route.approach)
        val courseGuide = GuidanceTracker(courseData.route.course)
        val scriptAnchorMs = player.startMs

        /** 단계별 배속. 코스마다 접근·코스 길이가 달라 [DemoConfig]의 목표 시간에 맞춘다 */
        val approachSpeed: Double
        val drivingSpeed: Double

        init {
            val (approachMs, courseMs) = splitScript(replay, course.start)
            approachSpeed = maxOf(config.minSpeed, approachMs / 1000.0 / config.approachSeconds)
            drivingSpeed = maxOf(config.minSpeed, courseMs / 1000.0 / config.drivingSeconds)
        }

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
        /** 코스에 들어간 실제(벽시계) 시각. 결과의 시작 시각 */
        var enteredWallMs = 0L

        // 들를 곳 (PLEOS-63)
        /** 아직 안 들른 장소. 이탈 지점 순 */
        val plannedStops = ArrayDeque(stops.sortedBy { it.exitAtM })
        val stopPoints: List<MapPoint> = stops.map { it.location.toMap() }
        /** 지금 다녀오는 중인 장소. 코스 위면 null */
        var detour: Detour? = null
        val visitedStops = ArrayList<String>()
        /** 들렀다 온 동안 흐른 시간(ms, 왕복 + 머문 시간). 이후 시각 계산에 더한다 */
        var pausedWallMs = 0L
        var resumedNoticeMs = 0L
        var resumedStopName = ""
        /** "곧 빠집니다" 동안 배속을 늦췄으면 true */
        var slowForNotice = false

        fun wallAt(scriptMs: Long): Long = wallAnchorMs + (scriptMs - scriptAnchorMs) + pausedWallMs
    }

    /** 들를 곳 한 번 다녀오기. 가는 길 재생 → 도착 → 돌아오는 길 재생 */
    private inner class Detour(val stop: CourseStop) {
        var phase = StopPhase.GOING
            private set
        var player = ReplayPlayer(stop.outboundDrive.toSignals(0))
            private set
        var guide = GuidanceTracker(stop.outbound)
            private set
        /** 첫 프레임부터 남은 거리가 보이게 출발점(= 이탈 지점)으로 한 번 맞춰 둔다 */
        var snapshot: GuidanceSnapshot? = guide.update(stop.outboundDrive.first().point)
        var route: List<MapPoint> = stop.outbound.geometry.map { it.toMap() }
            private set
        private var outboundMs = 0L
        private val stayMs = config.stopStayMinutes * 60_000L

        /** 이 구간을 [DemoConfig.detourLegSeconds]초 안에 보여 줄 배속 */
        val legSpeed: Double get() = maxOf(config.minSpeed, player.durationMs / 1000.0 / config.detourLegSeconds)

        /** 빠진 뒤 흐른 시간(ms). 도착하면 머문 시간을 한 번에 더한다 */
        val elapsedMs: Long
            get() = when (phase) {
                StopPhase.GOING -> player.positionMs - player.startMs
                StopPhase.RETURNING -> outboundMs + stayMs + (player.positionMs - player.startMs)
                else -> outboundMs + stayMs
            }

        fun arrive() {
            outboundMs = player.durationMs
            phase = StopPhase.ARRIVED
        }

        fun startReturn() {
            phase = StopPhase.RETURNING
            player = ReplayPlayer(stop.inboundDrive.toSignals(0))
            guide = GuidanceTracker(stop.inbound)
            snapshot = guide.update(stop.inboundDrive.first().point)
            route = stop.inbound.geometry.map { it.toMap() }
        }
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
            if (event is CourseEvent.Finished) finishCourse(run, signal.timeMs) else leaveCourseIfAtExit(run)
        }
    }

    /** 다음 들를 곳의 이탈 지점에 왔으면 코스 기록을 멈추고 장소 안내로 바꾼다. 진행 거리는 회전 안내와 같은 코스 경로 기준 */
    private fun leaveCourseIfAtExit(run: DriveRun) {
        val next = run.plannedStops.firstOrNull() ?: return
        val progress = run.guide?.progressM ?: return
        if (progress < next.exitAtM) return
        run.plannedStops.removeFirst()
        run.detour = Detour(next)
        run.resumedNoticeMs = 0
        applySpeed(notify = false)
    }

    /** 왕복 신호는 화면(위치·속도·안내)에만 쓴다. 주행 기록·코스 판정에는 넣지 않는다. */
    private fun handleDetour(signal: DriveSignal) {
        val run = drive ?: return
        val d = run.detour ?: return
        when (signal) {
            is DriveSignal.Speed -> run.speedKph = signal.kph
            is DriveSignal.Position -> {
                run.position = signal.point
                d.snapshot = d.guide.update(signal.point)
            }
            else -> Unit
        }
    }

    /** 빠졌던 코스 지점으로 돌아왔다. 코스 재생을 멈춘 곳부터 이어 간다. */
    private fun endDetour(run: DriveRun, d: Detour) {
        run.pausedWallMs += d.elapsedMs
        run.visitedStops += d.stop.name
        run.detour = null
        run.resumedNoticeMs = config.resumedNoticeMs
        run.resumedStopName = d.stop.name
        applySpeed(notify = false)
    }

    /** 이탈 지점이 [DemoConfig.stopNoticeM] 안으로 다가온 다음 장소와 남은 거리(m) */
    private fun upcomingStop(run: DriveRun): Pair<CourseStop, Double>? {
        val next = run.plannedStops.firstOrNull() ?: return null
        val progress = run.guide?.progressM ?: return null
        val left = next.exitAtM - progress
        return if (left <= config.stopNoticeM) next to left.coerceAtLeast(0.0) else null
    }

    private fun enterCourse(run: DriveRun, event: CourseEvent.Entered) {
        run.enteredAtMs = event.timeMs
        run.enteredWallMs = run.wallAt(event.timeMs)
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
        val course = run.course
        val start = run.enteredAtMs ?: timeMs
        // 시작 시각은 코스에 들어간 순간 그대로. 끝 시각에는 들렀다 온 시간이 더해져 있다(wallAt)
        val startWall = if (run.enteredAtMs != null) run.enteredWallMs else run.wallAt(start)
        val distanceKm = (run.session.liveDistanceM - run.enteredDistanceM) / 1000
        val efficiency = Efficiency.between(run.enteredSnapshot, run.latest, electric = run.energyType == EnergyType.EV)
        val result = DriveResult(
            courseId = course.id,
            startedAt = Instant.ofEpochMilli(startWall).atOffset(DriveResult.KST),
            endedAt = Instant.ofEpochMilli(run.wallAt(timeMs)).atOffset(DriveResult.KST),
            durationSec = (timeMs - start) / 1000,
            movingSec = run.courseMovingMs / 1000,
            distanceKm = distanceKm,
            energyType = run.energyType,
            avgFuelEconomy = if (run.energyType == EnergyType.FUEL) efficiency?.value else null,
            avgEvEfficiency = if (run.energyType == EnergyType.EV) efficiency?.value else null,
            fuelEconomySource = efficiency?.let { Efficiency.Source.ESTIMATED },
            maxKph = run.courseMaxKph,
            onCourseRatio = run.tracker.onCourseRatio,
            polyline = thin(run.coursePath, DriveResult.POLYLINE_SPACING_M),
            recordSource = DriveOrigin.Kind.REPLAY,
            synthetic = run.replay.synthetic,
            appVersion = config.appVersion,
        )
        val json = ContractCodec.encodeDriveResult(result, prettyPrint = true)
        state = state.copy(
            step = DemoStep.RESULT,
            result = ResultUiState(
                courseName = course.name,
                regionName = course.place,
                distanceKm = distanceKm,
                durationSec = result.durationSec,
                efficiency = efficiency?.let {
                    Efficiency(
                        value = it.value,
                        unit = if (it.unit == ai.pleosapp.drive.core.drive.Efficiency.Unit.KM_PER_KWH) EfficiencyUnit.KM_PER_KWH else EfficiencyUnit.KM_PER_L,
                        source = EfficiencySource.CALCULATED,
                    )
                },
                startedAtEpochMs = startWall,
                endedAtEpochMs = run.wallAt(timeMs),
                drivenPath = run.drawnMap + listOfNotNull(run.position?.toMap()),
                courseShape = run.courseRoad.map { it.toMap() },
                // 시연: 서버로 보내지 않고 성공 처리, QR은 고정 주소
                resultUrl = config.resultUrl,
                upload = UploadStatus.DONE,
                visitedStops = run.visitedStops.toList(),
                pausedSec = run.pausedWallMs / 1000,
            ),
            demo = state.demo.copy(playing = false, driveResultJson = json, courseProgress = 1.0),
        )
    }

    /** 신호를 처리한 뒤 화면 값을 한 번에 갱신한다. */
    private fun afterSignals() {
        val run = drive ?: return
        val slow = run.detour == null && state.step == DemoStep.DRIVING && upcomingStop(run) != null
        if (slow != run.slowForNotice) {
            run.slowForNotice = slow
            applySpeed(notify = false)
        }
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
                val common = s.driving.copy(
                    drivenKm = (run.session.liveDistanceM - run.enteredDistanceM) / 1000,
                    elapsedSec = ((scriptMs - entered) / 1000).coerceAtLeast(0),
                    drivenPath = run.drawnMap,
                    currentPosition = run.position?.toMap(),
                    speedKph = run.speedKph,
                )
                val d = run.detour
                s.copy(driving = if (d == null) onCourse(run, common, guide, scriptMs) else onDetour(run, d, common, scriptMs))
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
        val detour = run?.detour
        val base = when {
            run == null -> 1.0
            detour != null -> detour.legSpeed
            state.step == DemoStep.DRIVING && run.slowForNotice -> maxOf(config.minSpeed, run.drivingSpeed / config.noticeSlowdown)
            state.step == DemoStep.DRIVING -> run.drivingSpeed
            else -> run.approachSpeed
        }
        val speed = base * boost
        if (detour != null) detour.player.speed = speed else run?.player?.speed = speed
        state = state.copy(demo = state.demo.copy(speed = speed, boost = boost))
        if (notify) listener?.invoke(state)
    }

    private inline fun update(change: (DemoFlowState) -> DemoFlowState) {
        state = change(state)
        listener?.invoke(state)
    }

    /** 코스 위: 평소 기록 화면 + 다가오는 이탈 지점 / 방금 합류 안내 */
    private fun onCourse(run: DriveRun, base: DrivingUiState, guide: GuidanceSnapshot?, scriptMs: Long): DrivingUiState {
        val stop = when {
            run.resumedNoticeMs > 0 -> StopStatus(run.resumedStopName, StopPhase.RESUMED)
            else -> upcomingStop(run)?.let { (st, left) -> StopStatus(st.name, StopPhase.UPCOMING, distanceM = left.roundToInt()) }
        }
        return base.copy(
            isRecording = true,
            nextTurn = guide?.let(::turnOf),
            arrivalAtEpochMs = guide?.let { run.wallAt(scriptMs) + it.remainingSec * 1000 },
            remainingKm = guide?.remainingM?.div(1000),
            remainingMinutes = guide?.remainingSec?.let(::ceilMinutes),
            stop = stop,
            detourRoute = emptyList(),
        )
    }

    /** 들를 곳 왕복 중: 기록은 멈추고, 안내 값은 장소(또는 합류 지점)까지 */
    private fun onDetour(run: DriveRun, d: Detour, base: DrivingUiState, scriptMs: Long): DrivingUiState {
        val g = d.snapshot.takeIf { d.phase != StopPhase.ARRIVED }
        return base.copy(
            isRecording = false,
            nextTurn = g?.let(::turnOf),
            arrivalAtEpochMs = g?.let { run.wallAt(scriptMs) + d.elapsedMs + it.remainingSec * 1000 },
            remainingKm = g?.remainingM?.div(1000),
            remainingMinutes = g?.remainingSec?.let(::ceilMinutes),
            stop = StopStatus(
                name = d.stop.name,
                phase = d.phase,
                distanceM = g?.remainingM?.roundToInt(),
                minutes = g?.remainingSec?.let(::ceilMinutes),
            ),
            detourRoute = d.route,
        )
    }

    // ---------- 바꾸기 ----------

    private fun stopOption(s: CourseStop) = StopOption(
        id = s.id,
        name = s.name,
        address = s.address,
        location = s.location.toMap(),
        exitPoint = s.exit.toMap(),
        exitAtKm = s.exitAtM / 1000,
        extraKm = s.extraDistanceM / 1000,
        extraMinutes = ceilMinutes(s.extraDurationSec),
    )

    private fun coursesFor(regionId: String?, regionName: String?): CoursesUiState {
        if (regionId == null) return CoursesUiState()
        val list = repository.coursesIn(regionId).map(::summary)
        val featured = repository.featuredCourse(regionId)?.id
        return CoursesUiState(
            regionName = regionName.orEmpty(),
            courses = list,
            selectedCourseId = null,
            mapCourse = list.firstOrNull { it.id == featured },
        )
    }

    private fun summary(p: PresetCourse) = CourseSummary(
        id = p.id,
        label = p.label,
        name = shortName(p) + " 코스",
        distanceKm = p.distanceKm,
        estimatedMinutes = p.durationMin,
        shape = p.preview.map { it.toMap() },
        enabled = p.enabled,
        title = p.name,
        area = p.area,
    )

    /** "부천·인천 고양이 코스" → "고양이", "양주·의정부 꼬리 세운 강아지 코스" → "강아지" */
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

    private companion object {
        const val NOT_READY = "준비 중인 코스예요"
        const val MOVING_KPH = 3.0

        /** 이 거리 안에 처음 들어온 위치를 코스 시작으로 본다(배속 계산용 어림값) */
        const val START_NEAR_M = 60.0

        fun GeoPoint.toMap() = MapPoint(lat, lon)

        fun ceilMinutes(sec: Long): Int = ceil(sec / 60.0).toInt()

        fun durationSec(replay: ReplayScript): Long = (replay.signals.lastOrNull()?.timeMs ?: 0L) / 1000

        /** 재생 파일을 접근 구간과 코스 구간 시간(ms)으로 나눈다. 시작점 근처에 처음 온 시각이 경계 */
        fun splitScript(replay: ReplayScript, start: GeoPoint): Pair<Long, Long> {
            val first = replay.signals.firstOrNull()?.timeMs ?: 0L
            val last = replay.signals.lastOrNull()?.timeMs ?: 0L
            val atStart = replay.signals.firstOrNull { it is DriveSignal.Position && distanceM(it.point, start) <= START_NEAR_M }?.timeMs
                ?: (first + (last - first) / 3)
            return (atStart - first) to (last - atStart)
        }
    }
}
