package ai.pleosapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 시험 앱(spikes/vehicle-probe)의 EfficiencyTest 7개를 옮기고, 출처 고르기(resolve) 시험을 더했다. */
class EfficiencyTest {
    private fun snap(km: Double?, wh: Double? = null, ml: Double? = null) = Efficiency.Snapshot(km, wh, ml)

    @Test fun electricKmPerKwh() {
        val r = Efficiency.between(snap(1000.0, wh = 70_500.0), snap(1012.0, wh = 68_500.0), electric = true)!!
        assertEquals(6.0, r.value, 1e-9)
        assertEquals(Efficiency.Unit.KM_PER_KWH, r.unit)
        assertEquals(12.0, r.distanceKm, 1e-9)
    }

    @Test fun fuelKmPerLiter() {
        val r = Efficiency.between(snap(500.0, ml = 40_000.0), snap(530.0, ml = 38_000.0), electric = false)!!
        assertEquals(15.0, r.value, 1e-9)
        assertEquals(Efficiency.Unit.KM_PER_L, r.unit)
    }

    @Test fun shortDriveIsNotCalculated() {
        assertNull(Efficiency.between(snap(1000.0, wh = 70_000.0), snap(1000.3, wh = 69_000.0), electric = true))
    }

    @Test fun energyGainedIsNotCalculated() {
        // 회생제동·충전으로 에너지가 늘면 효율을 만들지 않는다.
        assertNull(Efficiency.between(snap(1000.0, wh = 70_000.0), snap(1010.0, wh = 70_200.0), electric = true))
    }

    @Test fun missingValueIsNotCalculated() {
        assertNull(Efficiency.between(snap(null, wh = 70_000.0), snap(1010.0, wh = 69_000.0), electric = true))
        assertNull(Efficiency.between(snap(1000.0, wh = null), snap(1010.0, wh = 69_000.0), electric = true))
        assertNull(Efficiency.between(snap(1000.0, ml = 1_000.0), snap(1010.0, ml = Double.NaN), electric = false))
    }

    @Test fun odometerRollbackIsNotCalculated() {
        assertNull(Efficiency.between(snap(1010.0, wh = 70_000.0), snap(1000.0, wh = 69_000.0), electric = true))
    }

    @Test fun litersPer100KmConversion() {
        assertEquals(12.5, Efficiency.kmPerLiterFromLitersPer100Km(8.0)!!, 1e-9)
        assertNull(Efficiency.kmPerLiterFromLitersPer100Km(0.0))
        assertNull(Efficiency.kmPerLiterFromLitersPer100Km(Double.NaN))
    }

    // ---- 출처 고르기 ----

    private val evStart = snap(1000.0, wh = 70_000.0)
    private val evEnd = snap(1012.0, wh = 68_000.0)

    @Test fun vehicleValueWinsOverEstimate() {
        val r = Efficiency.resolve(electric = true, start = evStart, end = evEnd, vehicleKmPerKwh = 6.4)!!
        assertEquals(6.4, r.value, 1e-9)
        assertEquals(Efficiency.Source.VEHICLE_SDK, r.source)

        val fuel = Efficiency.resolve(electric = false, start = snap(null), end = snap(null), vehicleLitersPer100Km = 8.0)!!
        assertEquals(12.5, fuel.value, 1e-9)
        assertEquals(Efficiency.Unit.KM_PER_L, fuel.unit)
        assertEquals(Efficiency.Source.VEHICLE_SDK, fuel.source)
    }

    @Test fun estimateIsUsedWhenVehicleValueMissingOrBroken() {
        for (broken in listOf(null, 0.0, -1.0, Double.NaN)) {
            val r = Efficiency.resolve(electric = true, start = evStart, end = evEnd, vehicleKmPerKwh = broken)!!
            assertEquals(6.0, r.value, 1e-9)
            assertEquals(Efficiency.Source.ESTIMATED, r.source)
        }
    }

    @Test fun wrongKindOfVehicleValueIsIgnored() {
        // 전기차인데 연비(L/100km)만 왔으면 쓰지 않는다.
        val r = Efficiency.resolve(electric = true, start = evStart, end = evEnd, vehicleLitersPer100Km = 8.0)!!
        assertEquals(Efficiency.Source.ESTIMATED, r.source)
    }

    @Test fun nothingAvailableGivesNull() {
        assertNull(Efficiency.resolve(electric = true, start = snap(null), end = snap(null)))
    }

    @Test fun sampleIsMarkedAsSample() {
        val r = Efficiency.sample(5.5, electric = true)
        assertEquals(Efficiency.Source.SAMPLE, r.source)
        assertEquals("sample", r.source.code)
        assertEquals(Efficiency.Unit.KM_PER_KWH, r.unit)
    }
}
