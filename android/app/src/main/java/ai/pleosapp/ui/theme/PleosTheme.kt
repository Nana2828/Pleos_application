package ai.pleosapp.ui.theme

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
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8A93A6)
    val MapBackground = Color(0xFFEDEAE3)
    val MapRoad = Color(0xFFFFFFFF)
    val MapRoadMajor = Color(0xFFF6D98B)
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
