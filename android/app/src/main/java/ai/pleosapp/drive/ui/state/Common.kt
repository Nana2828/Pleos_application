package ai.pleosapp.drive.ui.state

/*
 * 화면들이 같이 쓰는 값.
 *
 * 경계 규칙 (D·E 가안):
 * - D(재원)가 이 폴더(ui/state)의 UiState·Actions·ViewModel을 만든다.
 * - E(나연)는 화면(Composable)에서 UiState를 그대로 그리고, 버튼을 누르면 Actions를 부른다.
 * - 화면 문구(예: "연비"/"효율", 숫자 형식)는 E가 정한다. 여기에는 값과 단위만 둔다.
 */

/** 지도 위 한 점 (위도·경도, WGS84). */
data class MapPoint(
    val lat: Double,
    val lng: Double,
)

/** 화면에 보여 줄 코스 요약. 코스 선택·선택 확인 화면이 같이 쓴다. */
data class CourseSummary(
    val id: String,
    /** 목록 앞 글자. 예: "A" */
    val label: String,
    /** 예: "하트 코스" */
    val name: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    /** 코스 모양 좌표. 미리보기와 지도 경로선에 쓴다. */
    val shape: List<MapPoint>,
)

/** 효율(연비·전비) 단위. 에뮬레이터 차는 전기차라 KM_PER_KWH가 나온다. */
enum class EfficiencyUnit { KM_PER_L, KM_PER_KWH }

/** 효율 값을 어떻게 얻었는지. 화면에서 "추정" 같은 표시가 필요할 때 쓴다. */
enum class EfficiencySource {
    /** 차가 직접 준 값 (Vehicle SDK) */
    VEHICLE,

    /** 주행거리와 연료·배터리 변화로 앱이 계산한 값 */
    CALCULATED,

    /** 시연용 예시 값 */
    SAMPLE,
}

data class Efficiency(
    val value: Double,
    val unit: EfficiencyUnit,
    val source: EfficiencySource,
)
