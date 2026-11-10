package ai.pleosapp.drive.ui.length

import ai.pleosapp.drive.ui.common.BackButton
import ai.pleosapp.drive.ui.common.MapPlaceholder
import ai.pleosapp.drive.ui.common.MapRouteView
import ai.pleosapp.drive.ui.common.PleosButton
import ai.pleosapp.drive.ui.common.SampleRoute
import ai.pleosapp.drive.ui.common.Scrim
import ai.pleosapp.drive.ui.common.SideRail
import ai.pleosapp.drive.ui.common.StatTile
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// TEMP: 재원 님의 UiState(PLEOS-58)가 올라오면 이 모델은 지우고 그쪽을 써요.
data class LengthOption(
    val id: String,
    val title: String,        // "짧은 코스"
    val description: String,  // "가볍게 한 바퀴"
    val distanceKm: Int,
    val minutes: Int,
    val recommended: Boolean,
    val shape: List<Offset>,
    @androidx.annotation.DrawableRes val mapRes: Int,
)

/**
 * 3. 코스 길이 선택 화면 (새로 추가된 화면, 시안 p3).
 * 같은 모양을 짧게/길게 고르는 팝업이에요. 해당 Jira 티켓은 아직 없어요.
 */
@Composable
fun CourseLengthScreen(
    courseTitle: String, // "B 하트 코스"
    options: List<LengthOption>,
    selectedId: String?,
    onOptionSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes mapRes: Int = ai.pleosapp.drive.R.drawable.map_suwon,
) {
    Box(modifier.fillMaxSize().background(PleosColors.Rail)) {
        Row(Modifier.fillMaxSize()) {
            SideRail(dimmed = true)
            MapPlaceholder(photo = mapRes)
        }
        Scrim {
            Column(
                Modifier
                    .width(1040.dp)
                    .background(PleosColors.Panel, RoundedCornerShape(28.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BackButton(onBack, Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                Text("$courseTitle · 길이를 선택하세요", color = PleosColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("같은 하트 모양을 작게, 또는 크게 그려요", color = PleosColors.TextSecondary, fontSize = 22.sp)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)) {
                    options.forEach { o ->
                        OptionCard(o, o.id == selectedId, onOptionSelected, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(24.dp))
                PleosButton("이 코스로 안내받기", onConfirm, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun OptionCard(option: LengthOption, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .background(if (selected) PleosColors.AccentSelectedBg else PleosColors.PanelItem, shape)
            .then(if (selected) Modifier.border(3.dp, PleosColors.Accent, shape) else Modifier)
            .clickable { onSelect(option.id) }
            .padding(16.dp),
    ) {
        MapRouteView(
            option.shape,
            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(14.dp)),
            strokeWidth = 4.dp, showStart = true, photo = option.mapRes,
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
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            StatTile("${option.distanceKm}km", "거리", Modifier.weight(1f).background(PleosColors.Rail, RoundedCornerShape(14.dp)), 32)
            StatTile("약 ${option.minutes}분", "예상 시간", Modifier.weight(1f).background(PleosColors.Rail, RoundedCornerShape(14.dp)), 32)
        }
    }
}

/** TEMP: 시안과 같은 값의 임시 데이터 */
internal val tempLengthOptions = listOf(
    LengthOption("short", "짧은 코스", "가볍게 한 바퀴", 26, 50, false, SampleRoute.scaled(SampleRoute.heart, 0.6f), SampleRoute.HEART_MAP),
    LengthOption("long", "긴 코스", "하트를 크게 그려요", 42, 80, true, SampleRoute.heart, SampleRoute.HEART_MAP),
)

@Preview(widthDp = 1280, heightDp = 800, name = "코스 길이 선택")
@Composable
private fun CourseLengthScreenPreview() {
    PleosTheme {
        CourseLengthScreen("B 하트 코스", tempLengthOptions, "long", {}, {}, {})
    }
}
