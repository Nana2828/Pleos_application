package ai.pleosapp.core

enum class Gear(val code: String) {
    PARK("P"), REVERSE("R"), NEUTRAL("N"), DRIVE("D"), UNKNOWN("?");

    companion object {
        fun fromCode(code: String): Gear = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * 실차·에뮬레이터·재생 데이터가 같은 형태로 들어오도록 입력을 통일한다.
 * 규격 ② 주행 신호 JSONL(`docs/data-contracts.md` 2장)의 `k` 하나가 클래스 하나다.
 * 시간은 ms. 실차에서는 Unix 시각, 재생 파일에서는 시작 기준 경과 시간이다.
 */
sealed interface DriveSignal {
    val timeMs: Long

    /** `gear` */
    data class GearChange(override val timeMs: Long, val gear: Gear) : DriveSignal

    /** `speed` km/h */
    data class Speed(override val timeMs: Long, val kph: Double) : DriveSignal

    /** `pos` 위치와 정확도(m, 모르면 null) */
    data class Position(
        override val timeMs: Long,
        val point: GeoPoint,
        val accuracyM: Double? = null,
    ) : DriveSignal

    /** `odo` 누적 주행거리 km */
    data class Odometer(override val timeMs: Long, val km: Double) : DriveSignal

    /** `battery` 남은 배터리 에너지 Wh */
    data class Battery(override val timeMs: Long, val wh: Double) : DriveSignal

    /** `fuel` 남은 연료 mL */
    data class Fuel(override val timeMs: Long, val ml: Double) : DriveSignal

    /** `fuel_eco` 차가 주는 연비 L/100km (낮을수록 좋음, SDK 값 그대로) */
    data class FuelEconomy(override val timeMs: Long, val litersPer100Km: Double) : DriveSignal

    /** `ev_eff` 차가 주는 전비 km/kWh (높을수록 좋음) */
    data class EvEfficiency(override val timeMs: Long, val kmPerKwh: Double) : DriveSignal
}

/** 신호를 내보내는 곳. 실차 소스와 재생 소스를 이 인터페이스로 바꿔 끼운다. */
interface DriveSignalSource {
    fun start(sink: (DriveSignal) -> Unit)
    fun stop()
}

/** 기록이 어디서 왔는지 남긴다. 합성 데이터가 실제 주행으로 오해되지 않게 한다. */
data class DriveOrigin(
    val kind: Kind,
    val name: String = "",
    val synthetic: Boolean = false,
) {
    /** 규격 ③ `record_source` 값 */
    enum class Kind(val code: String) {
        CAR("car"), REPLAY("replay");

        companion object {
            fun fromCode(code: String): Kind = entries.firstOrNull { it.code == code } ?: CAR
        }
    }

    companion object {
        val CAR = DriveOrigin(Kind.CAR)
    }
}

/** 규격 ②의 머리말 `energy_type`, 규격 ③의 `energy_type` 값 */
enum class EnergyType(val code: String) {
    EV("ev"), FUEL("fuel");

    companion object {
        fun fromCode(code: String): EnergyType? = entries.firstOrNull { it.code == code }
    }
}
