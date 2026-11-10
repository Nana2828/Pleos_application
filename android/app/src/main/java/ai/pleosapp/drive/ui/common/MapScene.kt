package ai.pleosapp.drive.ui.common

import ai.pleosapp.drive.ui.state.MapFrame
import ai.pleosapp.drive.ui.state.MapPoint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset

/**
 * 위경도(D 의 [MapPoint])를 지도 위 0~1 좌표([Offset])로 바꿔 줘요.
 * [points] 는 한 화면에 담을 점 전부예요(코스 + 안내 경로처럼). 점이 없으면 모두 (0,0) 이에요.
 */
class MapScene(points: List<MapPoint>) {
    private val frame: MapFrame? = if (points.isEmpty()) null else MapFrame.fit(points)

    fun unit(p: MapPoint): Offset {
        val (x, y) = frame?.toUnit(p) ?: return Offset.Zero
        return Offset(x, y)
    }

    fun unit(ps: List<MapPoint>): List<Offset> = ps.map(::unit)
}

@Composable
fun rememberMapScene(vararg parts: List<MapPoint>): MapScene {
    val all = parts.toList()
    return remember(all) { MapScene(all.flatten()) }
}
