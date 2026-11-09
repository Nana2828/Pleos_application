package ai.pleosapp.drive.ui.guidance

import ai.pleosapp.drive.ui.common.MapPlaceholder
import ai.pleosapp.drive.ui.common.BackButton
import ai.pleosapp.drive.ui.common.PleosButton
import ai.pleosapp.drive.ui.common.SampleRoute
import ai.pleosapp.drive.ui.common.SideRail
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 5. 시작점까지 안내 화면 (PLEOS-44 의 뒷부분, 시안 p5).
 * 지도 위에 코스 모양을 크게 보여주고, 시작점까지 남은 거리와 안내 문구를 띄워요.
 * 실제 길 안내는 NaviHelper(D)가 하고, 이 화면은 값을 보여주기만 해요.
 */
@Composable
fun StartGuidanceScreen(
    courseTitle: String,        // "B 하트 코스"
    distanceToStart: String,    // "3.2km"
    etaToStart: String,         // "약 8분"
    route: List<Offset>,   // 지도 사진 위 코스 선 (0~1)
    start: Offset,         // 코스 시작점
    currentPosition: Offset,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize().background(PleosColors.Rail)) {
        SideRail()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            MapPlaceholder { t ->
                // 코스 선(파랑) + 시작점(분홍) + 현재 위치(파랑)와 그 사이 초록 점선
                val path = androidx.compose.ui.graphics.Path()
                route.forEachIndexed { i, p ->
                    val o = t.toScreen(p)
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(
                    path, PleosColors.Accent,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 16.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
                val s0 = t.toScreen(start)
                val me = t.toScreen(currentPosition)
                drawLine(
                    Color(0xFF22C55E), me, s0, strokeWidth = 7.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(1f, 16.dp.toPx()), 0f),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                drawCircle(Color.White, 20.dp.toPx(), s0)
                drawCircle(PleosColors.Pink, 14.dp.toPx(), s0)
                drawCircle(Color.White, 20.dp.toPx(), me)
                drawCircle(PleosColors.Accent, 14.dp.toPx(), me)
            }
            BackButton(onBack, Modifier.align(Alignment.TopStart).padding(24.dp))
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
                Text("$courseTitle 시작점까지", color = PleosColors.TextSecondary, fontSize = 22.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(distanceToStart, color = PleosColors.TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text(" · $etaToStart", color = PleosColors.TextSecondary, fontSize = 24.sp)
                }
            }
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
                PleosButton("확인", onConfirm, Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 800, name = "시작점까지 안내")
@Composable
private fun StartGuidanceScreenPreview() {
    PleosTheme {
        StartGuidanceScreen("B 하트 코스", "3.2km", "약 8분", SampleRoute.heart, SampleRoute.start, SampleRoute.currentPosition, {}, {})
    }
}
