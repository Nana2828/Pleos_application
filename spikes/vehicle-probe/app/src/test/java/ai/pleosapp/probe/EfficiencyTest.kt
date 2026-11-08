package ai.pleosapp.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
