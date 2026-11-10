package ai.pleosapp.drive.ui.length

import ai.pleosapp.drive.ui.common.BackButton
import ai.pleosapp.drive.ui.common.MapPlaceholder
import ai.pleosapp.drive.ui.common.MapRouteView
import ai.pleosapp.drive.ui.common.PleosButton
import ai.pleosapp.drive.ui.common.Scrim
import ai.pleosapp.drive.ui.common.SideRail
import ai.pleosapp.drive.ui.common.StatTile
import ai.pleosapp.drive.ui.common.rememberMapScene
import ai.pleosapp.drive.ui.state.CourseLengthActions
import ai.pleosapp.drive.ui.state.CourseLengthOption
import ai.pleosapp.drive.ui.state.CourseLengthUiState
import ai.pleosapp.drive.ui.state.FakeUiStates
import ai.pleosapp.drive.ui.theme.PleosColors
import ai.pleosapp.drive.ui.theme.PleosTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 3. 코스 길이 선택 화면 (새로 추가된 화면, 시안 p3).
 * 같은 모양을 짧게/길게 고르는 팝업이에요. 해당 Jira 티켓은 아직 없어요.
 *
 * 값은 D 의 [CourseLengthUiState], 동작은 [CourseLengthActions] 로만 주고받아요.
 * 준비 중인 길이(enabled=false)는 흐리게 보이고 고를 수 없어요.
 */
@Composable
fun CourseLengthScreen(
    state: CourseLengthUiState,
    actions: CourseLengthActions,
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
                    .width(1040.dp)
                    .background(PleosColors.Panel, RoundedCornerShape(28.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BackButton(actions::onLengthBack, Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                Text(
                    "${course?.label.orEmpty()} ${course?.name.orEmpty()} · 길이를 선택하세요".trim(),
                    color = PleosColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                )
                Text("같은 $shapeName 모양을 작게, 또는 크게 그려요", color = PleosColors.TextSecondary, fontSize = 22.sp)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    state.options.forEach { o ->
                        OptionCard(o, o.id == state.selectedId, actions::onLengthSelected, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(24.dp))
                PleosButton("이 코스로 안내받기", actions::onLengthConfirmed, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun OptionCard(option: CourseLengthOption, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .alpha(if (option.course.enabled) 1f else 0.45f)
            .background(if (selected) PleosColors.AccentSelectedBg else PleosColors.PanelItem, shape)
            .then(if (selected) Modifier.border(3.dp, PleosColors.Accent, shape) else Modifier)
            .clickable { onSelect(option.id) }
            .padding(16.dp),
    ) {
        val scene = rememberMapScene(option.course.shape)
        MapRouteView(
            scene.unit(option.course.shape),
            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(14.dp)),
            strokeWidth = 4.dp, showStart = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(option.title, color = PleosColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            if (option.recommended) {
                Spacer(Modifier.width(12.dp))
                Text(
                    "추천", color = PleosColors.TextPrimary, fontSize = 18.sp,
                    modifier = Modifier.background(PleosColors.Accent, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Text(option.description, color = PleosColors.TextSecondary, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("${option.course.distanceKm.roundToInt()}km", "거리", Modifier.weight(1f).background(PleosColors.Rail, RoundedCornerShape(14.dp)), 32)
            StatTile("약 ${option.course.estimatedMinutes}분", "예상 시간", Modifier.weight(1f).background(PleosColors.Rail, RoundedCornerShape(14.dp)), 32)
        }
    }
}

private val previewCourse = FakeUiStates.heartCourse
private val previewLength = CourseLengthUiState(
    course = previewCourse,
    options = listOf(
        CourseLengthOption("short", "짧은 코스", "가볍게 한 바퀴", previewCourse.copy(distanceKm = 26.0, estimatedMinutes = 50)),
        CourseLengthOption("long", "긴 코스", "하트를 크게 그려요", previewCourse, recommended = true),
    ),
    selectedId = "long",
)

@Preview(widthDp = 1280, heightDp = 800, name = "코스 길이 선택")
@Composable
private fun CourseLengthScreenPreview() {
    PleosTheme { CourseLengthScreen(previewLength, CourseLengthActions.Noop) }
}
