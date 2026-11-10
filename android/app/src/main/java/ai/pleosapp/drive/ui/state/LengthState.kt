package ai.pleosapp.drive.ui.state

/**
 * 2-1. 코스 길이 선택 (E의 CourseLengthScreen). 코스를 고른 뒤 짧은 코스 / 긴 코스를 고른다.
 * 시연에서는 긴 코스(진짜 데이터)만 고를 수 있고 짧은 코스는 [CourseSummary.enabled] = false.
 */
data class CourseLengthUiState(
    /** 고른 코스(모양). 예: "A 고양이 코스" */
    val course: CourseSummary? = null,
    /** 짧은 → 긴 순서 */
    val options: List<CourseLengthOption> = emptyList(),
    val selectedId: String? = null,
) {
    val selected: CourseLengthOption? get() = options.firstOrNull { it.id == selectedId }
}

data class CourseLengthOption(
    val id: String,
    /** "짧은 코스" / "긴 코스" */
    val title: String,
    /** 예: "가볍게 한 바퀴" */
    val description: String,
    val course: CourseSummary,
    /** 추천 표시 */
    val recommended: Boolean = false,
)

interface CourseLengthActions {
    fun onLengthSelected(optionId: String)

    /** "확인": 선택 확인 화면으로 */
    fun onLengthConfirmed()

    /** "이전": 코스 선택으로 */
    fun onLengthBack()

    object Noop : CourseLengthActions {
        override fun onLengthSelected(optionId: String) {}
        override fun onLengthConfirmed() {}
        override fun onLengthBack() {}
    }
}
