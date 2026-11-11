package ai.pleosapp.drive.ui.state

import ai.pleosapp.drive.core.course.DemoCourseFiles
import ai.pleosapp.drive.core.course.DemoCourseRepository
import ai.pleosapp.drive.core.drive.ContractCodec
import ai.pleosapp.drive.core.drive.DriveSignal
import ai.pleosapp.drive.core.drive.GeoPoint
import ai.pleosapp.drive.core.drive.ReplayPlayer
import ai.pleosapp.drive.core.drive.distanceM
import ai.pleosapp.drive.core.drive.pointToLineM
import ai.pleosapp.drive.core.navi.DemoRouteCodec
import ai.pleosapp.drive.core.navi.DemoStopsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 코스 주변 들를 곳: 이탈 지점에서 기록을 멈추고, 같은 지점으로 돌아오면 이어 간다. PLEOS-63 */
class DemoStopsTest {

    private fun asset(name: String): String =
        checkNotNull(javaClass.classLoader.getResource("demo/$name")) { "assets/demo/$name 없음" }.readText(Charsets.UTF_8)

    private fun hasAsset(name: String) = javaClass.classLoader.getResource("demo/$name") != null

    private val repo by lazy { DemoCourseRepository { asset(it) } }
    private val data by lazy {
        repo.realCourses.associate { c ->
            c.id to DemoCourseData(
                DemoRouteCodec.decode(asset(DemoCourseFiles.route(c.id))),
                ContractCodec.decodeReplay(asset(DemoCourseFiles.drive(c.id))),
                DemoCourseFiles.stops(c.id).takeIf(::hasAsset)?.let { DemoStopsCodec.decode(asset(it)) }.orEmpty(),
            )
        }
    }
    private val heartStops by lazy { data.getValue(DemoCourseFiles.HEART).stops }

    private val wall = 1_794_618_720_000L
    private fun engine() = DemoFlowEngine(repo, data, wallClock = { wall })

    private fun DemoFlowEngine.openHeart() {
        onQueryChange("화성")
        onCourseChosen(DemoCourseFiles.HEART)
    }

    /** 결과까지 달린다. 장소에 도착하면 [atPlace]를 부른다(기본: 코스로 돌아가기). 단계별 관찰은 [watch]로 */
    private fun DemoFlowEngine.runToResult(
        tickMs: Long = 50,
        atPlace: DemoFlowEngine.() -> Unit = { onReturnToCourse() },
        watch: (DrivingUiState) -> Unit = {},
    ) {
        var guard = 0
        while (state.step != DemoStep.RESULT && guard++ < 20_000) {
            if (state.step == DemoStep.DRIVING) {
                watch(state.driving)
                if (state.driving.stop?.phase == StopPhase.ARRIVED) atPlace()
            }
            tick(tickMs)
        }
        assertEquals(DemoStep.RESULT, state.step)
    }

    @Test fun stopsFileHasExitOnTheCourseAndRoundTripRoutes() {
        assertEquals(3, heartStops.size)
        val road = data.getValue(DemoCourseFiles.HEART).route.course.geometry
        assertEquals(heartStops.sortedBy { it.exitAtM }, heartStops)
        for (s in heartStops) {
            // 이탈 지점은 코스 위, 코스 앞뒤 끝은 아니다
            assertTrue(s.name, pointToLineM(s.exit, road) < 10)
            assertTrue(s.name, s.exitAtM > 1_000 && s.exitAtM < 65_536 - 1_000)
            // 가는 길은 이탈 지점에서 시작해 장소 근처에서 끝나고, 돌아오는 길은 장소에서 이탈 지점으로
            assertTrue(s.name, distanceM(s.outbound.geometry.first(), s.exit) < 30)
            assertTrue(s.name, distanceM(s.outbound.geometry.last(), s.location) < 100)
            assertTrue(s.name, distanceM(s.inbound.geometry.last(), s.exit) < 30)
            assertTrue(s.name, distanceM(s.outboundDrive.last().point, s.location) < 100)
            assertTrue(s.name, distanceM(s.inboundDrive.last().point, s.exit) < 30)
            assertTrue(s.name, s.extraDistanceM < 10_000)
        }
    }

    @Test fun onlyCoursesWithStopsShowThemInPreview() {
        val e = engine()
        e.openHeart()
        assertEquals(DemoStep.PREVIEW, e.state.step)
        assertEquals(heartStops.map { it.name }, e.state.preview.stops.map { it.name })
        assertTrue(e.state.preview.selectedStopIds.isEmpty())

        e.onPreviewBack()
        e.onQueryChange("부천")
        e.onCourseChosen(DemoCourseFiles.CAT)
        assertTrue(e.state.preview.stops.isEmpty())
    }

    @Test fun toggleSelectsAndUnselects() {
        val e = engine()
        e.openHeart()
        val id = heartStops[1].id
        e.onToggleStop(id)
        assertEquals(setOf(id), e.state.preview.selectedStopIds)
        e.onToggleStop("없는-장소")
        assertEquals(setOf(id), e.state.preview.selectedStopIds)
        e.onToggleStop(id)
        assertTrue(e.state.preview.selectedStopIds.isEmpty())
    }

    @Test fun detourPausesRecordingAndResultExcludesIt() {
        // 비교 기준: 들르지 않은 주행
        val plain = engine().apply { openHeart(); onStartGuidance(); runToResult() }.state.result

        val stop = heartStops.first { it.name == "화성식염온천" }
        val e = engine()
        e.openHeart()
        e.onToggleStop(stop.id)
        e.onStartGuidance()

        val phases = ArrayList<StopPhase?>()
        var frozenKm: Double? = null
        var frozenSec: Long? = null
        e.runToResult { d ->
            val phase = d.stop?.phase
            if (phases.lastOrNull() != phase) phases += phase
            when (phase) {
                StopPhase.GOING, StopPhase.ARRIVED, StopPhase.RETURNING -> {
                    assertFalse("들르는 동안 기록 멈춤", d.isRecording)
                    assertTrue(d.detourRoute.isNotEmpty())
                    // 기록 거리·시간이 멈춰 있다
                    if (frozenKm == null) {
                        frozenKm = d.drivenKm
                        frozenSec = d.elapsedSec
                    }
                    assertEquals(frozenKm!!, d.drivenKm, 0.0)
                    assertEquals(frozenSec, d.elapsedSec)
                    if (phase != StopPhase.ARRIVED) assertNotNull("장소·합류 지점까지 남은 거리", d.stop?.distanceM)
                }
                else -> {
                    assertTrue(d.isRecording)
                    assertTrue(d.detourRoute.isEmpty())
                }
            }
        }
        // 기록 중(null) → 예고 → 가는 중 → 도착 → 돌아오는 중 → 합류. 이 장소는 도착 1.7km 앞이라 합류 안내 중에 결과로 갈 수 있다
        assertEquals(
            listOf(StopPhase.UPCOMING, StopPhase.GOING, StopPhase.ARRIVED, StopPhase.RETURNING, StopPhase.RESUMED),
            phases.take(5),
        )

        val r = e.state.result
        assertEquals(listOf(stop.name), r.visitedStops)
        // 기록은 들르지 않은 주행과 같다(같은 코스 재생을 멈췄다 이어 갔으므로)
        assertEquals(plain.distanceKm, r.distanceKm, 0.05)
        assertEquals(plain.durationSec.toDouble(), r.durationSec.toDouble(), 2.0)
        assertEquals(plain.efficiency!!.value, r.efficiency!!.value, 0.05)
        // 시작·끝 시각에는 왕복 + 머문 시간(30분)이 들어 있다
        val roundTripSec = stop.outboundDrive.last().sec + stop.inboundDrive.last().sec
        assertEquals((roundTripSec + 30 * 60).toDouble(), r.pausedSec.toDouble(), 2.0)
        assertEquals(r.durationSec + r.pausedSec, (r.endedAtEpochMs - r.startedAtEpochMs) / 1000)
        assertEquals(plain.startedAtEpochMs, r.startedAtEpochMs)
        // 달린 길에는 장소 왕복이 없다
        assertTrue(r.drivenPath.all { p -> distanceM(GeoPoint(p.lat, p.lng), stop.location) > 300 })
    }

    @Test fun skipStopWhenItComesUp() {
        val e = engine()
        e.openHeart()
        heartStops.forEach { e.onToggleStop(it.id) }
        e.onStartGuidance()
        var skipped = 0
        e.runToResult { d ->
            if (d.stop?.phase == StopPhase.UPCOMING) {
                e.onSkipStop()
                skipped++
            }
            assertTrue(d.stop?.phase != StopPhase.GOING)
        }
        assertTrue(skipped >= 3)
        assertTrue(e.state.result.visitedStops.isEmpty())
        assertEquals(0L, e.state.result.pausedSec)
    }

    @Test fun visitsSeveralStopsInCourseOrder() {
        val e = engine()
        e.openHeart()
        heartStops.reversed().forEach { e.onToggleStop(it.id) }
        e.onStartGuidance()
        e.skipToCourseStart()
        e.runToResult()
        assertEquals(heartStops.map { it.name }, e.state.result.visitedStops)
        assertEquals(65.5, e.state.result.distanceKm, 1.5)
    }

    @Test fun nearFinishSkipStopsAtTheExit() {
        val e = engine()
        e.openHeart()
        e.onToggleStop(heartStops.last().id)
        e.onStartGuidance()
        e.skipToCourseStart()
        e.skipToNearFinish()
        // 이탈 지점이 95%보다 뒤라 건너뛰기는 95%에서 멈추고, 달리다 이탈 지점에서 빠진다
        var guard = 0
        while (e.state.driving.stop?.phase != StopPhase.GOING && guard++ < 2_000) e.tick(100)
        assertEquals(StopPhase.GOING, e.state.driving.stop?.phase)
        // 다녀오는 중에는 건너뛰기를 무시한다
        e.skipToNearFinish()
        assertEquals(StopPhase.GOING, e.state.driving.stop?.phase)
        e.onReturnToCourse() // 아직 도착 전이라 무시
        assertEquals(StopPhase.GOING, e.state.driving.stop?.phase)
        e.runToResult()
        assertEquals(1, e.state.result.visitedStops.size)
    }

    @Test fun upcomingNoticeSlowsPlaybackSoItCanBeRead() {
        val e = engine()
        e.openHeart()
        e.onToggleStop(heartStops.last().id)
        e.onStartGuidance()
        e.skipToCourseStart()
        val normal = e.state.demo.speed
        var noticeMs = 0L
        var slowest = Double.MAX_VALUE
        e.runToResult { d ->
            if (d.stop?.phase == StopPhase.UPCOMING) {
                noticeMs += 50
                slowest = minOf(slowest, e.state.demo.speed)
            }
        }
        assertEquals(normal / 4, slowest, 0.01)
        // 예고가 몇 초는 떠 있다 (늦추지 않으면 약 1.5초)
        assertTrue("예고 ${noticeMs}ms", noticeMs >= 4_000)
    }

    @Test fun replayPlayerStopsRightAfterTheSignal() {
        val p = ReplayPlayer((0..10).map { DriveSignal.Speed(it * 1000L, it.toDouble()) })
        val seen = ArrayList<Double>()
        p.seekTo(10_000, stopAfter = { seen.lastOrNull() == 4.0 }) { seen += (it as DriveSignal.Speed).kph }
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0), seen)
        assertEquals(4_000L, p.positionMs)
        p.seekTo(6_000) { seen += (it as DriveSignal.Speed).kph }
        assertEquals(6.0, seen.last(), 0.0)
    }
}
