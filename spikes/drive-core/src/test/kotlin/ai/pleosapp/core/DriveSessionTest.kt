package ai.pleosapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 옛 시험 앱(route-draw)의 DriveSessionTest 14개를 옮기고, 차량 값(누적거리·배터리·차 효율) 시험을 더했다. */
class DriveSessionTest {
    /** 동쪽으로 metres만큼 떨어진 점. */
    private fun east(metres: Double) = GeoPoint(37.5, 127.0 + metres / (111_194.9 * 0.79335))

    private fun gear(t: Long, g: Gear) = DriveSignal.GearChange(t, g)
    private fun pos(t: Long, m: Double, acc: Double? = null) = DriveSignal.Position(t, east(m), acc)
    private fun speed(t: Long, kph: Double) = DriveSignal.Speed(t, kph)
    private fun session(limits: DriveLimits = DriveLimits()) = DriveSession(DriveOrigin.CAR, limits)

    /** 초속 10m로 seconds초 달리는 신호를 넣는다. */
    private fun DriveSession.drive(fromS: Int, seconds: Int, startM: Double = fromS * 10.0) {
        for (i in 0..seconds) {
            val t = (fromS + i) * 1000L
            handle(speed(t, 36.0))
            handle(pos(t, startM + i * 10.0))
        }
    }

    private fun finished(event: SessionEvent?): DriveRecord = (event as SessionEvent.Finished).record

    @Test fun driveStartsAndParkEnds() {
        val s = session()
        assertFalse(s.isRecording)
        assertEquals(SessionEvent.Started(0), s.handle(gear(0, Gear.DRIVE)))
        assertTrue(s.isRecording)
        s.drive(0, 100)
        val record = finished(s.handle(gear(100_000, Gear.PARK)))
        assertFalse(s.isRecording)
        assertEquals(0, record.startMs)
        assertEquals(100_000, record.endMs)
        assertEquals(1000.0, record.distanceM, 5.0)
        assertEquals(36.0, record.maxKph, 0.001)
        assertEquals(100_000, record.movingMs)
        assertEquals(1, record.segments.size)
    }

    @Test fun reverseAndNeutralDoNotEndOrStartADrive() {
        val s = session()
        assertNull(s.handle(gear(0, Gear.REVERSE)))
        assertNull(s.handle(gear(1, Gear.NEUTRAL)))
        assertFalse(s.isRecording)
        s.handle(gear(2, Gear.DRIVE))
        assertNull(s.handle(gear(3, Gear.REVERSE)))
        assertNull(s.handle(gear(4, Gear.NEUTRAL)))
        assertNull(s.handle(gear(5, Gear.UNKNOWN)))
        assertTrue(s.isRecording)
    }

    @Test fun repeatedDriveAndParkAreIgnored() {
        val s = session()
        assertNull(s.handle(gear(0, Gear.PARK)))
        s.handle(gear(1, Gear.DRIVE))
        assertNull(s.handle(gear(2, Gear.DRIVE)))
        assertEquals(1L, s.liveStartMs)
    }

    @Test fun shortDrivesAreDiscardedByDistanceOrTime() {
        val tooNear = session()
        tooNear.handle(gear(0, Gear.DRIVE))
        tooNear.drive(0, 20)
        assertTrue(tooNear.handle(gear(120_000, Gear.PARK)) is SessionEvent.Discarded)

        val tooQuick = session()
        tooQuick.handle(gear(0, Gear.DRIVE))
        tooQuick.drive(0, 50)
        assertTrue(tooQuick.handle(gear(50_000, Gear.PARK)) is SessionEvent.Discarded)
    }

    @Test fun positionsBeforeDriveSeedOnlyWhenFresh() {
        val fresh = session()
        fresh.handle(pos(0, 0.0))
        fresh.handle(gear(30_000, Gear.DRIVE))
        fresh.drive(30, 100, startM = 10.0)
        val record = finished(fresh.handle(gear(130_000, Gear.PARK)))
        assertEquals(east(0.0), record.segments[0][0].point)

        val stale = session()
        stale.handle(pos(0, 0.0))
        stale.handle(gear(120_000, Gear.DRIVE))
        stale.drive(120, 100, startM = 500.0)
        val record2 = finished(stale.handle(gear(220_000, Gear.PARK)))
        assertEquals(500.0, distanceM(east(0.0), record2.segments[0][0].point), 1.0)
    }

    @Test fun inaccurateAndImpossiblePositionsAreDropped() {
        val s = session()
        s.handle(gear(0, Gear.DRIVE))
        s.handle(pos(0, 0.0))
        s.handle(pos(1_000, 10.0))
        s.handle(pos(2_000, 5_000.0)) // 튄 값
        s.handle(pos(3_000, 30.0, acc = 500.0)) // 부정확
        s.drive(4, 100, startM = 40.0)
        val record = finished(s.handle(gear(104_000, Gear.PARK)))
        assertEquals(1040.0, record.distanceM, 5.0)
        assertEquals(1, record.segments.size)
    }

    @Test fun stationaryJitterDoesNotAddDistance() {
        val s = session(DriveLimits(minDistanceM = 0.0))
        s.handle(gear(0, Gear.DRIVE))
        for (i in 0..600) {
            s.handle(pos(i * 1000L, if (i % 2 == 0) 0.0 else 3.0))
        }
        val record = finished(s.handle(gear(601_000, Gear.PARK)))
        assertEquals(0.0, record.distanceM, 0.001)
    }

    @Test fun slowCrawlStillAccumulatesDistance() {
        val s = session(DriveLimits(minDistanceM = 0.0))
        s.handle(gear(0, Gear.DRIVE))
        for (i in 0..100) s.handle(pos(i * 1000L, i * 1.0)) // 초속 1m, 한 칸은 minStep보다 작다.
        val record = finished(s.handle(gear(100_000, Gear.PARK)))
        assertEquals(100.0, record.distanceM, 6.0)
    }

    @Test fun gpsGapSplitsLineAndCountsSpeedDistanceOnly() {
        val s = session()
        s.handle(gear(0, Gear.DRIVE))
        s.drive(0, 60)
        // 터널 120초: 위치 없이 속도만 들어온다. 실제로 간 거리는 1200m다.
        for (i in 61..180) s.handle(speed(i * 1000L, 36.0))
        s.drive(181, 60, startM = 1810.0)
        val record = finished(s.handle(gear(241_000, Gear.PARK)))
        assertEquals(2, record.segments.size)
        assertEquals(1200.0, record.gapDistanceM, 15.0)
        assertEquals(2400.0, record.distanceM, 30.0)
    }

    @Test fun gpsGapWithoutSpeedAddsNoGuessedDistance() {
        val s = session()
        s.handle(gear(0, Gear.DRIVE))
        for (i in 0..60) s.handle(pos(i * 1000L, i * 10.0))
        for (i in 181..241) s.handle(pos(i * 1000L, i * 10.0))
        val record = finished(s.handle(gear(241_000, Gear.PARK)))
        assertEquals(0.0, record.gapDistanceM, 0.001)
        assertEquals(1200.0, record.distanceM, 10.0)
    }

    @Test fun staleSpeedIsNotIntegrated() {
        val s = session(DriveLimits(minDistanceM = 0.0, minDurationMs = 0))
        s.handle(gear(0, Gear.DRIVE))
        s.handle(speed(0, 100.0))
        s.handle(speed(600_000, 100.0))
        val record = finished(s.handle(gear(600_001, Gear.PARK)))
        assertEquals(0, record.movingMs)
    }

    @Test fun impossibleSpeedDoesNotBecomeMax() {
        val s = session(DriveLimits(minDistanceM = 0.0, minDurationMs = 0))
        s.handle(gear(0, Gear.DRIVE))
        s.handle(speed(1_000, 80.0))
        s.handle(speed(2_000, 9_000.0))
        assertEquals(80.0, finished(s.handle(gear(3_000, Gear.PARK))).maxKph, 0.001)
    }

    @Test fun drivesAreIndependent() {
        val s = session()
        s.handle(gear(0, Gear.DRIVE))
        s.drive(0, 100)
        s.handle(gear(100_000, Gear.PARK))
        s.handle(gear(200_000, Gear.DRIVE))
        s.drive(200, 60, startM = 1000.0)
        val second = finished(s.handle(gear(260_000, Gear.PARK)))
        assertEquals(600.0, second.distanceM, 5.0)
        assertEquals(200_000, second.startMs)
    }

    @Test fun closeAtLastSignalUsesLastTimeAndMarksRecovered() {
        val s = session()
        s.handle(gear(0, Gear.DRIVE))
        s.drive(0, 100)
        val record = finished(s.closeAtLastSignal())
        assertEquals(100_000, record.endMs)
        assertTrue(record.recovered)
    }

    // ---- 아래는 새로 더한 차량 값 시험 ----

    @Test fun vehicleValuesAreSnapshottedAtStartAndEnd() {
        val s = session()
        s.handle(DriveSignal.Odometer(0, 1000.0))
        s.handle(DriveSignal.Battery(0, 70_000.0))
        s.handle(gear(1_000, Gear.DRIVE))
        s.drive(1, 100)
        s.handle(DriveSignal.Odometer(100_000, 1001.0))
        s.handle(DriveSignal.Battery(100_000, 69_800.0))
        val record = finished(s.handle(gear(101_000, Gear.PARK)))
        assertEquals(Efficiency.Snapshot(1000.0, 70_000.0, null), record.startSnapshot)
        assertEquals(Efficiency.Snapshot(1001.0, 69_800.0, null), record.endSnapshot)
        val eff = Efficiency.between(record.startSnapshot, record.endSnapshot, electric = true)!!
        assertEquals(5.0, eff.value, 1e-9)
    }

    @Test fun vehicleEfficiencyOnlyCountsWhileRecording() {
        val s = session()
        s.handle(DriveSignal.EvEfficiency(0, 99.0)) // 기록 전 값은 버린다.
        s.handle(gear(1_000, Gear.DRIVE))
        s.drive(1, 100)
        s.handle(DriveSignal.EvEfficiency(50_000, 6.1))
        s.handle(DriveSignal.EvEfficiency(90_000, 6.4))
        val record = finished(s.handle(gear(101_000, Gear.PARK)))
        assertEquals(6.4, record.vehicleKmPerKwh!!, 1e-9)
        assertNull(record.vehicleLitersPer100Km)

        // 다음 주행은 앞 주행의 값을 물려받지 않는다.
        s.handle(gear(200_000, Gear.DRIVE))
        s.drive(200, 100, startM = 1000.0)
        assertNull(finished(s.handle(gear(301_000, Gear.PARK))).vehicleKmPerKwh)
    }

    @Test fun nonFiniteVehicleValuesAreIgnored() {
        val s = session()
        s.handle(DriveSignal.Odometer(0, 1000.0))
        s.handle(gear(1_000, Gear.DRIVE))
        s.drive(1, 100)
        s.handle(DriveSignal.Odometer(100_000, Double.NaN))
        val record = finished(s.handle(gear(101_000, Gear.PARK)))
        assertEquals(1000.0, record.endSnapshot.odometerKm!!, 1e-9)
    }
}
