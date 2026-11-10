package ai.pleosapp.drive.ui.result

import ai.pleosapp.drive.ui.common.BackButton
import ai.pleosapp.drive.ui.common.MapRouteView
import ai.pleosapp.drive.ui.common.SampleRoute
import ai.pleosapp.drive.ui.common.SideRail
import ai.pleosapp.drive.ui.common.StatTile
import ai.pleosapp.drive.ui.theme.PleosColors
import ai.pleosapp.drive.ui.theme.PleosTheme
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/**
 * 7. 도착 결과 화면 (PLEOS-46, 시안 p7).
 * 왼쪽엔 지도 사진 위에 달린 경로(파랑 + 빨강 선), 오른쪽엔 거리·시간·연비·위치와 결과 QR.
 *
 * QR 그림은 아직 자리표시예요(스캔은 안 돼요). 실제 QR 은 결과 주소(result_url)로 만들어야 하는데,
 * QR 라이브러리를 앱에 넣을지는 재원 님과 정해야 해요(PLEOS-46).
 */
@Composable
fun ResultScreen(
    title: String,       // "주행 완료 · 수원 하트 코스"
    distance: String,    // "42.3km"
    duration: String,    // "1:24:10"
    efficiency: String,  // "12.8km/L" (전기차면 km/kWh. 표기는 팀에서 정해요)
    place: String,       // "수원시"
    period: String,      // "2026.11.05  10:12 ~ 11:36"
    route: List<Offset>, // 지도 사진 위 달린 경로 (0~1)
    qrCaption: String,   // "QR을 찍어 친구와 비교해요"
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes mapRes: Int = ai.pleosapp.drive.R.drawable.map_suwon,
) {
    Row(modifier.fillMaxSize().background(PleosColors.Rail)) {
        SideRail()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            MapRouteView(
                route, Modifier.fillMaxSize(),
                strokeWidth = 12.dp, lineColor = PleosColors.Accent, innerColor = Color(0xFFE02B2B),
                focus = true, photo = mapRes,
            )
            BackButton(onBack, Modifier.align(Alignment.TopStart).padding(24.dp))
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
            Text(
                title, color = PleosColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth()) {
                StatTile(distance, "주행 거리", Modifier.weight(1f), 42)
                StatTile(duration, "주행 시간", Modifier.weight(1f), 42)
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                StatTile(efficiency, "평균 연비", Modifier.weight(1f), 42)
                StatTile(place, "위치", Modifier.weight(1f), 42)
            }
            Spacer(Modifier.height(20.dp))
            Text(period, color = PleosColors.TextSecondary, fontSize = 26.sp)
            Spacer(Modifier.height(16.dp))
            QrPlaceholder(Modifier.size(230.dp))
            Spacer(Modifier.height(12.dp))
            Text(qrCaption, color = PleosColors.TextSecondary, fontSize = 26.sp, textAlign = TextAlign.Center)
        }
    }
}

/** TEMP: QR 처럼 보이는 그림(스캔 안 됨). 진짜 QR 은 result_url 로 만들어 바꿔요. */
@Composable
private fun QrPlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
        val n = 25
        val cell = size.width / n
        val rnd = Random(11)
        fun finder(cx: Int, cy: Int) {
            drawRect(Color.Black, Offset(cx * cell, cy * cell), Size(7 * cell, 7 * cell))
            drawRect(Color.White, Offset((cx + 1) * cell, (cy + 1) * cell), Size(5 * cell, 5 * cell))
            drawRect(Color.Black, Offset((cx + 2) * cell, (cy + 2) * cell), Size(3 * cell, 3 * cell))
        }
        for (y in 0 until n) for (x in 0 until n) {
            val inFinder = (x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8)
            if (!inFinder && rnd.nextFloat() < 0.5f) {
                drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
        finder(0, 0); finder(n - 7, 0); finder(0, n - 7)
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "도착 결과")
@Composable
private fun ResultScreenPreview() {
    PleosTheme {
        ResultScreen(
            "주행 완료 · 수원 하트 코스", "42.3km", "1:24:10", "12.8km/L", "수원시",
            "2026.11.05  10:12 ~ 11:36", SampleRoute.heart, "QR을 찍어 친구와 비교해요", {},
        )
    }
}
