package ai.pleosapp.drive.core.drive

/**
 * 평균 효율(연비·전비)을 구한다.
 *
 * 순서 (docs/data-contracts.md 3장 `fuel_economy_source`):
 * 1. 차가 직접 주는 연비·전비 (Vehicle SDK) → `vehicle_sdk`. 에뮬레이터 v2.1.0에서는 지원하지 않음(2026-11-08 실측).
 * 2. 주행 시작·끝의 누적 주행거리와 남은 에너지 차이로 계산 → `estimated`.
 * 3. 둘 다 안 되면 null. 화면은 "예시 값" 또는 표시 안 함으로 처리한다(⬜ 합의 필요).
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
    ) {
        companion object {
            val EMPTY = Snapshot(null, null, null)
        }
    }

    enum class Unit(val code: String) { KM_PER_L("km_per_l"), KM_PER_KWH("km_per_kwh") }

    /** 규격 ③ `fuel_economy_source` 값 */
    enum class Source(val code: String) {
        VEHICLE_SDK("vehicle_sdk"), ESTIMATED("estimated"), SAMPLE("sample");

        companion object {
            fun fromCode(code: String): Source? = entries.firstOrNull { it.code == code }
        }
    }

    data class Result(val value: Double, val unit: Unit, val distanceKm: Double)

    /** 결과에 넣을 최종 효율 값과 출처. */
    data class Resolved(val value: Double, val unit: Unit, val source: Source)

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

    /**
     * 차가 준 값이 있으면 그것을, 없으면 전후 차이 계산을 쓴다.
     * [vehicleKmPerKwh]는 SDK `ev_eff`, [vehicleLitersPer100Km]는 SDK `fuel_eco` 그대로.
     * > ⚠️ 확인 필요: SDK 값이 "이번 주행 평균"인지 "순간 값"인지 문서로 확인하지 못했다. 실차에서 확인 후 쓸지 정한다.
     */
    fun resolve(
        electric: Boolean,
        start: Snapshot,
        end: Snapshot,
        vehicleKmPerKwh: Double? = null,
        vehicleLitersPer100Km: Double? = null,
    ): Resolved? {
        if (electric) {
            vehicleKmPerKwh?.takeIf { it.isFinite() && it > 0 }?.let {
                return Resolved(it, Unit.KM_PER_KWH, Source.VEHICLE_SDK)
            }
        } else {
            vehicleLitersPer100Km?.let(::kmPerLiterFromLitersPer100Km)?.let {
                return Resolved(it, Unit.KM_PER_L, Source.VEHICLE_SDK)
            }
        }
        return between(start, end, electric)?.let { Resolved(it.value, it.unit, Source.ESTIMATED) }
    }

    /** 시연용 예시 값. 화면에 "예시 값"으로 표시해야 한다. */
    fun sample(value: Double, electric: Boolean): Resolved =
        Resolved(value, if (electric) Unit.KM_PER_KWH else Unit.KM_PER_L, Source.SAMPLE)

    /** Vehicle SDK 연비(L/100km, 낮을수록 좋음)를 화면·서버용 km/L(높을수록 좋음)로 바꾼다. */
    fun kmPerLiterFromLitersPer100Km(value: Double): Double? =
        if (value.isFinite() && value > 0) 100.0 / value else null

    private fun diff(later: Double?, earlier: Double?): Double? {
        if (later == null || earlier == null || !later.isFinite() || !earlier.isFinite()) return null
        return later - earlier
    }
}
