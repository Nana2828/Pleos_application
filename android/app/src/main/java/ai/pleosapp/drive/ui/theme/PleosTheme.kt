package ai.pleosapp.drive.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 시안(docs/ui)에서 가져온 색. 정확한 값은 디자인 확정 후 맞춰요. */
object PleosColors {
    val Rail = Color(0xFF12151C)
    val Panel = Color(0xFF161B26)
    val PanelItem = Color(0xFF232A38)
    val Accent = Color(0xFF2F7BFF)
    val AccentDim = Color(0xFF123A80)
    val AccentSelectedBg = Color(0xFF1D3A6E)
    val Scrim = Color(0xB3000000)
    val Pink = Color(0xFFFF4D6D)
    val DangerBg = Color(0xFF3A2430)
    val DangerText = Color(0xFFFF8A9B)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8A93A6)
    val MapBackground = Color(0xFFEDEAE3)
    val MapRoad = Color(0xFFFFFFFF)
    val MapRoadMajor = Color(0xFFF6D98B)

    /** 들를 곳 표시와 들를 곳 경로 (PLEOS-63) */
    val Stop = Color(0xFF1DB37A)
    val StopBg = Color(0xFF173A30)
    /** 기록 일시정지 표시 */
    val Paused = Color(0xFFFFB547)
}

@Composable
fun PleosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PleosColors.Accent,
            background = PleosColors.Rail,
            surface = PleosColors.Panel,
            onSurface = PleosColors.TextPrimary,
        ),
        content = content,
    )
}
