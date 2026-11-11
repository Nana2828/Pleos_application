package ai.pleosapp.drive.ui.result

import ai.pleosapp.drive.ui.common.BackButton
import ai.pleosapp.drive.ui.common.MapPlaceholder
import ai.pleosapp.drive.ui.common.SideRail
import ai.pleosapp.drive.ui.common.StatTile
import ai.pleosapp.drive.ui.common.drawPolyline
import ai.pleosapp.drive.ui.common.efficiencyUnit
import ai.pleosapp.drive.ui.common.formatDuration
import ai.pleosapp.drive.ui.common.formatEfficiency
import ai.pleosapp.drive.ui.common.formatKm
import ai.pleosapp.drive.ui.common.formatPeriod
import ai.pleosapp.drive.ui.common.rememberMapScene
import ai.pleosapp.drive.ui.state.FakeUiStates
import ai.pleosapp.drive.ui.state.ResultActions
import ai.pleosapp.drive.ui.state.ResultUiState
import ai.pleosapp.drive.ui.state.UploadStatus
import ai.pleosapp.drive.ui.theme.PleosColors
import ai.pleosapp.drive.ui.theme.PleosTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 7. 도착 결과 화면 (PLEOS-46, 시안 p7).
 * 왼쪽엔 지도 위에 코스(파랑)와 달린 길(빨강), 오른쪽엔 거리·시간·연비·위치와 결과 QR.
 * 값은 D 의 [ResultUiState], 동작은 [ResultActions].
 *
 * QR 은 진짜 QR 이에요. state.resultUrl(웹 기본 주소)에 이번 주행 결과를 담아 [ResultLink] 로 주소를 만들어요.
 * 서버가 아직 없어서 결과를 주소 안에 같이 담아요(폰 웹이 그 값으로 화면을 그려요).
 */
@Composable
fun ResultScreen(
    state: ResultUiState,
    actions: ResultActions,
    modifier: Modifier = Modifier,
) {
    val scene = rememberMapScene(state.courseShape, state.drivenPath)
    val course = scene.unit(state.courseShape)
    val driven = scene.unit(state.drivenPath)
    Row(modifier.fillMaxSize().background(PleosColors.Rail)) {
        SideRail()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            MapPlaceholder { t ->
                drawPolyline(t, course, PleosColors.Accent, 12.dp.toPx())
                drawPolyline(t, driven, Color(0xFFE02B2B), 5.dp.toPx())
            }
            BackButton(actions::onDone, Modifier.align(Alignment.TopStart).padding(24.dp), label = "처음으로")
        }
        Column(
            Modifier
                .width(500.dp)
                .fillMaxHeight()
                .padding(16.dp)
                .background(PleosColors.Panel, RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("주행 완료", color = PleosColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Text(state.courseName, color = PleosColors.TextSecondary, fontSize = 26.sp, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth()) {
                StatTile(formatKm(state.distanceKm), "주행 거리", Modifier.weight(1f), 34)
                StatTile(formatDuration(state.durationSec), "주행 시간", Modifier.weight(1f), 34)
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                StatTile(formatEfficiency(state.efficiency), "평균 연비 (${efficiencyUnit(state.efficiency)})", Modifier.weight(1f), 34)
                StatTile(state.regionName, "위치", Modifier.weight(1f), 34)
            }
            Spacer(Modifier.height(20.dp))
            Text(formatPeriod(state.startedAtEpochMs, state.endedAtEpochMs), color = PleosColors.TextSecondary, fontSize = 26.sp)
            Spacer(Modifier.height(16.dp))
            val qrUrl = remember(state.resultUrl, state.startedAtEpochMs, state.endedAtEpochMs, state.drivenPath) {
                state.resultUrl?.takeIf { state.upload == UploadStatus.DONE }?.let { ResultLink.build(it, state) }
            }
            // 웹 주소가 빌드에 없으면(PLEOS-64, local.properties 의 pleos.webUrl) QR 자리에 그렇게 알려 줘요
            val noWebUrl = state.upload == UploadStatus.DONE && state.resultUrl == null
            QrCode(qrUrl, if (noWebUrl) "웹 주소가 설정되지 않았어요" else "결과를 준비하는 중이에요", Modifier.size(300.dp))
            Spacer(Modifier.height(12.dp))
            when (state.upload) {
                UploadStatus.UPLOADING -> Text("결과를 보내는 중이에요", color = PleosColors.TextSecondary, fontSize = 26.sp, textAlign = TextAlign.Center)
                UploadStatus.DONE -> Text(
                    if (noWebUrl) "local.properties 에 pleos.webUrl 을 넣고 다시 빌드해요" else "QR을 찍어 친구와 비교해요",
                    color = PleosColors.TextSecondary, fontSize = if (noWebUrl) 20.sp else 26.sp, textAlign = TextAlign.Center,
                )
                UploadStatus.FAILED -> Text(
                    "결과를 보내지 못했어요. 눌러서 다시 보내기", color = PleosColors.DangerText, fontSize = 24.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(onClick = actions::onRetryUpload).padding(8.dp),
                )
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "도착 결과")
@Composable
private fun ResultScreenPreview() {
    PleosTheme { ResultScreen(FakeUiStates.result, ResultActions.Noop) }
}
