package ai.pleosapp.probe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.FuelEconomyListener
import android.content.Context

/**
 * Pleos Vehicle SDK(Vehicle State)로 연비·주행거리 값을 읽는다.
 * 모든 호출은 IPC 비동기이고, 결과는 onComplete 또는 onFailed로 온다(문서 obtain-domain-object).
 * 단위는 문서 기준: 연비 L/100km, 누적 주행거리 km, 이번 주행 거리 m, 이번 주행 시간 분.
 */
class PleosReader(
    context: Context,
    private val log: (String) -> Unit,
    /** 누적 주행거리(km)를 받을 때마다 부른다. 효율 계산용 */
    private val onOdometerKm: (Double) -> Unit = {},
) {
    private val vehicle = Vehicle(context.applicationContext)
    private val fuel by lazy { vehicle.getFuel() }
    private val odometer by lazy { vehicle.getOdometer() }
    private val ev by lazy { vehicle.getEvBattery() }

    private val fuelListener = object : FuelEconomyListener {
        override fun onFuelEconomyUpdated(efficiency: Float?) = log("이벤트 currentFuelEconomy = $efficiency L/100km")
        override fun onFailed(e: Exception) = log("이벤트 currentFuelEconomy 실패: ${describe(e)}")
    }
    private val odoListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            log("이벤트 distanceDriven = $value km")
            value?.let { onOdometerKm(it.toDouble()) }
        }
        override fun onFailed(e: Exception) = log("이벤트 distanceDriven 실패: ${describe(e)}")
    }
    private val tripListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) = log("이벤트 tripCurrentDrivingDistance = $value m")
        override fun onFailed(e: Exception) = log("이벤트 tripCurrentDrivingDistance 실패: ${describe(e)}")
    }

    fun initialize() {
        try {
            vehicle.initialize()
            log("Pleos Vehicle initialize() 호출 완료 (연결 완료 콜백은 SDK에 없음)")
        } catch (t: Throwable) {
            log("Pleos Vehicle initialize() 실패: ${describe(t)}")
        }
    }

    /** 각 값이 이 차(에뮬레이터)에서 지원되는지 확인한다. */
    fun checkCapabilities() {
        cap("Fuel.currentFuelEconomy") { ok, fail -> fuel.checkCurrentFuelEconomyCapabilities(ok, fail) }
        cap("Fuel.fuelPercent") { ok, fail -> fuel.checkFuelPercentCapabilities(ok, fail) }
        cap("Odometer.currentDistanceDriven") { ok, fail -> odometer.checkCurrentDistanceDrivenCapabilities(ok, fail) }
        cap("Odometer.tripCurrentDrivingDistance") { ok, fail -> odometer.checkTripCurrentDrivingDistanceCapabilities(ok, fail) }
        cap("Odometer.tripCurrentDrivingTime") { ok, fail -> odometer.checkTripCurrentDrivingTimeCapabilities(ok, fail) }
        cap("EvBattery.currentEvEfficiency") { ok, fail -> ev.checkCurrentEvEfficiencyCapabilities(ok, fail) }
        cap("EvBattery.stateOfCharge") { ok, fail -> ev.checkEvBatteryStateOfChargeCapabilities(ok, fail) }
    }

    /** 값을 한 번씩 읽는다. */
    fun readAll() {
        get<Float>("Fuel.currentFuelEconomy", "L/100km") { ok, fail -> fuel.getCurrentFuelEconomy(ok, fail) }
        get<Float>("Fuel.cumulativeFuelEconomy", "L/100km") { ok, fail -> fuel.getCumulativeFuelEconomy(ok, fail) }
        get<Float>("Fuel.sinceChargeFuelEconomy", "L/100km") { ok, fail -> fuel.getSinceChargeFuelEconomy(ok, fail) }
        get<Int>("Fuel.fuelPercent", "%") { ok, fail -> fuel.getFuelPercent(ok, fail) }
        get<Float>("Odometer.currentDistanceDriven", "km") { ok, fail -> odometer.getCurrentDistanceDriven(ok, fail) }
        get<Float>("Odometer.tripCurrentDrivingDistance", "m") { ok, fail -> odometer.getTripCurrentDrivingDistance(ok, fail) }
        get<Int>("Odometer.tripCurrentDrivingTime", "분") { ok, fail -> odometer.getTripCurrentDrivingTime(ok, fail) }
        get<Float>("EvBattery.currentEvEfficiency", "km/kWh") { ok, fail -> ev.getCurrentEvEfficiency(ok, fail) }
        get<Int>("EvBattery.stateOfCharge", "%") { ok, fail -> ev.getEvBatteryStateOfCharge(ok, fail) }
    }

    fun registerListeners() {
        runCatching { fuel.registerCurrentFuelEconomy(fuelListener) }.onFailure { log("등록 실패 currentFuelEconomy: ${describe(it)}") }
        runCatching { odometer.registerDistanceDriven(odoListener) }.onFailure { log("등록 실패 distanceDriven: ${describe(it)}") }
        runCatching { odometer.registerTripCurrentDrivingDistance(tripListener) }.onFailure { log("등록 실패 tripDistance: ${describe(it)}") }
    }

    fun release() {
        runCatching { fuel.unregisterCurrentFuelEconomy(fuelListener) }
        runCatching { odometer.unregisterDistanceDriven(odoListener) }
        runCatching { odometer.unregisterTripCurrentDrivingDistance(tripListener) }
        runCatching { vehicle.release() }
    }

    private fun cap(
        name: String,
        call: (onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    ) {
        try {
            call({ log("지원 $name = ${it.accessType}") }, { log("지원 $name 확인 실패: ${describe(it)}") })
        } catch (t: Throwable) {
            log("지원 $name 호출 예외: ${describe(t)}")
        }
    }

    private fun <T> get(
        name: String,
        unit: String,
        call: (onComplete: (T?) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    ) {
        try {
            call({ log("값 $name = $it $unit") }, { log("값 $name 실패: ${describe(it)}") })
        } catch (t: Throwable) {
            log("값 $name 호출 예외: ${describe(t)}")
        }
    }

    // VehicleException 클래스는 난독화되어 이름으로 참조할 수 없어 toString으로 남긴다(errorCode가 포함되는지 확인용).
    private fun describe(t: Throwable): String = "${t.javaClass.name}: $t"
}
