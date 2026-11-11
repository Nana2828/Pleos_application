package ai.pleosapp.drive.ui.preview

import ai.pleosapp.drive.ui.common.BackButton
import ai.pleosapp.drive.ui.common.InfoChip
import ai.pleosapp.drive.ui.common.MapPlaceholder
import ai.pleosapp.drive.ui.common.PleosButton
import ai.pleosapp.drive.ui.common.SideRail
import ai.pleosapp.drive.ui.common.StatTile
import ai.pleosapp.drive.ui.common.drawPolyline
import ai.pleosapp.drive.ui.common.formatKm
import ai.pleosapp.drive.ui.common.rememberMapScene
import ai.pleosapp.drive.ui.state.FakeUiStates
import ai.pleosapp.drive.ui.state.PreviewActions
import ai.pleosapp.drive.ui.state.PreviewUiState
import ai.pleosapp.drive.ui.theme.PleosColors
import ai.pleosapp.drive.ui.theme.PleosTheme
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 2-2. 코스 미리보기 (PLEOS-62). 내비 앱의 경로 미리보기처럼 고른 코스만 지도에 크게 보여 주고,
 * 오른쪽 카드에서 거리·시간을 확인한 뒤 "안내 시작"을 누르면 바로 시작점까지 안내가 시작돼요.
 * (예전 흐름의 코스 길이 선택·선택 확인 창을 이 화면 하나로 대신해요.)
 *
 * 값은 D 의 [PreviewUiState], 동작은 [PreviewActions]. 정차 중에만 쓰는 화면이라 터치를 써요.
 */
@Composable
fun CoursePreviewScreen(
    state: PreviewUiState,
    actions: PreviewActions,
    modifier: Modifier = Modifier,
) {
    val course = state.course
    val route = state.courseRoute.ifEmpty { course?.shape.orEmpty() }
    val scene = rememberMapScene(route)
    val line = scene.unit(route)
    val start = state.startPoint?.let(scene::unit)
    Row(modifier.fillMaxSize().background(PleosColors.Rail)) {
        SideRail()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            MapPlaceholder(Modifier.fillMaxSize().padding(top = 96.dp, bottom = 72.dp, start = 24.dp, end = 24.dp)) { t ->
                // 코스 경로(파랑) + 출발·도착 지점(분홍). 한 바퀴 코스라 출발점이 곧 도착점이에요.
                drawPolyline(t, line, PleosColors.Accent, 12.dp.toPx())
                if (start != null) {
                    val s0 = t.toScreen(start)
                    drawCircle(Color.White, 22.dp.toPx(), s0)
                    drawCircle(PleosColors.Pink, 15.dp.toPx(), s0)
                }
            }
            BackButton(actions::onPreviewBack, Modifier.align(Alignment.TopStart).padding(24.dp))
            InfoChip("● 출발·도착 지점", Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp))
        }
        Column(
            Modifier
                .width(520.dp)
                .fillMaxHeight()
                .background(PleosColors.Panel)
                .padding(32.dp),
        ) {
            if (course != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(64.dp).background(PleosColors.Pink, CircleShape),
                    ) {
                        Text(course.label, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(18.dp))
                    Text(course.name, color = PleosColors.TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text(course.title, color = PleosColors.TextPrimary, fontSize = 24.sp)
                if (course.area.isNotBlank()) Text(course.area, color = PleosColors.TextSecondary, fontSize = 20.sp)
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth()) {
                    StatTile("${course.distanceKm.roundToInt()}km", "코스 거리", Modifier.weight(1f))
                    StatTile("약 ${course.estimatedMinutes}분", "예상 시간", Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(PleosColors.PanelItem, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                ) {
                    Text("현재 위치에서 시작점까지", color = PleosColors.TextSecondary, fontSize = 20.sp)
                    Text(
                        listOfNotNull(state.distanceToStartKm?.let(::formatKm), state.minutesToStart?.let { "약 ${it}분" })
                            .joinToString(" · ").ifEmpty { "-" },
                        color = PleosColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (!state.canStartGuidance) {
                Text("준비 중인 코스예요", color = PleosColors.DangerText, fontSize = 22.sp)
                Spacer(Modifier.height(12.dp))
            }
            PleosButton(
                "안내 시작",
                { if (state.canStartGuidance) actions.onStartGuidance() },
                Modifier.fillMaxWidth().height(96.dp).alpha(if (state.canStartGuidance) 1f else 0.4f),
            )
        }
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "코스 미리보기")
@Composable
private fun CoursePreviewScreenPreview() {
    PleosTheme { CoursePreviewScreen(FakeUiStates.preview, PreviewActions.Noop) }
}
