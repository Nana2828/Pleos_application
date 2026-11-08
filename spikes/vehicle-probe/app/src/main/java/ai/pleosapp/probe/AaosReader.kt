package ai.pleosapp.probe

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context

/**
 * AAOS 표준 Car API로 연료·주행거리 관련 속성을 읽는다.
 * Pleos Vehicle SDK 값이 안 나올 때의 대안 후보다. 읽을 수 없는 속성은 이유를 그대로 남긴다.
 */
class AaosReader(context: Context) {
    private val app = context.applicationContext
    private var car: Car? = null
    private var properties: CarPropertyManager? = null

    /** 이름, 속성 ID, 단위 설명 */
    private val targets = listOf(
        Triple("INFO_FUEL_TYPE", VehiclePropertyIds.INFO_FUEL_TYPE, "연료 종류 코드 배열"),
        Triple("INFO_FUEL_CAPACITY", VehiclePropertyIds.INFO_FUEL_CAPACITY, "mL"),
        Triple("FUEL_LEVEL", VehiclePropertyIds.FUEL_LEVEL, "mL"),
        Triple("RANGE_REMAINING", VehiclePropertyIds.RANGE_REMAINING, "m"),
        Triple("PERF_ODOMETER", VehiclePropertyIds.PERF_ODOMETER, "km"),
        Triple("PERF_VEHICLE_SPEED", VehiclePropertyIds.PERF_VEHICLE_SPEED, "m/s"),
        Triple("INFO_EV_BATTERY_CAPACITY", VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY, "Wh"),
        Triple("EV_BATTERY_LEVEL", VehiclePropertyIds.EV_BATTERY_LEVEL, "Wh"),
        Triple("GEAR_SELECTION", VehiclePropertyIds.GEAR_SELECTION, "기어 코드"),
    )

    fun connect(log: (String) -> Unit) {
        try {
            val created = Car.createCar(app)
            car = created
            properties = created?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            log("AAOS Car 연결: ${if (properties != null) "성공" else "PropertyManager 없음"}")
        } catch (t: Throwable) {
            log("AAOS Car 연결 실패: ${t.javaClass.simpleName} ${t.message}")
        }
    }

    /** 한 번 읽은 결과를 "이름 = 값 단위" 줄로 돌려준다. */
    fun readAll(): List<String> {
        val manager = properties ?: return listOf("AAOS: 연결 안 됨")
        return targets.map { (name, id, unit) ->
            val text = try {
                val value = manager.getProperty<Any>(id, 0)
                if (value == null) {
                    "값 없음(null)"
                } else {
                    "${show(value.value)} $unit (status=${value.status})"
                }
            } catch (t: Throwable) {
                "읽기 실패: ${t.javaClass.simpleName} ${t.message?.take(80)}"
            }
            "AAOS $name = $text"
        }
    }

    /** 남은 배터리 에너지 Wh. 읽지 못하면 null */
    fun batteryWh(): Double? = readFloat(VehiclePropertyIds.EV_BATTERY_LEVEL)

    /** 남은 연료 mL. 읽지 못하면 null */
    fun fuelMl(): Double? = readFloat(VehiclePropertyIds.FUEL_LEVEL)

    /** INFO_FUEL_TYPE에 전기(FuelType.ELECTRIC = 10)가 있으면 true, 모르면 null */
    fun isElectric(): Boolean? = runCatching {
        val types = properties?.getProperty<Array<Int>>(VehiclePropertyIds.INFO_FUEL_TYPE, 0)?.value
        types?.contains(FUEL_TYPE_ELECTRIC)
    }.getOrNull()

    private fun readFloat(id: Int): Double? = runCatching {
        properties?.getProperty<Float>(id, 0)?.value?.toDouble()
    }.getOrNull()

    fun release() {
        runCatching { car?.disconnect() }
        car = null
        properties = null
    }

    private companion object {
        const val FUEL_TYPE_ELECTRIC = 10
    }

    private fun show(value: Any?): String = when (value) {
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]")
        is IntArray -> value.joinToString(prefix = "[", postfix = "]")
        is FloatArray -> value.joinToString(prefix = "[", postfix = "]")
        else -> value.toString()
    }
}
