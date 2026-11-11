package ai.pleosapp.drive.ui.state

/**
 * 2-2. 코스 미리보기 + 안내 시작 (PLEOS-62).
 * 내비 앱의 경로 미리보기처럼 고른 코스 하나만 지도에 크게 보여 주고, "안내 시작"을 누르면 시작점 안내로 간다.
 */
data class PreviewUiState(
    val course: CourseSummary? = null,
    /** 도로를 따라 그린 코스 경로(지도 위 선). 촘촘해서 [CourseSummary.shape]보다 실제 길에 가깝다 */
    val courseRoute: List<MapPoint> = emptyList(),
    /** 코스 시작점 = 도착점 (한 바퀴 코스) */
    val startPoint: MapPoint? = null,
    /** 결과 화면 "위치"와 같은 짧은 지역 이름. 예: "부천시" */
    val place: String = "",
    /** 현재 위치(시연: LG가산디지털센터) → 코스 시작점 도로 거리 */
    val distanceToStartKm: Double? = null,
    /** 현재 위치 → 코스 시작점 예상 시간 */
    val minutesToStart: Int? = null,
    /** 경로 데이터가 있어 안내를 시작할 수 있으면 true */
    val canStartGuidance: Boolean = false,
    /** 코스 주변 추천 장소 (PLEOS-63). 코스 순서(이탈 지점 순). 없으면 빈 목록 */
    val stops: List<StopOption> = emptyList(),
    /** 들르기로 고른 장소 id */
    val selectedStopIds: Set<String> = emptySet(),
)

interface PreviewActions {
    /** "안내 시작": 시작점까지 안내를 시작한다. */
    fun onStartGuidance()

    /** "이전": 지역 입력·코스 목록으로 돌아간다(입력한 글자는 그대로). */
    fun onPreviewBack()

    /** 추천 장소를 들를 곳으로 고르거나 뺀다 (PLEOS-63) */
    fun onToggleStop(stopId: String)

    object Noop : PreviewActions {
        override fun onStartGuidance() {}
        override fun onPreviewBack() {}
        override fun onToggleStop(stopId: String) {}
    }
}
