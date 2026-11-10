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
 */
@Composable
fun DrivingScreen(
    state: DrivingUiState,
    actions: DrivingActions,
    modifier: Modifier = Modifier,
) {
    val scene = rememberMapScene(state.courseShape)
    val course = scene.unit(state.courseShape)
    val driven = scene.unit(state.drivenPath)
    val me = state.currentPosition?.let(scene::unit)
    Row(modifier.fillMaxSize().background(PleosColors.Rail)) {
        SideRail()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            MapPlaceholder { t ->
                drawPolyline(t, course, PleosColors.Accent, 12.dp.toPx())
                drawPolyline(t, driven, Color(0xFFE02B2B), 6.dp.toPx())
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
            Text("${state.courseName} 기록 중", color = PleosColors.TextSecondary, fontSize = 26.sp)
            Spacer(Modifier.height(16.dp))
            val turn = state.nextTurn
            if (turn != null) {
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
                Stat(state.remainingKm?.let(::formatKm) ?: "-", "남은 거리", Modifier.weight(1f))
                Stat(state.remainingMinutes?.let { "${it}분" } ?: "-", "남은 시간", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(state.arrivalAtEpochMs?.let(::formatClock) ?: "-", "도착 예정", Modifier.weight(1f))
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
