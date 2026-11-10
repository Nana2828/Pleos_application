package ai.pleosapp.drive.ui.confirm

import ai.pleosapp.drive.ui.common.MapPlaceholder
import ai.pleosapp.drive.ui.common.PleosButton
import ai.pleosapp.drive.ui.common.PleosButtonStyle
import ai.pleosapp.drive.ui.common.Scrim
import ai.pleosapp.drive.ui.common.SideRail
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 4. 선택 확인 화면 (PLEOS-44, 시안 p4).
 * "B 하트 경로로 안내합니다" 팝업. 확인을 누르면 시작점까지 안내가 시작돼요.
 */
@Composable
fun ConfirmScreen(
    title: String,    // "B 하트 경로로 안내합니다"
    summary: String,  // "수원 하트 코스 · 42km · 약 80분"
    notice: String,   // "먼저 코스 시작점까지 안내해 드릴게요"
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
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
                    .width(760.dp)
                    .background(PleosColors.Panel, RoundedCornerShape(28.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("♥", color = PleosColors.Pink, fontSize = 88.sp)
                Spacer(Modifier.height(8.dp))
                Text(title, color = PleosColors.TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(summary, color = PleosColors.TextSecondary, fontSize = 24.sp, textAlign = TextAlign.Center)
                Text(notice, color = PleosColors.TextSecondary, fontSize = 24.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    PleosButton("취소", onCancel, Modifier.weight(1f), PleosButtonStyle.Secondary)
                    PleosButton("확인", onConfirm, Modifier.weight(1f))
                }
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "선택 확인")
@Composable
private fun ConfirmScreenPreview() {
    PleosTheme {
        ConfirmScreen("B 하트 경로로 안내합니다", "수원 하트 코스 · 42km · 약 80분", "먼저 코스 시작점까지 안내해 드릴게요", {}, {})
    }
}
