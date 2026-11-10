package ai.pleosapp.drive.ui.state

/** 3. 선택 확인 (PLEOS-44, docs/ui/page-3.png) */
data class ConfirmUiState(
    val regionName: String = "",
    val course: CourseSummary? = null,
    /** 경로 데이터를 받아 안내를 시작할 수 있으면 true. false면 확인 버튼을 막는다. */
    val canStartGuidance: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

interface ConfirmActions {
    /** "확인": 시작점까지 안내를 시작한다. */
    fun onStartGuidance()

    /** "취소": 코스 선택으로 돌아간다. */
    fun onCancel()

    object Noop : ConfirmActions {
        override fun onStartGuidance() {}
        override fun onCancel() {}
    }
}
