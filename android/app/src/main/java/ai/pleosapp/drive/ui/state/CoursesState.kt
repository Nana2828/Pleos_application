package ai.pleosapp.drive.ui.state

/** 2. 코스 선택 (PLEOS-43, docs/ui/page-2.png) */
data class CoursesUiState(
    /** 예: "수원" */
    val regionName: String = "",
    val courses: List<CourseSummary> = emptyList(),
    /** 미리보기 중인 코스. 아직 안 골랐으면 null */
    val selectedCourseId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /**
     * 지도에 크게 보여 줄 그 지역의 진짜 코스 (PLEOS-62). 시 이름(부천·화성·동두천)을 입력하면 채워지고,
     * 여러 코스가 나오는 "경기도"에서는 null.
     */
    val mapCourse: CourseSummary? = null,
) {
    /** 지도 미리보기에 그릴 코스 */
    val selectedCourse: CourseSummary?
        get() = courses.firstOrNull { it.id == selectedCourseId }
}

interface CoursesActions {
    /** 목록에서 코스를 누르면 미리보기를 바꾼다. */
    fun onCoursePreview(courseId: String)

    /** "선택"을 누르면 부른다. 다음 화면(선택 확인)으로 가는 것은 화면 쪽에서 한다. */
    fun onCourseChosen(courseId: String)

    object Noop : CoursesActions {
        override fun onCoursePreview(courseId: String) {}
        override fun onCourseChosen(courseId: String) {}
    }
}
