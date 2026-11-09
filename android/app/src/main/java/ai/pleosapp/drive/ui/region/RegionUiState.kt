package ai.pleosapp.drive.ui.region

/** 검색 결과 한 줄. 예: 이름 "수원역", 설명 "경기 수원시 팔달구" */
data class RegionSuggestion(
    val name: String,
    val detail: String,
)

/** 지역 입력 화면이 그리는 값 전부. 화면은 이것만 보고 그려요. */
data class RegionUiState(
    val query: String = "",
    val suggestions: List<RegionSuggestion> = emptyList(),
)
