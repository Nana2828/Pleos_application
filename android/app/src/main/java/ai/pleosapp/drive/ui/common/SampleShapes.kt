package ai.pleosapp.drive.ui.common

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * TEMP: 재원 님의 가짜 데이터(FakeUiStates)가 오기 전까지 미리보기용으로 쓰는 하트 모양.
 * 데이터를 받으면 이 파일은 지워요.
 */
object SampleShapes {
    /** 0~1 안으로 줄인 하트. [scale] 이 작을수록 작은 하트(짧은 코스 느낌) */
    fun heart(scale: Float = 1f, steps: Int = 80): List<Offset> {
        val raw = (0..steps).map { i ->
            val t = 2 * PI * i / steps
            val x = 16 * sin(t).let { it * it * it }
            val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
            x to -y
        }
        val minX = raw.minOf { it.first }
        val maxX = raw.maxOf { it.first }
        val minY = raw.minOf { it.second }
        val maxY = raw.maxOf { it.second }
        return raw.map { (x, y) ->
            val nx = ((x - minX) / (maxX - minX)).toFloat()
            val ny = ((y - minY) / (maxY - minY)).toFloat()
            val pad = (1f - scale) / 2f
            Offset(pad + nx * scale, pad + ny * scale)
        }
    }
}
