package ai.pleosapp.drive.ui.region

import androidx.compose.ui.geometry.Offset

// TEMP: 재원 님의 UiState(PLEOS-58)가 올라오면 이 모델은 지우고 그쪽을 써요.

/** 추천 코스 한 개. 예: "B", "하트 코스", 42km, 약 80분 */
data class CourseItem(
    val id: String,
    val label: String,       // "A"
    val name: String,        // "표범 코스"
    val distanceKm: Int,
    val minutes: Int,
    val shape: List<Offset>, // 지도 사진 위 경로 (0~1, 좌상단이 0,0)
    @androidx.annotation.DrawableRes val mapRes: Int, // 이 코스의 지도 사진
)

/**
 * 지역 입력 + 코스 선택 화면이 그리는 값 전부. (시안 1번과 2번을 한 화면으로 합쳤어요)
 * 화면은 이것만 보고 그려요.
 */
data class RegionUiState(
    val query: String = "",
    val courses: List<CourseItem> = emptyList(),
    val selectedId: String? = null,
)
