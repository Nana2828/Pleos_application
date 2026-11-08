package ai.pleosapp.probe

/**
 * 평균 효율(연비·전비)을 구한다. 앱 코어(`app/`)로 그대로 옮길 수 있게 안드로이드 의존 없이 쓴다.
 *
 * 순서 (docs/data-contracts.md 3장 `fuel_economy_source`):
 * 1. 차가 직접 주는 평균 연비·전비 (Vehicle SDK). 에뮬레이터 v2.1.0에서는 지원하지 않음(2026-11-08 실측).
 * 2. 주행 시작·끝의 누적 주행거리와 남은 에너지 차이로 계산 (`estimated`).
 * 3. 둘 다 안 되면 null. 화면은 "예시 값" 또는 표시 안 함으로 처리한다(합의 필요).
 */
object Efficiency {
    /** 한 시점의 차량 값. 읽지 못한 값은 null. */
    data class Snapshot(
        /** 누적 주행거리 km (Vehicle SDK Odometer.getCurrentDistanceDriven) */
        val odometerKm: Double?,
        /** 남은 배터리 에너지 Wh (AAOS EV_BATTERY_LEVEL) */
        val batteryWh: Double?,
        /** 남은 연료 mL (AAOS FUEL_LEVEL) */
        val fuelMl: Double?,
    )

    enum class Unit(val code: String) { KM_PER_L("km_per_l"), KM_PER_KWH("km_per_kwh") }

    data class Result(val value: Double, val unit: Unit, val distanceKm: Double)

    /** 이보다 짧게 달렸거나 적게 썼으면 오차가 커서 계산하지 않는다. 💡 제안 값, 실차 미검증 */
    const val MIN_DISTANCE_KM = 0.5
    const val MIN_ENERGY_KWH = 0.05
    const val MIN_FUEL_L = 0.05

    /** 전기차면 km/kWh, 내연기관이면 km/L. 에너지가 줄지 않았거나(회생제동·충전) 값이 없으면 null. */
    fun between(start: Snapshot, end: Snapshot, electric: Boolean): Result? {
        val distance = diff(end.odometerKm, start.odometerKm) ?: return null
        if (distance < MIN_DISTANCE_KM) return null
        return if (electric) {
            val usedKwh = diff(start.batteryWh, end.batteryWh)?.div(1_000) ?: return null
            if (usedKwh < MIN_ENERGY_KWH) null else Result(distance / usedKwh, Unit.KM_PER_KWH, distance)
        } else {
            val usedL = diff(start.fuelMl, end.fuelMl)?.div(1_000) ?: return null
            if (usedL < MIN_FUEL_L) null else Result(distance / usedL, Unit.KM_PER_L, distance)
        }
    }

    /** Vehicle SDK 연비(L/100km, 낮을수록 좋음)를 화면·서버용 km/L(높을수록 좋음)로 바꾼다. */
    fun kmPerLiterFromLitersPer100Km(value: Double): Double? =
        if (value.isFinite() && value > 0) 100.0 / value else null

    private fun diff(later: Double?, earlier: Double?): Double? {
        if (later == null || earlier == null || !later.isFinite() || !earlier.isFinite()) return null
        return later - earlier
    }
}
