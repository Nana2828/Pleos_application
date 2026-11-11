package ai.pleosapp.drive.ui.state

/*
 * 코스 주변 들를 곳 (PLEOS-63).
 * 미리보기(정차 중)에서 골라 두면, 주행 중 그 장소에 가장 가까운 코스 지점에서 기록을 멈추고 장소로 안내한다.
 * 장소에 들른 뒤 같은 지점으로 돌아오면 기록을 이어 간다. 들렀다 온 거리·시간·전력은 기록에 넣지 않는다.
 */

/** 미리보기에 보여 줄 추천 장소 하나 */
data class StopOption(
    val id: String,
    val name: String,
    val address: String,
    val location: MapPoint,
    /** 기록을 멈추고 빠질 코스 위 지점 */
    val exitPoint: MapPoint,
    /** 코스 시작에서 이탈 지점까지 거리 */
    val exitAtKm: Double,
    /** 다녀오는 데 더 드는 거리(가는 길 + 돌아오는 길) */
    val extraKm: Double,
    /** 다녀오는 데 더 드는 시간(머무는 시간 제외) */
    val extraMinutes: Int,
)

enum class StopPhase {
    /** 이탈 지점이 다가온다. "지나가기"로 이번에는 안 들를 수 있다 */
    UPCOMING,

    /** 기록을 멈추고 장소로 가는 중 */
    GOING,

    /** 장소 도착. "코스로 돌아가기"를 누르면 돌아오는 안내를 시작한다 */
    ARRIVED,

    /** 빠졌던 코스 지점으로 돌아가는 중 (기록 멈춤) */
    RETURNING,

    /** 코스에 합류해 기록을 다시 시작했다 (잠깐만 보여 준다) */
    RESUMED,
}

/** 주행 화면의 들를 곳 안내. 들를 곳이 없거나 아직 멀면 null */
data class StopStatus(
    val name: String,
    val phase: StopPhase,
    /**
     * UPCOMING: 이탈 지점까지, GOING: 장소까지, RETURNING: 코스 합류 지점까지 남은 거리(m).
     * ARRIVED·RESUMED는 null
     */
    val distanceM: Int? = null,
    /** GOING·RETURNING의 남은 시간(분). 모르면 null */
    val minutes: Int? = null,
)
