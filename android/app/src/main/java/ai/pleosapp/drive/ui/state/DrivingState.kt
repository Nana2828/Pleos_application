package ai.pleosapp.drive.ui.state

/**
 * 5. 주행 기록 중 (PLEOS-45, docs/ui/page-5.png)
 *
 * 주행 중 화면이라 숫자 몇 개와 "안내 종료" 버튼 하나만 둔다.
 * 회전 안내·남은 거리처럼 내비(NaviHelper)가 주는 값은 못 받을 수 있어 모두 null을 허용한다.
 */
data class DrivingUiState(
    val courseName: String = "",
    /** 코스에 들어가 기록 중이면 true */
    val isRecording: Boolean = false,
    /** 다음 회전 안내. 모르면 null */
    val nextTurn: TurnInfo? = null,
    /** 도착 예정 시각 (epoch 밀리초). 모르면 null */
    val arrivalAtEpochMs: Long? = null,
    /** 도착까지 남은 거리. 모르면 null */
    val remainingKm: Double? = null,
    /** 도착까지 남은 시간. 모르면 null */
    val remainingMinutes: Int? = null,
    /** 기록 시작부터 달린 거리 */
    val drivenKm: Double = 0.0,
    /** 기록 시작부터 지난 시간 */
    val elapsedSec: Long = 0,
    /** 작은 지도용: 코스 모양 */
    val courseShape: List<MapPoint> = emptyList(),
    /** 작은 지도용: 지금까지 달린 길 */
    val drivenPath: List<MapPoint> = emptyList(),
    val currentPosition: MapPoint? = null,
    /** 현재 속도. 모르면 null */
    val speedKph: Double? = null,
)

data class TurnInfo(
    val direction: TurnDirection,
    /** 회전 지점까지 남은 거리(미터) */
    val distanceM: Int,
    /** 안내 문구. 예: "우회전", "회전교차로 2번째 출구". 비어 있으면 화면이 direction으로 정한다 */
    val text: String = "",
    /** 들어갈 도로 이름. 모르면 빈 문자열 */
    val roadName: String = "",
)

enum class TurnDirection { STRAIGHT, LEFT, RIGHT, U_TURN }

interface DrivingActions {
    /** "안내 종료" */
    fun onEndGuidance()

    object Noop : DrivingActions {
        override fun onEndGuidance() {}
    }
}
