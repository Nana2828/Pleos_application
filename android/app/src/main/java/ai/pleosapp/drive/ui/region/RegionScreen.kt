package ai.pleosapp.drive.ui.region

import ai.pleosapp.drive.ui.common.InfoChip
import ai.pleosapp.drive.ui.common.MapPlaceholder
import ai.pleosapp.drive.ui.common.MapRouteView
import ai.pleosapp.drive.ui.common.SideRail
import ai.pleosapp.drive.ui.theme.PleosColors
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 지역 입력 + 코스 선택 화면 (PLEOS-42·43, 시안 1번과 2번을 합침).
 *
 * "경기도"를 입력하면 아래에 추천 코스 3개가 바로 나오고,
 * 코스를 고르면 오른쪽에 모양 미리보기가 떠요.
 *
 * 값은 [state] 로만 받고, 사용자 동작은 콜백으로만 알려요. (ViewModel 에 의존하지 않아요)
 * 주행 전 정차 상태에서만 쓰는 화면이라 터치 입력을 써요.
 */
@Composable
fun RegionScreen(
    state: RegionUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onCourseSelected: (CourseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.courses.firstOrNull { it.id == state.selectedId }
    Row(modifier = modifier.fillMaxSize().background(PleosColors.Rail)) {
        SideRail()
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            MapPlaceholder()
            Column(modifier = Modifier.padding(start = 32.dp, top = 24.dp).width(560.dp)) {
                InfoChip(if (state.courses.isEmpty()) "어디로 가시겠어요?" else "${state.query.trim()} 드라이브 코스")
                Spacer(Modifier.height(16.dp))
                SearchField(state.query, onQueryChange, onClear)
                if (state.courses.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    CourseList(state.courses, state.selectedId, onCourseSelected)
                }
            }
            if (selected != null) {
                CoursePreview(selected, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp, top = 80.dp))
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
            focusedIndicatorColor = PleosColors.Accent,
            unfocusedIndicatorColor = PleosColors.Panel,
            cursorColor = PleosColors.Accent,
            focusedTextColor = PleosColors.TextPrimary,
            unfocusedTextColor = PleosColors.TextPrimary,
        ),
        modifier = Modifier.fillMaxWidth().height(80.dp),
    )
}

@Composable
private fun CourseList(
    courses: List<CourseItem>,
    selectedId: String?,
    onCourseSelected: (CourseItem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(PleosColors.Panel, RoundedCornerShape(24.dp)).padding(8.dp),
    ) {
        courses.forEach { c -> CourseRow(c, c.id == selectedId, onCourseSelected) }
    }
}

@Composable
private fun CourseRow(item: CourseItem, selected: Boolean, onSelect: (CourseItem) -> Unit) {
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
            .clickable { onSelect(item) }
            .padding(horizontal = 12.dp),
    ) {
        MapRouteView(
            item.shape,
            Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
            strokeWidth = 2.dp, lineColor = Color(0xFFE02B2B),
        )
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text("${item.label}. ${item.name}", color = PleosColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Medium)
            Text("${item.distanceKm}km · 약 ${item.minutes}분", color = PleosColors.TextSecondary, fontSize = 22.sp)
        }
        if (selected) Text("선택", color = PleosColors.Accent, fontSize = 24.sp)
    }
}

@Composable
private fun CoursePreview(course: CourseItem, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 700.dp, height = 640.dp)
            .shadow(16.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .border(4.dp, Color.White, RoundedCornerShape(28.dp)),
    ) {
        MapRouteView(course.shape, Modifier.fillMaxSize(), strokeWidth = 9.dp, showStart = true, focus = true)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp)
                .background(PleosColors.Rail, RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text("♥", color = PleosColors.Pink, fontSize = 26.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${course.label} ${course.name} 미리보기 · 지도 위 ${course.name.removeSuffix(" 코스")} 모양",
                color = PleosColors.TextPrimary,
                fontSize = 26.sp,
            )
        }
    }
}

// ---- 미리보기 / 임시 연결 (가짜 데이터) ----

/** 상태를 이 함수 안에서 들고 있는 임시 버전. ViewModel(D)이 연결되면 쓰지 않아요. */
@Composable
fun RegionScreenWithFakeData(initialQuery: String = "", onCourseSelected: (CourseItem) -> Unit = {}) {
    var query by remember { mutableStateOf(initialQuery) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    PleosTheme {
        RegionScreen(
            state = RegionUiState(query, FakeRegionSource.search(query), selectedId),
            onQueryChange = { query = it; selectedId = null },
            onClear = { query = ""; selectedId = null },
            onCourseSelected = { selectedId = it.id; onCourseSelected(it) },
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "경기도 입력 - 코스 3개")
@Composable
private fun RegionScreenPreview() {
    PleosTheme {
        RegionScreen(
            state = RegionUiState("경기도", FakeRegionSource.search("경기도")),
            onQueryChange = {}, onClear = {}, onCourseSelected = {},
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "경기도 입력 - 하트 선택")
@Composable
private fun RegionScreenSelectedPreview() {
    PleosTheme {
        RegionScreen(
            state = RegionUiState("경기도", FakeRegionSource.search("경기도"), selectedId = "heart"),
            onQueryChange = {}, onClear = {}, onCourseSelected = {},
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "빈 입력")
@Composable
private fun RegionScreenEmptyPreview() {
    PleosTheme {
        RegionScreen(state = RegionUiState(), onQueryChange = {}, onClear = {}, onCourseSelected = {})
    }
}
