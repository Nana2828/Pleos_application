package ai.pleosapp.drive.ui.state

/** 1. 지역 입력 (PLEOS-42, docs/ui/page-1.png) */
data class RegionUiState(
    /** 입력창 글자 */
    val query: String = "",
    /** 입력에 맞는 지역 후보 */
    val suggestions: List<RegionSuggestion> = emptyList(),
    val isLoading: Boolean = false,
    /** 보여 줄 오류가 없으면 null */
    val errorMessage: String? = null,
)

data class RegionSuggestion(
    val id: String,
    /** 예: "수원시" */
    val name: String,
    /** 예: "경기도" */
    val detail: String,
)

interface RegionActions {
    fun onQueryChange(query: String)
    fun onClearQuery()

    /** 후보를 고르면 부른다. 다음 화면(코스 선택)으로 가는 것은 화면 쪽에서 한다. */
    fun onRegionSelected(regionId: String)

    /** 미리보기·가짜 데이터용. 아무것도 하지 않는다. */
    object Noop : RegionActions {
        override fun onQueryChange(query: String) {}
        override fun onClearQuery() {}
        override fun onRegionSelected(regionId: String) {}
    }
}
