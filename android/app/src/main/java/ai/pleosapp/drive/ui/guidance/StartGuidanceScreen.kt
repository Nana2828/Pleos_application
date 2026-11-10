package ai.pleosapp.drive.ui.guidance

import ai.pleosapp.drive.ui.common.BackButton
import ai.pleosapp.drive.ui.common.MapPlaceholder
import ai.pleosapp.drive.ui.common.PleosButton
import ai.pleosapp.drive.ui.common.SideRail
import ai.pleosapp.drive.ui.common.drawPolyline
import ai.pleosapp.drive.ui.common.formatKm
import ai.pleosapp.drive.ui.common.formatMeters
import ai.pleosapp.drive.ui.common.rememberMapScene
import ai.pleosapp.drive.ui.common.turnArrow
import ai.pleosapp.drive.ui.common.turnLabel
import ai.pleosapp.drive.ui.state.ApproachActions
import ai.pleosapp.drive.ui.state.ApproachUiState
import ai.pleosapp.drive.ui.state.FakeUiStates
import ai.pleosapp.drive.ui.state.GuidanceStatus
import ai.pleosapp.drive.ui.state.MapPoint
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 5. 시작점까지 안내 화면 (PLEOS-44 의 뒷부분, 시안 p5).
 * 지도 위에 코스 모양을 크게 보여주고, 시작점까지 남은 거리와 안내 문구를 띄워요.
 * 실제 길 안내는 D(NaviHelper)가 하고, 이 화면은 값을 보여주기만 해요.
 *
 * 시작점에 들어서면 D 가 알아서 주행 화면으로 바꿔요. 그래서 "다음으로" 버튼은 없고,
 * 안내 문구의 "확인"은 그 문구만 닫아요.
 *
 * @param courseShape 코스 모양(파란 선). [ApproachUiState] 에는 없어서 D 의 주행 상태(driving.courseShape)에서 받아요.
 */
@Composable
fun StartGuidanceScreen(
    state: ApproachUiState,
    courseShape: List<MapPoint>,
    actions: ApproachActions,
    modifier: Modifier = Modifier,
) {
    val scene = rememberMapScene(courseShape, state.routeToStart)
    val courseLine = scene.unit(courseShape)
    val toStart = scene.unit(state.routeToStart)
    val start = state.startPoint?.let(scene::unit)
    val me = state.currentPosition?.let(scene::unit)
    Row(modifier.fillMaxSize().background(PleosColors.Rail)) {
        SideRail()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            MapPlaceholder { t ->
                // 코스 선(파랑) + 현재 위치에서 시작점까지 안내 경로(초록 점선) + 시작점(분홍) + 현재 위치(파랑)
                drawPolyline(t, courseLine, PleosColors.Accent, 16.dp.toPx())
                if (toStart.size > 1) {
                    drawPolyline(t, toStart, Color(0xFF22C55E), 7.dp.toPx())
                }
                if (start != null) {
                    val s0 = t.toScreen(start)
                    drawCircle(Color.White, 20.dp.toPx(), s0)
                    drawCircle(PleosColors.Pink, 14.dp.toPx(), s0)
                }
                if (me != null) {
                    val m = t.toScreen(me)
                    drawCircle(Color.White, 20.dp.toPx(), m)
                    drawCircle(PleosColors.Accent, 14.dp.toPx(), m)
                }
            }
            BackButton(actions::onCancelGuidance, Modifier.align(Alignment.TopStart).padding(24.dp), label = "안내 취소")
            state.nextTurn?.let { turn ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .background(PleosColors.Panel, RoundedCornerShape(20.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                ) {
                    Text(turnArrow(turn), color = PleosColors.TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("${formatMeters(turn.distanceM)} 앞 ${turnLabel(turn)}", color = PleosColors.TextPrimary, fontSize = 26.sp)
                        if (turn.roadName.isNotBlank()) Text(turn.roadName, color = PleosColors.TextSecondary, fontSize = 20.sp)
                    }
                }
            }
            Text(
                "코스 시작점", color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(32.dp)
                    .width(390.dp)
                    .background(PleosColors.Panel, RoundedCornerShape(20.dp))
                    .padding(24.dp),
            ) {
                Text("${state.course?.let { "${it.label} ${it.name} " }.orEmpty()}시작점까지", color = PleosColors.TextSecondary, fontSize = 22.sp)
                when (state.guidance) {
                    GuidanceStatus.PREPARING -> Text("안내를 준비하는 중이에요", color = PleosColors.TextPrimary, fontSize = 26.sp)
                    GuidanceStatus.FAILED -> Text("안내를 시작하지 못했어요", color = PleosColors.DangerText, fontSize = 26.sp)
                    GuidanceStatus.ARRIVED -> Text("시작점에 도착했어요", color = PleosColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    GuidanceStatus.GUIDING -> Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            state.distanceToStartKm?.let(::formatKm) ?: "-",
                            color = PleosColors.TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                        )
                        state.minutesToStart?.let { Text(" · 약 ${it}분", color = PleosColors.TextSecondary, fontSize = 24.sp) }
                    }
                }
            }
            if (state.showRecordingHint) {
                Column(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(32.dp)
                        .width(480.dp)
                        .background(PleosColors.Panel, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                ) {
                    Text("시작점에 도착하면 기록이 시작돼요", color = PleosColors.TextPrimary, fontSize = 22.sp)
                    Spacer(Modifier.height(12.dp))
                    PleosButton("확인", actions::onDismissRecordingHint, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "시작점까지 안내")
@Composable
private fun StartGuidanceScreenPreview() {
    PleosTheme { StartGuidanceScreen(FakeUiStates.approach, FakeUiStates.heartShape, ApproachActions.Noop) }
}
