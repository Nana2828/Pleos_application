package ai.pleosapp.core

import ai.pleosapp.core.TestGeo.at
import ai.pleosapp.core.TestGeo.course
import ai.pleosapp.core.TestGeo.driveAlong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseMatcherTest {
    /** 규격 예시 고리 코스(출발점 = 도착점, 약 3km). */
    private val loop: Course = ContractCodec.decodeCourse(TestGeo.resource("contracts/course.json"))

    /** 동쪽 1km 뒤 북쪽 1km. 출발점과 도착점이 다르다. */
    private val elbow = course(listOf(at(0.0, 0.0), at(1000.0, 0.0), at(1000.0, 1000.0)))

    private fun TrackPoint.shift(ms: Long) = copy(timeMs = timeMs + ms)

    @Test fun fullLoopIsEnteredAndFinished() {
        assertTrue(loop.isLoop)
        val m = CourseMatcher.evaluate(loop, driveAlong(loop.shape, mps = 10.0))
        assertTrue(m.entered)
        assertTrue(m.finished)
        assertTrue("progress=${m.progressRatio}", m.progressRatio >= 0.95)
        assertEquals(1.0, m.onCourseRatio!!, 1e-9)
        assertTrue("coverage=${m.coverageRatio}", m.coverageRatio >= 0.95)
        assertTrue(m.finishedAtMs!! > m.enteredAtMs!!)
    }

    @Test fun loopIsNotFinishedRightAfterStart() {
        // 고리 코스는 출발점 = 도착점이라, 출발 직후에도 "도착점 반경 안"이다. 진행률 조건이 이를 막아야 한다.
        val tracker = CourseTracker(loop)
        val firstMinute = driveAlong(loop.shape, mps = 10.0).take(5) // 0~40m
        val events = firstMinute.mapNotNull { tracker.update(it.timeMs, it.point) }
        assertEquals(1, events.size)
        assertTrue(events[0] is CourseEvent.Entered)
        assertEquals(CourseTracker.Phase.ON_COURSE, tracker.phase)
        assertTrue(distanceM(firstMinute.last().point, loop.destination.point) <= CourseMatchParams().finishRadiusM)
    }

    @Test fun drivingTheLoopBackwardsNeverEnters() {
        val m = CourseMatcher.evaluate(loop, driveAlong(loop.shape.reversed(), mps = 10.0))
        assertFalse(m.entered)
        assertFalse(m.finished)
        assertNull(m.onCourseRatio)
        assertEquals(0.0, m.coverageRatio, 1e-9)
    }

    @Test fun joiningMidCourseWithoutPassingStartDoesNotEnter() {
        val fromMiddle = listOf(at(500.0, 0.0), at(1000.0, 0.0), at(1000.0, 1000.0))
        assertFalse(CourseMatcher.evaluate(elbow, driveAlong(fromMiddle, mps = 10.0)).entered)
    }

    @Test fun approachFromOutsideThenFollowEnters() {
        // 남쪽 300m에서 출발점까지 온 뒤 코스를 따라 달린다.
        val approach = listOf(at(0.0, -300.0), at(0.0, 0.0))
        val track = driveAlong(approach, 10.0) + driveAlong(elbow.shape, 10.0).drop(1).map { it.shift(31_000) }
        val m = CourseMatcher.evaluate(elbow, track)
        assertTrue(m.entered)
        assertTrue(m.finished)
        // 출발점에 오기 전의 접근 구간은 일치율에 넣지 않는다.
        assertEquals(1.0, m.onCourseRatio!!, 1e-9)
    }

    @Test fun enteredTimeIsWhenTheCarLeftTheStartArea() {
        val tracker = CourseTracker(elbow)
        var entered: CourseEvent.Entered? = null
        for (tp in driveAlong(elbow.shape, 10.0)) {
            val e = tracker.update(tp.timeMs, tp.point)
            if (e is CourseEvent.Entered) { entered = e; break }
        }
        assertNotNull(entered)
        // 약 30m 나아간 3~4초째 확정. 출발점 50m 안에 아직 있으니 통과 시각도 그 무렵이다.
        assertTrue("confirmed=${entered!!.confirmedAtMs}", entered.confirmedAtMs in 3_000L..4_000L)
        assertTrue(entered.timeMs <= entered.confirmedAtMs)
    }

    @Test fun shortcutFinishesButShowsLowCoverage() {
        // 출발점에서 도착점으로 대각선으로 바로 가면, 도착은 되지만 일치율·커버리지가 낮게 나온다.
        val diagonal = listOf(at(0.0, 0.0), at(1000.0, 1000.0))
        val m = CourseMatcher.evaluate(elbow, driveAlong(diagonal, 10.0))
        assertTrue(m.entered)
        assertTrue(m.finished)
        assertTrue("onCourse=${m.onCourseRatio}", m.onCourseRatio!! < 0.5)
        assertTrue("coverage=${m.coverageRatio}", m.coverageRatio < 0.3)
    }

    @Test fun stoppingHalfwayGivesHalfCoverage() {
        val half = listOf(at(0.0, 0.0), at(1000.0, 0.0))
        val m = CourseMatcher.evaluate(elbow, driveAlong(half, 10.0))
        assertTrue(m.entered)
        assertFalse(m.finished)
        assertEquals(0.5, m.progressRatio, 0.03)
        assertEquals(0.5, m.coverageRatio, 0.05)
    }

    @Test fun gpsGapInTunnelStillReachesFinish() {
        val straight = course(listOf(at(0.0, 0.0), at(3000.0, 0.0)))
        // 1000m~2000m 구간(100초) 위치가 끊긴다.
        val track = driveAlong(straight.shape, 10.0).filterNot { it.timeMs in 100_001L..199_999L }
        val m = CourseMatcher.evaluate(straight, track)
        assertTrue(m.finished)
        assertEquals(1.0, m.onCourseRatio!!, 1e-9)
    }

    @Test fun briefDetourKeepsProgressAndLowersOnCourseRatio() {
        val straight = course(listOf(at(0.0, 0.0), at(2000.0, 0.0)))
        val detour = listOf(at(0.0, 0.0), at(800.0, 0.0), at(900.0, 300.0), at(1100.0, 300.0), at(1200.0, 0.0), at(2000.0, 0.0))
        val m = CourseMatcher.evaluate(straight, driveAlong(detour, 10.0))
        assertTrue(m.finished)
        val ratio = m.onCourseRatio!!
        assertTrue("onCourse=$ratio", ratio in 0.6..0.95)
        assertTrue("coverage=${m.coverageRatio}", m.coverageRatio in 0.8..0.95)
    }

    @Test fun narrowCorridorFromCourseIsUsed() {
        val narrow = course(listOf(at(0.0, 0.0), at(1000.0, 0.0)), corridorM = 10.0)
        val offset20 = driveAlong(listOf(at(0.0, 0.0), at(0.0, 20.0), at(1000.0, 20.0)), 10.0)
        val m = CourseMatcher.evaluate(narrow, offset20)
        assertFalse("20m 옆길은 폭 10m 코스에 들어온 것이 아니다", m.entered)
    }

    @Test fun emptyTrackGivesNothing() {
        val m = CourseMatcher.evaluate(loop, emptyList())
        assertFalse(m.entered)
        assertNull(m.onCourseRatio)
        assertEquals(0.0, m.coverageRatio, 1e-9)
    }
}
