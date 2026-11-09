package ai.pleosapp.drive.ui.state

/** 4. 시작점까지 안내 (PLEOS-44, docs/ui/page-4.png) */
data class ApproachUiState(
    val course: CourseSummary? = null,
    /** 코스 시작점 (지도에 빨간 점) */
    val startPoint: MapPoint? = null,
    /** 현재 위치. 아직 모르면 null */
    val currentPosition: MapPoint? = null,
    /** 시작점까지 남은 거리. 모르면 null */
    val distanceToStartKm: Double? = null,
    /** 시작점까지 남은 시간. 모르면 null */
    val minutesToStart: Int? = null,
    val guidance: GuidanceStatus = GuidanceStatus.PREPARING,
    /** "시작점에 도착하면 기록이 시작돼요" 안내를 보여 줄지 */
    val showRecordingHint: Boolean = true,
)

enum class GuidanceStatus {
    /** 내비 안내를 준비하는 중 */
    PREPARING,

    /** 시작점으로 안내하는 중 */
    GUIDING,

    /** 시작점 도착. 곧 주행 기록 화면으로 넘어간다 */
    ARRIVED,

    /** 안내를 시작하지 못함 */
    FAILED,
}

interface ApproachActions {
    /** 안내 문구의 "확인": 문구를 닫는다. */
    fun onDismissRecordingHint()

    /** 안내를 그만둔다. */
    fun onCancelGuidance()

    object Noop : ApproachActions {
        override fun onDismissRecordingHint() {}
        override fun onCancelGuidance() {}
    }
}
