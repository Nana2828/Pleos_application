package ai.pleosapp.drive.ui.region

import ai.pleosapp.drive.ui.theme.PleosColors
import ai.pleosapp.drive.ui.theme.PleosTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 1. 지역 입력 화면 (PLEOS-42, 시안 docs/ui/page-1.png)
 *
 * 값은 [state] 로만 받고, 사용자 동작은 콜백으로만 알려요. (ViewModel 에 의존하지 않아요)
 * 주행 전 정차 상태에서만 쓰는 화면이라 터치 입력을 써요.
 */
@Composable
fun RegionScreen(
    state: RegionUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onRegionSelected: (RegionSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize().background(PleosColors.Rail)) {
        SideRail()
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            MapPlaceholder()
            Column(
                modifier = Modifier.padding(start = 32.dp, top = 24.dp).width(560.dp),
            ) {
                GreetingChip()
                Spacer(Modifier.height(16.dp))
                SearchField(state.query, onQueryChange, onClear)
                if (state.suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SuggestionList(state.suggestions, onRegionSelected)
                }
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
private fun SideRail() {
    Column(
        modifier = Modifier.width(88.dp).fillMaxHeight().background(PleosColors.Rail).padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        if (index == 0) PleosColors.Accent else PleosColors.PanelItem,
                        RoundedCornerShape(14.dp),
                    ),
            )
        }
    }
}

/** 지도 SDK 가 없어서 자리만 잡은 배경. D 의 지도 표시(PLEOS-39)가 되면 바꿔요. */
@Composable
private fun MapPlaceholder() {
    Canvas(modifier = Modifier.fillMaxSize().background(PleosColors.MapBackground)) {
        val w = size.width
        val h = size.height
        for (i in 1..8) {
            val y = h * i / 9f
            drawLine(PleosColors.MapRoad, Offset(0f, y), Offset(w, y), strokeWidth = 6f)
        }
        for (i in 1..12) {
            val x = w * i / 13f
            drawLine(PleosColors.MapRoad, Offset(x, 0f), Offset(x, h), strokeWidth = 6f)
        }
        drawLine(PleosColors.MapRoadMajor, Offset(0f, h * 0.8f), Offset(w, h * 0.3f), strokeWidth = 14f)
        drawLine(PleosColors.MapRoadMajor, Offset(w * 0.55f, 0f), Offset(w * 0.7f, h), strokeWidth = 14f)
    }
}

@Composable
private fun GreetingChip() {
    Text(
        text = "어디로 가시겠어요?",
        color = PleosColors.Rail,
        fontSize = 22.sp,
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 32.sp),
        placeholder = { Text("지역을 입력하세요", color = PleosColors.TextSecondary, fontSize = 28.sp) },
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
private fun SuggestionList(
    suggestions: List<RegionSuggestion>,
    onRegionSelected: (RegionSuggestion) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PleosColors.Panel, RoundedCornerShape(24.dp))
            .padding(vertical = 12.dp),
    ) {
        suggestions.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp) // 차 화면이라 터치 영역을 크게
                    .clickable { onRegionSelected(item) }
                    .padding(horizontal = 20.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(64.dp).background(PleosColors.PanelItem, RoundedCornerShape(14.dp)),
                ) { Text("📍", fontSize = 28.sp) }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(item.name, color = PleosColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                    Text(item.detail, color = PleosColors.TextSecondary, fontSize = 22.sp)
                }
            }
        }
    }
}

// ---- 미리보기 / 임시 연결 (가짜 데이터) ----

/** 상태를 이 함수 안에서 들고 있는 임시 버전. ViewModel 담당이 정해지면 그쪽으로 옮겨요. */
@Composable
fun RegionScreenWithFakeData(onRegionSelected: (RegionSuggestion) -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    PleosTheme {
        RegionScreen(
            state = RegionUiState(query, FakeRegionSource.search(query)),
            onQueryChange = { query = it },
            onClear = { query = "" },
            onRegionSelected = onRegionSelected,
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "지역 입력 - 수원")
@Composable
private fun RegionScreenPreview() {
    PleosTheme {
        RegionScreen(
            state = RegionUiState("수원", FakeRegionSource.search("수원")),
            onQueryChange = {}, onClear = {}, onRegionSelected = {},
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "지역 입력 - 빈 입력")
@Composable
private fun RegionScreenEmptyPreview() {
    PleosTheme {
        RegionScreen(state = RegionUiState(), onQueryChange = {}, onClear = {}, onRegionSelected = {})
    }
}
