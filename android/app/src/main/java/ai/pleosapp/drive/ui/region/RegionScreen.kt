package ai.pleosapp.drive.ui.region

import ai.pleosapp.drive.ui.common.InfoChip
import ai.pleosapp.drive.ui.common.MapPlaceholder
import ai.pleosapp.drive.ui.common.MapRouteView
import ai.pleosapp.drive.ui.common.rememberMapScene
import ai.pleosapp.drive.ui.common.SideRail
import ai.pleosapp.drive.ui.theme.PleosColors
import ai.pleosapp.drive.ui.state.CourseSummary
import ai.pleosapp.drive.ui.state.CoursesActions
import ai.pleosapp.drive.ui.state.CoursesUiState
import ai.pleosapp.drive.ui.state.FakeUiStates
import ai.pleosapp.drive.ui.state.RegionActions
import ai.pleosapp.drive.ui.state.RegionUiState
import ai.pleosapp.drive.ui.theme.PleosTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 지역 입력 + 코스 선택 화면 (PLEOS-42·43, 시안 1번과 2번을 합침).
 *
 * "경기도"를 입력하면 아래에 추천 코스가 바로 나오고, 코스를 누르면 길이 선택으로 넘어가요.
 * 미리보기를 보던 코스가 있으면(되돌아왔을 때) 오른쪽에 모양이 떠요.
 *
 * 값은 D 의 [RegionUiState]·[CoursesUiState] 로만 받고, 사용자 동작은 [RegionActions]·[CoursesActions] 로만 알려요.
 * 준비 중인 코스([CourseSummary.enabled] = false)는 흐리게 보이고, 누르면 안내 문구가 나와요.
 * 주행 전 정차 상태에서만 쓰는 화면이라 터치 입력을 써요.
 */
@Composable
fun RegionScreen(
    region: RegionUiState,
    courses: CoursesUiState,
    regionActions: RegionActions,
    coursesActions: CoursesActions,
    modifier: Modifier = Modifier,
) {
    val selected = courses.selectedCourse
    Row(modifier = modifier.fillMaxSize().background(PleosColors.Rail)) {
        SideRail()
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            MapPlaceholder()
            Column(modifier = Modifier.padding(start = 32.dp, top = 24.dp).width(560.dp)) {
                InfoChip(if (courses.courses.isEmpty()) "어디로 가시겠어요?" else "${region.query.trim()} 드라이브 코스")
                Spacer(Modifier.height(16.dp))
                SearchField(region.query, regionActions::onQueryChange, regionActions::onClearQuery)
                if (courses.courses.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    CourseList(courses.courses, courses.selectedCourseId, coursesActions::onCourseChosen)
                    courses.errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        InfoChip(it)
                    }
                }
            }
            if (selected != null) {
                CoursePreview(selected, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp, top = 80.dp))
            }
            if (courses.courses.isEmpty()) {
                Text(
                    text = "\"경기도\" 라고 말해보세요",
                    color = PleosColors.TextPrimary,
                    fontSize = 26.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(32.dp)
                        .background(PleosColors.Rail, RoundedCornerShape(20.dp))
                        .padding(horizontal = 28.dp, vertical = 18.dp),
                )
            }
            // TODO(PLEOS-42): 시각·날씨는 가짜 값이에요. 실제 값은 정해지면 연결해요.
            Text(
                text = "오전 10:00  18°C",
                color = PleosColors.Rail,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 32.sp),
        placeholder = { Text("지역을 입력하세요 (예: 경기도)", color = PleosColors.TextSecondary, fontSize = 28.sp) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Text(
                    text = "✕",
                    color = PleosColors.TextSecondary,
                    fontSize = 28.sp,
                    modifier = Modifier.clickable(onClick = onClear).padding(16.dp),
                )
            }
        },
        shape = RoundedCornerShape(36.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = PleosColors.Panel,
            unfocusedContainerColor = PleosColors.Panel,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = PleosColors.Accent,
            focusedTextColor = PleosColors.TextPrimary,
            unfocusedTextColor = PleosColors.TextPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .border(2.dp, PleosColors.Accent, RoundedCornerShape(36.dp)),
    )
}

@Composable
private fun CourseList(
    courses: List<CourseSummary>,
    selectedId: String?,
    onCourseSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(PleosColors.Panel, RoundedCornerShape(24.dp)).padding(8.dp),
    ) {
        courses.forEach { c -> CourseRow(c, c.id == selectedId, onCourseSelected) }
    }
}

@Composable
private fun CourseRow(item: CourseSummary, selected: Boolean, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp) // 차 화면이라 터치 영역을 크게
            .then(
                if (selected) Modifier.background(PleosColors.AccentSelectedBg, shape).border(2.dp, PleosColors.Accent, shape)
                else Modifier,
            )
            .alpha(if (item.enabled) 1f else 0.45f)
            .clickable { onSelect(item.id) }
            .padding(horizontal = 12.dp),
    ) {
        val scene = rememberMapScene(item.shape)
        MapRouteView(
            scene.unit(item.shape),
            Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
            strokeWidth = 2.dp, lineColor = Color(0xFFE02B2B),
        )
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text("${item.label}. ${item.name}", color = PleosColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Medium)
            Text(
                if (item.enabled) "${item.distanceKm.roundToInt()}km · 약 ${item.estimatedMinutes}분" else "준비 중",
                color = PleosColors.TextSecondary, fontSize = 22.sp,
            )
        }
        if (selected) Text("선택", color = PleosColors.Accent, fontSize = 24.sp)
    }
}

@Composable
private fun CoursePreview(course: CourseSummary, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 700.dp, height = 640.dp)
            .shadow(16.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .border(4.dp, Color.White, RoundedCornerShape(28.dp)),
    ) {
        val scene = rememberMapScene(course.shape)
        MapRouteView(scene.unit(course.shape), Modifier.fillMaxSize(), strokeWidth = 9.dp, showStart = true)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp)
                .background(PleosColors.Rail, RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text(
                text = "${course.label} ${course.name} 미리보기 · 지도 위 ${course.name.removeSuffix(" 코스")} 모양",
                color = PleosColors.TextPrimary,
                fontSize = 26.sp,
            )
        }
    }
}

// ---- 미리보기 (D 의 가짜 값 FakeUiStates) ----

@Preview(widthDp = 1280, heightDp = 800, name = "경기도 입력 - 코스 3개")
@Composable
private fun RegionScreenPreview() {
    PleosTheme {
        RegionScreen(
            FakeUiStates.region.copy(query = "경기도"), FakeUiStates.courses.copy(selectedCourseId = null),
            RegionActions.Noop, CoursesActions.Noop,
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "경기도 입력 - 하트 선택")
@Composable
private fun RegionScreenSelectedPreview() {
    PleosTheme {
        RegionScreen(FakeUiStates.region.copy(query = "경기도"), FakeUiStates.courses, RegionActions.Noop, CoursesActions.Noop)
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "빈 입력")
@Composable
private fun RegionScreenEmptyPreview() {
    PleosTheme {
        RegionScreen(RegionUiState(), CoursesUiState(), RegionActions.Noop, CoursesActions.Noop)
    }
}
