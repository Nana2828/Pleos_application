package ai.pleosapp.drive.ui.common

import ai.pleosapp.drive.ui.state.Efficiency
import ai.pleosapp.drive.ui.state.EfficiencyUnit
import ai.pleosapp.drive.ui.state.TurnDirection
import ai.pleosapp.drive.ui.state.TurnInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** D 의 값(숫자·단위)을 화면 글자로 바꿔요. 문구는 E 가 정해요(ui/state/Common.kt 의 경계 규칙). */

fun formatKm(km: Double): String = String.format(Locale.US, "%.1fkm", km)

fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = sec % 3600 / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

/** 효율 숫자만. 단위는 [efficiencyUnit] 으로 따로 보여줘요(칸이 좁아서). */
fun formatEfficiency(e: Efficiency?): String = if (e == null) "-" else String.format(Locale.US, "%.1f", e.value)

fun efficiencyUnit(e: Efficiency?): String = if (e?.unit == EfficiencyUnit.KM_PER_KWH) "km/kWh" else "km/L"

/** 예: "2026.11.05  10:12 ~ 11:36" (한국 시각) */
fun formatPeriod(startMs: Long, endMs: Long): String {
    val tz = TimeZone.getTimeZone("Asia/Seoul")
    val day = SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.KOREA).apply { timeZone = tz }
    val hm = SimpleDateFormat("HH:mm", Locale.KOREA).apply { timeZone = tz }
    return "${day.format(Date(startMs))} ~ ${hm.format(Date(endMs))}"
}

fun formatClock(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.KOREA).apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }.format(Date(epochMs))

fun formatMeters(m: Int): String = if (m >= 1000) String.format(Locale.US, "%.1fkm", m / 1000.0) else "${m}m"

/** 회전 안내 문구. D 가 text 를 주면 그대로, 비어 있으면 방향으로 정해요. */
fun turnLabel(t: TurnInfo): String = t.text.ifBlank {
    when (t.direction) {
        TurnDirection.LEFT -> "좌회전"
        TurnDirection.RIGHT -> "우회전"
        TurnDirection.U_TURN -> "유턴"
        TurnDirection.STRAIGHT -> "직진"
    }
}

fun turnArrow(t: TurnInfo): String = when (t.direction) {
    TurnDirection.LEFT -> "←"
    TurnDirection.RIGHT -> "→"
    TurnDirection.U_TURN -> "↩"
    TurnDirection.STRAIGHT -> "↑"
}
