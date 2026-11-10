package ai.pleosapp.drive.core.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 까다로운 상황 가상주행으로 찾은 버그를 다시 생기지 않게 막는다 (2026-11-08 밤 디버그).
 * 시험 파일은 `spikes/vehicle-probe/tools/course_drive.py --dry-run`으로 만든 규격 ② 기록이다.
 *  - 02 하트 1,200m 지점부터 800m 터널(위치 없음, 속도는 있음)
 *  - 03 하트 500m에서 40초, 2,000m에서 60초 신호 대기 + GPS 흔들림 표준편차 4m
 *  - 04 하트를 거꾸로 한 바퀴
 *  - 10 8자 코스, 교차점에서 500m 터널
 */
class EdgeCaseTest {
    private val heart = ContractCodec.decodeCourse(TestGeo.resource("scenarios/test-heart.json"))
    private val figure8 = ContractCodec.decodeCourse(TestGeo.resource("scenarios/test-figure8.json"))

    private fun record(name: String): DriveRecord {
        val replay = ContractCodec.decodeReplay(TestGeo.resource("scenarios/$name.jsonl"))
        val session = DriveSession(DriveOrigin(DriveOrigin.Kind.REPLAY, replay.name, replay.synthetic))
        return (replay.signals.mapNotNull(session::handle).last() as SessionEvent.Finished).record
    }

    /** 버그: 정차 중 GPS 흔들림이 거리로 쌓여 3.83km 코스가 4.63km(+21%)로 기록됐다. */
    @Test fun gpsJitterWhileStoppedDoesNotAddDistance() {
        val r = record("03-heart-stops-noise")
        // 움직이는 중 흔들림(표준편차 4m)은 아직 조금 더해진다. 정차 100초분 약 700m가 빠졌는지 본다.
        assertTrue("거리 ${r.distanceM}", r.distanceM < heart.shapeLengthM * 1.08)
        assertEquals(238.0, r.movingMs / 1000.0, 2.0)
    }

    @Test fun stoppedJitterUnitCase() {
        val s = DriveSession(DriveOrigin.CAR)
        val base = TestGeo.at(0.0, 0.0)
        s.handle(DriveSignal.GearChange(0, Gear.DRIVE))
        s.handle(DriveSignal.Speed(0, 0.0))
        s.handle(DriveSignal.Position(0, base, 5.0))
        // 속도 0인 채 위치만 6~8m씩 왔다 갔다 한다.
        for (i in 1..60) {
            s.handle(DriveSignal.Speed(i * 1000L, 0.0))
            s.handle(DriveSignal.Position(i * 1000L, TestGeo.at(if (i % 2 == 0) 7.0 else -7.0, 0.0), 5.0))
        }
        assertEquals(0.0, s.liveDistanceM, 0.001)
    }

    /** 버그: 하트를 거꾸로 돌아도 시작점·꼭짓점에서 진행률이 건너뛰어 "완주"가 됐다. */
    @Test fun drivingTheCourseBackwardsIsNotAFinish() {
        val m = CourseMatcher.evaluate(heart, record("04-heart-reverse").points)
        assertFalse(m.finished)
        assertTrue("진행률 ${m.progressRatio}", m.progressRatio < 0.1)
    }

    /** 방향 확인을 넣은 뒤 터널 직후 "끊긴 앞뒤 점을 이은 방향" 때문에 진행이 멈추던 문제(고친 중간에 생김)를 막는다. */
    @Test fun tunnelStillFinishesAndCountsGapDistance() {
        val r = record("02-heart-tunnel")
        assertEquals(2, r.segments.size)
        assertEquals(800.0, r.gapDistanceM, 30.0)
        assertEquals(heart.shapeLengthM, r.distanceM, 40.0)
        val m = CourseMatcher.evaluate(heart, r.points)
        assertTrue(m.finished)
    }

    @Test fun figureEightWithTunnelAtTheCrossingFinishes() {
        val m = CourseMatcher.evaluate(figure8, record("10-figure8-tunnel-at-cross").points)
        assertTrue(m.finished)
        assertEquals(1.0, m.onCourseRatio!!, 0.01)
    }
}
