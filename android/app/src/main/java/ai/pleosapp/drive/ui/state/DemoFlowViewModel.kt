package ai.pleosapp.drive.ui.state

import ai.pleosapp.drive.BuildConfig
import ai.pleosapp.drive.core.course.DemoCourseFiles
import ai.pleosapp.drive.core.course.DemoCourseRepository
import ai.pleosapp.drive.core.drive.ContractCodec
import ai.pleosapp.drive.core.navi.DemoRouteCodec
import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 시연 흐름 ViewModel. assets/demo의 코스·경로·가짜 주행을 읽어 [DemoFlowEngine]을 만들고,
 * 재생 중에는 [TICK_MS]마다 흐른 시간을 넘긴다.
 *
 * 화면(E)은 [state]를 모아서 `state.step`에 맞는 화면을 그리고, 버튼은 [actions]의 함수를 부른다.
 * ```
 * val vm: DemoFlowViewModel = viewModel()
 * val s by vm.state.collectAsStateWithLifecycle()
 * when (s.step) { DemoStep.REGION -> RegionScreen(s.region, s.courses, vm.actions) ... }
 * ```
 */
class DemoFlowViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(DemoFlowState())
    val state: StateFlow<DemoFlowState> = _state.asStateFlow()

    /** 시연 데이터를 못 읽었을 때의 이유. 정상이면 null */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private var engine: DemoFlowEngine? = null

    /** 화면 버튼이 부를 함수 모음. 데이터를 읽기 전에는 아무것도 하지 않는다. */
    val actions: DemoFlowActions = object : DemoFlowActions {
        override fun onQueryChange(query: String) = run { it.onQueryChange(query) }
        override fun onClearQuery() = run { it.onClearQuery() }
        override fun onRegionSelected(regionId: String) = run { it.onRegionSelected(regionId) }
        override fun onCoursePreview(courseId: String) = run { it.onCoursePreview(courseId) }
        override fun onCourseChosen(courseId: String) = run { it.onCourseChosen(courseId) }
        override fun onPreviewBack() = run { it.onPreviewBack() }
        override fun onLengthSelected(optionId: String) = run { it.onLengthSelected(optionId) }
        override fun onLengthConfirmed() = run { it.onLengthConfirmed() }
        override fun onLengthBack() = run { it.onLengthBack() }
        override fun onStartGuidance() = run { it.onStartGuidance() }
        override fun onCancel() = run { it.onCancel() }
        override fun onDismissRecordingHint() = run { it.onDismissRecordingHint() }
        override fun onCancelGuidance() = run { it.onCancelGuidance() }
        override fun onEndGuidance() = run { it.onEndGuidance() }
        override fun onRetryUpload() = run { it.onRetryUpload() }
        override fun onDone() = run { it.onDone() }

        override fun setPlaying(playing: Boolean) = run { it.setPlaying(playing) }
        override fun setBoost(value: Double) = run { it.setBoost(value) }
        override fun skipToCourseStart() = run { it.skipToCourseStart() }
        override fun skipToNearFinish() = run { it.skipToNearFinish() }
        override fun restart() = run { it.restart() }

        private inline fun run(block: (DemoFlowEngine) -> Unit) {
            engine?.let(block)
        }
    }

    init {
        viewModelScope.launch {
            try {
                val e = withContext(Dispatchers.Default) { load(app) }
                var lastStep = e.state.step
                var stepAt = SystemClock.elapsedRealtime()
                e.listener = {
                    if (it.step != lastStep) {
                        // 리허설 때 단계별 실제 걸린 시간을 logcat(태그 DemoFlow)으로 본다
                        val now = SystemClock.elapsedRealtime()
                        Log.i(TAG, "단계 $lastStep → ${it.step}: ${now - stepAt}ms, 재생 ${it.demo.scriptSec}s, ×${it.demo.speed}")
                        lastStep = it.step
                        stepAt = now
                    }
                    _state.value = it
                }
                engine = e
                _state.value = e.state
                ticker()
            } catch (t: Exception) {
                Log.e(TAG, "시연 데이터를 읽지 못함", t)
                _loadError.value = "시연 데이터를 읽지 못했습니다: ${t.message}"
            }
        }
    }

    private var resumeOnForeground = false

    /** 앱이 화면에서 사라지면 재생을 멈춘다. 발표 중 다른 화면에 갔다 와도 주행이 혼자 진행되지 않게. */
    fun onAppBackground() {
        val e = engine ?: return
        resumeOnForeground = e.state.demo.playing
        if (resumeOnForeground) e.setPlaying(false)
    }

    /** 다시 보이면 멈추기 전에 재생 중이었던 경우만 이어 간다. */
    fun onAppForeground() {
        if (resumeOnForeground) engine?.setPlaying(true)
        resumeOnForeground = false
    }

    /** 진짜 코스 3개의 코스·경로·가짜 주행을 모두 읽는다 (합쳐 약 3.5MB, 백그라운드에서) */
    private fun load(app: Application): DemoFlowEngine {
        fun asset(name: String) = app.assets.open("demo/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val repo = DemoCourseRepository(::asset)
        val data = repo.realCourses.associate { c ->
            c.id to DemoCourseData(
                route = DemoRouteCodec.decode(asset(DemoCourseFiles.route(c.id))),
                replay = ContractCodec.decodeReplay(asset(DemoCourseFiles.drive(c.id))),
            )
        }
        val webUrl = WebBaseUrl.parse(BuildConfig.RESULT_WEB_URL)
        if (webUrl == null) Log.w(TAG, "결과 QR 웹 주소가 없거나 틀림: '${BuildConfig.RESULT_WEB_URL}' (android/local.properties 의 pleos.webUrl)")
        return DemoFlowEngine(repo, data, DemoConfig(resultUrl = webUrl))
    }

    /** 재생 중이면 흐른 시간만큼 진행한다. Main 스레드에서만 엔진을 만진다. */
    private suspend fun ticker() {
        var last = SystemClock.elapsedRealtime()
        while (true) {
            delay(TICK_MS)
            val now = SystemClock.elapsedRealtime()
            // 화면이 멈췄다 돌아오는 경우 한 번에 너무 많이 건너뛰지 않게 막는다
            val elapsed = (now - last).coerceAtMost(MAX_STEP_MS)
            last = now
            engine?.tick(elapsed)
        }
    }

    private companion object {
        const val TAG = "DemoFlow"
        const val TICK_MS = 50L
        /** 느린 에뮬레이터에서 틱이 늦어도 흐른 시간을 버리지 않게 넉넉히. 백그라운드는 onAppBackground가 멈춘다 */
        const val MAX_STEP_MS = 1_000L
    }
}

/** 화면 버튼 전부 + 시연 조작. E 화면은 필요한 함수만 부르면 된다. */
interface DemoFlowActions :
    RegionActions, CoursesActions, PreviewActions, CourseLengthActions, ConfirmActions, ApproachActions, DrivingActions, ResultActions {
    fun setPlaying(playing: Boolean)
    fun setBoost(value: Double)
    fun skipToCourseStart()
    fun skipToNearFinish()
    fun restart()
}
