package ai.pleosapp.drive.ui.driving

import ai.pleosapp.drive.ui.common.MapPlaceholder
import ai.pleosapp.drive.ui.common.PleosButton
import ai.pleosapp.drive.ui.common.PleosButtonStyle
import ai.pleosapp.drive.ui.common.SideRail
import ai.pleosapp.drive.ui.common.drawPolyline
import ai.pleosapp.drive.ui.common.formatClock
import ai.pleosapp.drive.ui.common.formatDuration
import ai.pleosapp.drive.ui.common.formatKm
import ai.pleosapp.drive.ui.common.formatMeters
import ai.pleosapp.drive.ui.common.rememberMapScene
import ai.pleosapp.drive.ui.common.turnArrow
import ai.pleosapp.drive.ui.common.turnLabel
import ai.pleosapp.drive.ui.state.DrivingActions
import ai.pleosapp.drive.ui.state.DrivingUiState
import ai.pleosapp.drive.ui.state.FakeUiStates
import ai.pleosapp.drive.ui.state.StopPhase
import ai.pleosapp.drive.ui.state.StopStatus
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
import kotlin.math.roundToInt

/**
 * 6. 주행 기록 중 화면 (PLEOS-45).
 * TEMP: 시안의 3D 도로 화면은 아직 없어서, 흐름이 끊기지 않게 만든 간단한 임시 화면이에요.
 * 왼쪽엔 코스(파랑)와 지금까지 달린 길(빨강), 오른쪽엔 다음 회전·남은 거리·속도, 아래에 "안내 종료".
 * 값은 D 의 [DrivingUiState], 동작은 [DrivingActions]. 모르는 값(null)은 "-"로 보여줘요.
 *
 * 들를 곳 (PLEOS-63): state.stop 이 있으면 오른쪽 위에 안내 카드를 띄워요. 주행 중이라 문구는 한 줄, 버튼은 하나만.
 * - 곧 빠짐: "지나가기"(이번엔 안 들름) / 가는 중·돌아오는 중: 버튼 없음, 기록 일시정지 표시
 * - 도착(정차): "코스로 돌아가기" / 합류: "기록을 다시 시작해요"
 * 들르는 동안 지도에는 들를 곳 경로(초록)를 그리고, 남은 거리·시간은 장소(또는 합류 지점)까지 값이에요.
 */
@Composable
fun DrivingScreen(
    state: DrivingUiState,
    actions: DrivingActions,
    modifier: Modifier = Modifier,
) {
    val scene = rememberMapScene(state.courseShape, state.stopPoints)
    val course = scene.unit(state.courseShape)
    val driven = scene.unit(state.drivenPath)
    val detour = scene.unit(state.detourRoute)
    val stopDots = scene.unit(state.stopPoints)
    val stop = state.stop
    val away = stop != null && stop.phase in AWAY
    val me = state.currentPosition?.let(scene::unit)
    Row(modifier.fillMaxSize().background(PleosColors.Rail)) {
        SideRail()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            MapPlaceholder { t ->
                drawPolyline(t, course, PleosColors.Accent, 12.dp.toPx())
                drawPolyline(t, driven, Color(0xFFE02B2B), 6.dp.toPx())
                drawPolyline(t, detour, PleosColors.Stop, 8.dp.toPx())
                for (d in stopDots) {
                    val p = t.toScreen(d)
                    drawCircle(Color.White, 14.dp.toPx(), p)
                    drawCircle(PleosColors.Stop, 9.dp.toPx(), p)
                }
                if (me != null) {
                    val m = t.toScreen(me)
                    drawCircle(Color.White, 20.dp.toPx(), m)
                    drawCircle(PleosColors.Pink, 14.dp.toPx(), m)
                }
            }
        }
        Column(
            Modifier
                .width(500.dp)
                .fillMaxHeight()
                .padding(16.dp)
                .background(PleosColors.Panel, RoundedCornerShape(28.dp))
                .padding(28.dp),
        ) {
            if (away) {
                Text("● 기록 일시정지", color = PleosColors.Paused, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("${state.courseName} 기록 중", color = PleosColors.TextSecondary, fontSize = 26.sp)
            }
            Spacer(Modifier.height(16.dp))
            if (stop != null) {
                StopCard(stop, actions)
                Spacer(Modifier.height(20.dp))
            }
            val turn = state.nextTurn
            if (stop?.phase == StopPhase.ARRIVED) {
                // 장소에 서 있는 동안은 회전 안내가 없다
            } else if (turn != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(turnArrow(turn), color = PleosColors.TextPrimary, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(formatMeters(turn.distanceM), color = PleosColors.TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                        Text(turnLabel(turn), color = PleosColors.TextPrimary, fontSize = 28.sp)
                        if (turn.roadName.isNotBlank()) Text(turn.roadName, color = PleosColors.TextSecondary, fontSize = 22.sp)
                    }
                }
            } else {
                Text("회전 안내를 기다리는 중", color = PleosColors.TextSecondary, fontSize = 26.sp)
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(state.remainingKm?.let(::formatKm) ?: "-", remainingLabel(stop), Modifier.weight(1f))
                Stat(state.remainingMinutes?.let { "${it}분" } ?: "-", "남은 시간", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(state.arrivalAtEpochMs?.let(::formatClock) ?: "-", arrivalLabel(stop), Modifier.weight(1f))
                Stat(state.speedKph?.let { "${it.roundToInt()}km/h" } ?: "-", "속도", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(formatKm(state.drivenKm), "달린 거리", Modifier.weight(1f))
                Stat(formatDuration(state.elapsedSec), "달린 시간", Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            PleosButton("안내 종료", actions::onEndGuidance, Modifier.fillMaxWidth(), PleosButtonStyle.Danger)
        }
    }
}

/** 들르는 중에는 기록 대신 장소·합류 지점까지 남은 값을 보여 줘요 */
private val AWAY = setOf(StopPhase.GOING, StopPhase.ARRIVED, StopPhase.RETURNING)

private fun remainingLabel(stop: StopStatus?): String = when (stop?.phase) {
    StopPhase.GOING, StopPhase.ARRIVED -> "장소까지"
    StopPhase.RETURNING -> "코스 합류까지"
    else -> "남은 거리"
}

private fun arrivalLabel(stop: StopStatus?): String = when (stop?.phase) {
    StopPhase.GOING, StopPhase.ARRIVED -> "장소 도착"
    StopPhase.RETURNING -> "합류 예정"
    else -> "도착 예정"
}

/** 들를 곳 안내 카드. 한 줄 문구 + 필요할 때만 버튼 하나 */
@Composable
private fun StopCard(stop: StopStatus, actions: DrivingActions) {
    val (title, detail) = when (stop.phase) {
        StopPhase.UPCOMING -> "${stop.distanceM?.let(::formatMeters) ?: "곧"} 앞에서 빠져요" to stop.name
        StopPhase.GOING -> "${stop.name}(으)로 가는 중" to "다녀오는 동안 기록을 멈춰요"
        StopPhase.ARRIVED -> "${stop.name} 도착" to "출발할 때 아래 버튼을 눌러 주세요"
        StopPhase.RETURNING -> "코스로 돌아가는 중" to "빠졌던 곳에서 기록을 이어 가요"
        StopPhase.RESUMED -> "코스 합류" to "기록을 다시 시작해요"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(PleosColors.StopBg, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Text(title, color = PleosColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(detail, color = PleosColors.TextSecondary, fontSize = 22.sp, maxLines = 1)
        when (stop.phase) {
            StopPhase.UPCOMING -> {
                Spacer(Modifier.height(12.dp))
                PleosButton("지나가기", actions::onSkipStop, Modifier.fillMaxWidth(), PleosButtonStyle.Secondary)
            }
            StopPhase.ARRIVED -> {
                Spacer(Modifier.height(12.dp))
                PleosButton("코스로 돌아가기", actions::onReturnToCourse, Modifier.fillMaxWidth())
            }
            else -> Unit
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = PleosColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text(label, color = PleosColors.TextSecondary, fontSize = 20.sp)
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "주행 기록 중")
@Composable
private fun DrivingScreenPreview() {
    PleosTheme { DrivingScreen(FakeUiStates.driving, DrivingActions.Noop) }
}

@Preview(widthDp = 1280, heightDp = 800, name = "들를 곳 도착")
@Composable
private fun DrivingScreenStopPreview() {
    PleosTheme { DrivingScreen(FakeUiStates.drivingAtStop, DrivingActions.Noop) }
}
