package ai.pleosapp.drive.core.navi

import ai.pleosapp.drive.core.drive.ContractCodec
import ai.pleosapp.drive.core.drive.DriveSignal
import ai.pleosapp.drive.core.drive.GeoPoint
import ai.pleosapp.drive.core.drive.distanceM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceTrackerTest {

    private val route = DemoRouteCodec.decode(DemoAssets.text("bucheon-cat-91min.route.json"))
    private val positions: List<GeoPoint> = ContractCodec.decodeReplay(DemoAssets.text("bucheon-cat-91min.drive.jsonl"))
        .signals.filterIsInstance<DriveSignal.Position>().map { it.point }

    private data class Run(
        val approach: List<GuidanceSnapshot>,
        val course: List<GuidanceSnapshot>,
    )

    /** 접근 구간이 거의 끝나면(남은 거리 30m 미만) 코스 안내로 바꾼다. */
    private fun run(points: List<GeoPoint>): Run {
        val approachTracker = GuidanceTracker(route.approach)
        val courseTracker = GuidanceTracker(route.course)
        val a = ArrayList<GuidanceSnapshot>()
        val c = ArrayList<GuidanceSnapshot>()
        var onCourse = false
        for (p in points) {
            if (!onCourse) {
                val s = approachTracker.update(p)
                a += s
                // 코스 시작점 30m 앞이면 바꾼다. 드문 위치(프레임 건너뜀)에서는 시작점을 지나친 뒤 끝에 붙어서 바뀐다.
                if (s.remainingM < 30.0) onCourse = true
            } else {
                c += courseTracker.update(p)
            }
        }
        return Run(a, c)
    }

    private fun assertMonotonic(list: List<GuidanceSnapshot>) {
        list.zipWithNext().forEach { (x, y) -> assertTrue("진행 거리가 줄었다: ${x.progressM} → ${y.progressM}", y.progressM >= x.progressM) }
    }

    @Test fun denseReplayFollowsApproachAndCourse() {
        val run = run(positions)
        assertTrue("코스 구간에 들어가지 못했다", run.course.size > 1000)

        // 접근 구간: 끝까지 와서 바뀐다
        assertMonotonic(run.approach)
        assertTrue("접근 마지막 남은 거리 ${run.approach.last().remainingM}", run.approach.last().remainingM < 30.0)
        assertTrue("접근 끝 직전 안내 ${run.approach[run.approach.size - 2].next}", run.approach[run.approach.size - 2].next?.kind == ManeuverKind.ARRIVE)

        // 코스 구간
        val course = run.course
        assertMonotonic(course)
        assertTrue("코스 시작은 0 근처여야 한다(고리 끝에 붙으면 안 됨): ${course.first().progressM}", course.first().progressM < 100.0)
        assertTrue("마지막 남은 거리: ${course.last().remainingM}", course.last().remainingM < 60.0)
        course.zipWithNext().forEach { (x, y) ->
            assertTrue("1초에 너무 많이 뛰었다: ${x.progressM} → ${y.progressM}", y.progressM - x.progressM <= 600.0)
        }
        val off = course.map { it.offRouteM }.sorted()
        assertTrue("경로와 떨어진 거리 중앙값: ${off[off.size / 2]}", off[off.size / 2] < 15.0)
        course.forEach { s -> s.distanceToNextM?.let { assertTrue(it >= 0.0) } }

        // 남은 시간은 줄어들기만 하고 처음에는 OSRM 시간(약 91분)에 가깝다
        course.zipWithNext().forEach { (x, y) -> assertTrue("남은 시간 ${x.remainingSec} → ${y.remainingSec}", y.remainingSec <= x.remainingSec) }
        assertTrue("처음 남은 시간 ${course.first().remainingSec}", course.first().remainingSec in 80L * 60..100L * 60)
    }

    @Test fun uTurnsArePassedInOrderWithoutJumping() {
        val course = run(positions).course
        val uTurns = route.course.steps.filter { it.modifier == "uturn" }.map { it.atM }
        assertEquals(3, uTurns.size)
        for (at in uTurns) {
            // 유턴 지점이 "다음 안내"로 나온 적이 있고, 지나고 나면 다음 안내가 그 뒤로 넘어간다.
            val shownAt = course.indexOfFirst { it.next?.kind == ManeuverKind.U_TURN && it.next.atM == at }
            assertTrue("유턴($at)이 다음 안내로 나오지 않았다", shownAt >= 0)
            val passedAt = course.indexOfFirst { it.progressM > at + 1.0 }
            assertTrue(passedAt > shownAt)
            assertTrue(course[passedAt].next!!.atM > at)
            // 유턴 앞뒤에서 돌아오는 쪽 도로로 먼저 건너뛰지 않는다(1초 진행 ≤ 60m)
            course.zipWithNext().filter { (x, _) -> x.progressM in (at - 500)..(at + 500) }.forEach { (x, y) ->
                assertTrue("유턴($at) 근처에서 ${x.progressM} → ${y.progressM}", y.progressM - x.progressM <= 60.0)
            }
        }
    }

    @Test fun everyAnnouncedManeuverAppearsAsNextInOrder() {
        val course = run(positions).course
        val seen = course.mapNotNull { it.next?.atM }.distinct()
        assertEquals("다음 안내 위치는 앞으로만 간다", seen.sorted(), seen)
        val expected = route.course.steps.mapNotNull(ManeuverText::of).map { it.atM }.filter { it > 100.0 }
        val missing = expected.filter { it !in seen }
        assertTrue("다음 안내로 한 번도 안 나온 안내: $missing", missing.isEmpty())
    }

    @Test fun sparseReplayStillFollows() {
        val dense = run(positions)
        val idx = (positions.indices step 60).toList().let { if (it.last() != positions.lastIndex) it + positions.lastIndex else it }
        val sparse = run(idx.map { positions[it] })
        assertMonotonic(sparse.approach)
        assertMonotonic(sparse.course)
        assertTrue("드문 위치로도 코스 구간에 들어가야 한다", sparse.course.isNotEmpty())
        assertTrue(sparse.course.last().remainingM < 60.0)
        // 같은 위치에서 촘촘한 실행과 비슷한 진행 거리.
        // 방향을 모르는 드문 위치에서는 유턴 왕복 도로에서 일부러 먼저 지나는 쪽(뒤쪽)을 고르므로,
        // 왕복 길이(이 코스 세 번째 유턴은 약 350m × 2)만큼 늦을 수 있다. 앞질러 가는 일은 없어야 한다.
        val denseByPoint = HashMap<GeoPoint, Double>()
        val switch = dense.approach.size
        dense.course.forEachIndexed { i, s -> denseByPoint[positions[switch + i]] = s.progressM }
        var compared = 0
        val sparseSwitch = sparse.approach.size
        sparse.course.forEachIndexed { i, s ->
            val d = denseByPoint[positions[idx[sparseSwitch + i]]] ?: return@forEachIndexed
            compared++
            assertTrue("드문 실행이 앞질렀다 ${s.progressM} vs 촘촘한 실행 $d", s.progressM - d <= 100.0)
            assertTrue("드문 실행이 너무 늦다 ${s.progressM} vs 촘촘한 실행 $d", d - s.progressM <= 900.0)
        }
        assertTrue(compared > 50)
    }

    @Test fun loopStartMatchesBeginningNotEnd() {
        val tracker = GuidanceTracker(route.course)
        val s = tracker.update(route.course.geometry.first())
        assertTrue(s.progressM < 1.0)
        assertTrue(s.remainingM > route.course.distanceM - 50)
        assertNotNull(s.next)
    }

    @Test fun resetStartsOver() {
        val tracker = GuidanceTracker(route.approach)
        route.approach.geometry.forEach { tracker.update(it) }
        tracker.update(route.approach.geometry.last()).let { assertTrue("끝 남은 거리 ${it.remainingM}", it.remainingM < 1.0) }
        tracker.reset()
        assertTrue(tracker.update(route.approach.geometry.first()).progressM < 1.0)
    }

    @Test fun offRoutePointDoesNotMoveProgressBackwardOrFar() {
        val tracker = GuidanceTracker(route.approach)
        val g = route.approach.geometry
        val mid = g[g.size / 2]
        g.take(g.size / 2 + 1).forEach { tracker.update(it) }
        val before = tracker.update(mid).progressM
        // 1km 떨어진 엉뚱한 점
        val far = GeoPoint(mid.lat + 0.009, mid.lon)
        val s = tracker.update(far)
        assertTrue(s.progressM >= before)
        assertTrue(s.offRouteM > 100.0)
    }
}
