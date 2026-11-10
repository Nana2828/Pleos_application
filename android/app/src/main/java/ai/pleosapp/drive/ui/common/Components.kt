package ai.pleosapp.drive.ui.common

import ai.pleosapp.drive.R
import ai.pleosapp.drive.ui.theme.PleosColors
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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

/**
 * 지도 SDK 가 없어서 지도 사진(map_suwon)을 배경으로 써요. D 의 지도 표시(PLEOS-39)가 되면 바꿔요.
 * 지도·코스 경로는 항상 이 실제 사진 위에 그려요(가짜 그림 지도는 쓰지 않아요).
 *
 * 기본은 영역을 꽉 채우도록 가운데를 잘라 보여줘요. [focus] 에 코스 점(0~1)을 주면 그 코스가
 * 영역에 꽉 차도록 확대해요. [overlay] 에서는 [MapPhotoTransform] 으로 사진 좌표(0~1)를 화면 좌표로 바꿔
 * 선·점을 그려요.
 */
@Composable
fun MapPlaceholder(
    modifier: Modifier = Modifier.fillMaxSize(),
    focus: List<Offset>? = null,
    @DrawableRes photo: Int = R.drawable.map_suwon,
    focusPadding: Float = 0.05f,
    overlay: (DrawScope.(MapPhotoTransform) -> Unit)? = null,
) {
    val image = ImageBitmap.imageResource(photo)
    Canvas(modifier = modifier.clipToBounds().background(PleosColors.MapBackground)) {
        val iw = image.width.toFloat()
        val ih = image.height.toFloat()
        val cover = maxOf(size.width / iw, size.height / ih)
        var scale = cover
        var cx = iw / 2f
        var cy = ih / 2f
        val fp = focus?.filter { it.isSpecified }
        if (fp != null && fp.size > 1) {
            val minX = fp.minOf { it.x }; val maxX = fp.maxOf { it.x }
            val minY = fp.minOf { it.y }; val maxY = fp.maxOf { it.y }
            val bw = ((maxX - minX) * iw).coerceAtLeast(1f)
            val bh = ((maxY - minY) * ih).coerceAtLeast(1f)
            // 코스 전체가 보이도록 맞춰요. 영역이 사진보다 크면 남는 곳은 사진 가장자리로 이어 채워요.
            scale = minOf(size.width / bw, size.height / bh) * (1f - 2f * focusPadding)
            cx = (minX + maxX) / 2f * iw
            cy = (minY + maxY) / 2f * ih
        }
        val dw = iw * scale
        val dh = ih * scale
        val left = if (dw <= size.width) (size.width - dw) / 2f
            else (size.width / 2f - cx * scale).coerceIn(size.width - dw, 0f)
        val top = if (dh <= size.height) (size.height - dh) / 2f
            else (size.height / 2f - cy * scale).coerceIn(size.height - dh, 0f)
        val iwI = image.width
        val ihI = image.height
        fun strip(srcX: Int, srcY: Int, srcW: Int, srcH: Int, x: Float, y: Float, w: Float, h: Float) {
            if (w <= 0f || h <= 0f) return
            drawImage(
                image, IntOffset(srcX, srcY), IntSize(srcW, srcH),
                IntOffset(x.toInt(), y.toInt()),
                IntSize(w.toInt() + 1, h.toInt() + 1),
            )
        }
        // 사진 밖 빈틈: 가장자리 한 줄을 늘려서 채움
        if (top > 0f) strip(0, 0, iwI, 1, left, 0f, dw, top)
        if (top + dh < size.height) strip(0, ihI - 1, iwI, 1, left, top + dh, dw, size.height - top - dh)
        if (left > 0f) strip(0, 0, 1, ihI, 0f, top, left, dh)
        if (left + dw < size.width) strip(iwI - 1, 0, 1, ihI, left + dw, top, size.width - left - dw, dh)
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(iwI, ihI),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(dw.toInt() + 1, dh.toInt() + 1),
            filterQuality = FilterQuality.Medium,
        )
        overlay?.invoke(this, MapPhotoTransform(left, top, dw, dh))
    }
}

/** 지도 사진 위 좌표(0~1, 좌상단 0,0)를 화면 좌표로 바꿔요 */
class MapPhotoTransform(private val left: Float, private val top: Float, private val w: Float, private val h: Float) {
    fun toScreen(p: Offset) = Offset(left + p.x * w, top + p.y * h)
}

/**
 * 지도 사진 위에 코스 경로를 그린 그림. 코스 미리보기·썸네일·길이 선택 카드·도착 결과에 써요.
 * [focus] 가 true 면 코스가 영역에 꽉 차게 확대하고, false 면 사진 전체를 보여줘요(작은 코스가 작게 보여요).
 * [innerColor] 를 주면 가운데에 가는 선을 하나 더 그려요(도착 결과의 파랑+빨강 선).
 */
@Composable
fun MapRouteView(
    route: List<Offset>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 6.dp,
    lineColor: Color = PleosColors.Accent,
    innerColor: Color? = null,
    showStart: Boolean = false,
    focus: Boolean = false,
    @DrawableRes photo: Int = R.drawable.map_suwon,
) {
    MapPlaceholder(modifier, focus = if (focus) route else null, photo = photo) { t ->
        if (route.size > 1) {
            val path = Path()
            var penUp = true
            route.forEach { p ->
                if (p.isUnspecified) { penUp = true; return@forEach }
                val o = t.toScreen(p)
                if (penUp) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                penUp = false
            }
            fun stroke(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path, lineColor, style = stroke(strokeWidth.toPx()))
            if (innerColor != null) drawPath(path, innerColor, style = stroke(strokeWidth.toPx() * 0.4f))
            if (showStart) {
                val s = t.toScreen(route.first { it.isSpecified })
                drawCircle(Color.White, strokeWidth.toPx() * 1.6f, s)
                drawCircle(PleosColors.Pink, strokeWidth.toPx() * 1.1f, s)
            }
        }
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

/** 이전 화면으로 돌아가는 버튼. 차 화면이라 터치 영역을 크게(최소 64dp) */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "이전") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = 64.dp)
            .background(PleosColors.PanelItem, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        Text("‹", color = PleosColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(label, color = PleosColors.TextPrimary, fontSize = 24.sp)
    }
}
