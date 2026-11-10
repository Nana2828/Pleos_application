package ai.pleosapp.drive.ui.confirm

import ai.pleosapp.drive.ui.common.MapPlaceholder
import ai.pleosapp.drive.ui.common.PleosButton
import ai.pleosapp.drive.ui.common.PleosButtonStyle
import ai.pleosapp.drive.ui.common.Scrim
import ai.pleosapp.drive.ui.common.SideRail
import ai.pleosapp.drive.ui.state.ConfirmActions
import ai.pleosapp.drive.ui.state.ConfirmUiState
import ai.pleosapp.drive.ui.state.FakeUiStates
import ai.pleosapp.drive.ui.theme.PleosColors
import ai.pleosapp.drive.ui.theme.PleosTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 4. 선택 확인 화면 (PLEOS-44, 시안 p4).
 * "B 하트 경로로 안내합니다" 팝업. 확인을 누르면 시작점까지 안내가 시작돼요.
 *
 * 값은 D 의 [ConfirmUiState], 동작은 [ConfirmActions]. 안내를 시작할 수 없으면(canStartGuidance=false)
 * 확인 버튼이 흐려지고 이유를 보여줘요.
 */
@Composable
fun ConfirmScreen(
    state: ConfirmUiState,
    actions: ConfirmActions,
    modifier: Modifier = Modifier,
) {
    val course = state.course
    val shapeName = course?.name?.removeSuffix(" 코스").orEmpty()
    Box(modifier.fillMaxSize().background(PleosColors.Rail)) {
        Row(Modifier.fillMaxSize()) {
            SideRail(dimmed = true)
            MapPlaceholder()
        }
        Scrim {
            Column(
                Modifier
                    .width(760.dp)
                    .background(PleosColors.Panel, RoundedCornerShape(28.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(96.dp).background(PleosColors.Pink, CircleShape), contentAlignment = Alignment.Center) {
                    Text(course?.label.orEmpty(), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "${course?.label.orEmpty()} $shapeName 경로로 안내합니다".trim(),
                    color = PleosColors.TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                if (course != null) {
                    Text(
                        "${state.regionName} ${course.name} · ${course.distanceKm.roundToInt()}km · 약 ${course.estimatedMinutes}분".trim(),
                        color = PleosColors.TextSecondary, fontSize = 24.sp, textAlign = TextAlign.Center,
                    )
                }
                Text(
                    state.errorMessage ?: "먼저 코스 시작점까지 안내해 드릴게요",
                    color = if (state.errorMessage != null) PleosColors.DangerText else PleosColors.TextSecondary,
                    fontSize = 24.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    PleosButton("취소", actions::onCancel, Modifier.weight(1f), PleosButtonStyle.Secondary)
                    PleosButton(
                        "확인", { if (state.canStartGuidance) actions.onStartGuidance() },
                        Modifier.weight(1f).alpha(if (state.canStartGuidance) 1f else 0.4f),
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "선택 확인")
@Composable
private fun ConfirmScreenPreview() {
    PleosTheme { ConfirmScreen(FakeUiStates.confirm, ConfirmActions.Noop) }
}
