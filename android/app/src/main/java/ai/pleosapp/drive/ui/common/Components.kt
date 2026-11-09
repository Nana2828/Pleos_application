package ai.pleosapp.drive.ui.common

import ai.pleosapp.drive.ui.theme.PleosColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 모든 인포 화면이 같이 쓰는 부품. 화면마다 따로 만들지 않아요. */

/** 왼쪽 메뉴 줄. [dimmed] 는 팝업이 뜬 동안 어둡게 보일 때 */
@Composable
fun SideRail(modifier: Modifier = Modifier, dimmed: Boolean = false) {
    Column(
        modifier = modifier.width(88.dp).fillMaxHeight().background(PleosColors.Rail).padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(4) { index ->
            val color = when {
                index == 0 && dimmed -> PleosColors.AccentDim
                index == 0 -> PleosColors.Accent
                else -> PleosColors.PanelItem
            }
            Box(Modifier.size(56.dp).background(color, RoundedCornerShape(14.dp)))
        }
    }
}

/** 지도 SDK 가 없어서 자리만 잡은 배경. D 의 지도 표시(PLEOS-39)가 되면 바꿔요. */
@Composable
fun MapPlaceholder(modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = modifier.background(PleosColors.MapBackground)) {
        val w = size.width
        val h = size.height
        for (i in 1..8) {
            val y = h * i / 9f
            drawLine(PleosColors.MapRoad, Offset(0f, y), Offset(w, y), strokeWidth = 6f)
        }
        for (i in 1..12) {
            val x = w * i / 13f
            drawLine(PleosColors.MapRoad, Offset(x, 0f), Offset(x, h), strokeWidth = 6f)
        }
        drawLine(PleosColors.MapRoadMajor, Offset(0f, h * 0.8f), Offset(w, h * 0.3f), strokeWidth = 14f)
        drawLine(PleosColors.MapRoadMajor, Offset(w * 0.55f, 0f), Offset(w * 0.7f, h), strokeWidth = 14f)
    }
}

/** 어두운 막 위에 팝업을 띄울 때 쓰는 바탕 */
@Composable
fun Scrim(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().background(PleosColors.Scrim),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 지도 위에 얹는 흰 말풍선 (예: "어디로 가시겠어요?") */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = PleosColors.Rail,
        fontSize = 22.sp,
        modifier = modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

enum class PleosButtonStyle { Primary, Secondary, Danger }

/** 차 화면용 큰 버튼. 최소 높이 72dp */
@Composable
fun PleosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PleosButtonStyle = PleosButtonStyle.Primary,
) {
    val (bg, fg) = when (style) {
        PleosButtonStyle.Primary -> PleosColors.Accent to Color.White
        PleosButtonStyle.Secondary -> PleosColors.PanelItem to Color.White
        PleosButtonStyle.Danger -> PleosColors.DangerBg to PleosColors.DangerText
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 72.dp)
            .background(bg, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
    ) {
        Text(text, color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** 큰 숫자 + 작은 설명 (예: 42.3km / 주행 거리) */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueSize: Int = 40) {
    Column(modifier = modifier.padding(8.dp)) {
        Text(value, color = PleosColors.TextPrimary, fontSize = valueSize.sp, fontWeight = FontWeight.Bold)
        Text(label, color = PleosColors.TextSecondary, fontSize = 20.sp)
    }
}

/**
 * 코스 모양 선. 점은 0~1 사이로 줄여 넣어요(좌상단이 0,0).
 * 실제 코스 좌표(위도·경도)를 0~1 로 바꾸는 일은 ViewModel(D) 쪽에서 할지 정해야 해요.
 */
@Composable
fun CourseShape(
    points: List<Offset>,
    modifier: Modifier = Modifier,
    color: Color = PleosColors.Accent,
    strokeWidth: Dp = 6.dp,
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = p.x * size.width
            val y = p.y * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
