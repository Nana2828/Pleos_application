package ai.pleosapp.drive.ui.result

import ai.pleosapp.drive.ui.theme.PleosColors
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * [content] 를 담은 진짜 QR 코드(스마트폰 카메라로 읽혀요).
 * [content] 가 null 이면(결과를 아직 못 보냄) [emptyText] 를 보여주고, 너무 길어서 못 만들면 안내 문구를 보여줘요.
 * 폰 카메라가 읽기 쉽게 흰 배경과 여백을 두고, 점은 흐려지지 않게 확대해요.
 */
@Composable
fun QrCode(content: String?, emptyText: String, modifier: Modifier = Modifier) {
    val image: ImageBitmap? = remember(content) { content?.let(::encodeQr) }
    Box(modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
        if (image == null) {
            Text(
                if (content == null) emptyText else "QR 을 만들지 못했어요",
                color = PleosColors.Rail, fontSize = 22.sp, textAlign = TextAlign.Center,
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image, IntOffset.Zero, IntSize(image.width, image.height),
                    IntOffset.Zero, IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}

/** 여백 없는 QR(오류 정정 M)을 1점 = 1픽셀 비트맵으로. 너무 길면 null. */
private fun encodeQr(content: String): ImageBitmap? = try {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until m.height) for (x in 0 until m.width) {
        bmp.setPixel(x, y, if (m.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    bmp.asImageBitmap()
} catch (e: Exception) {
    null
}
