package ai.pleosapp.drive.ui.state

import ai.pleosapp.drive.core.course.DemoCourseRepository
import ai.pleosapp.drive.core.drive.ContractCodec
import ai.pleosapp.drive.core.navi.DemoRouteCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시연 흐름 전체를 실제 assets/demo 데이터로 재생해 본다. */
class DemoFlowEngineTest {

    private fun asset(name: String): String =
        checkNotNull(javaClass.classLoader.getResource("demo/$name")) { "assets/demo/$name 없음" }.readText(Charsets.UTF_8)

    private val repo by lazy { DemoCourseRepository(asset("bucheon-cat-91min.json")) }
    private val route by lazy { DemoRouteCodec.decode(asset("demo-route.json")) }
    private val replay by lazy { ContractCodec.decodeReplay(asset("demo-drive.jsonl")) }

    /** 2026-11-14 10:12 KST */
    private val wall = 1_794_618_720_000L

    private fun engine() = DemoFlowEngine(repo, route, replay, wallClock = { wall })

    /** 정해진 선택지로 시작점 안내까지 간다. */
    private fun DemoFlowEngine.startDemo() {
        onQueryChange("경기도")
        onCourseChosen(repo.demoCourse.id)
        onLengthConfirmed()
        onStartGuidance()
    }

    @Test fun regionSearchShowsCatAndDisabledDummies() {
        val e = engine()
        e.onQueryChange("부천")
        val courses = e.state.courses.courses
        assertEquals(listOf("A", "B", "C"), courses.map { it.label })
        assertEquals(listOf(true, false, false), courses.map { it.enabled })
        assertEquals("고양이 코스", courses[0].name)
        e.onQueryChange("서울")
        assertTrue(e.state.courses.courses.isEmpty())
    }

    @Test fun disabledCourseAndShortLengthCannotBeChosen() {
        val e = engine()
        e.onQueryChange("경기도")
        e.onCourseChosen("demo-heart")
        assertEquals(DemoStep.REGION, e.state.step)
        assertNotNull(e.state.courses.errorMessage)

        e.onCourseChosen(repo.demoCourse.id)
        assertEquals(DemoStep.LENGTH, e.state.step)
        val options = e.state.length.options
        assertEquals(listOf("짧은 코스", "긴 코스"), options.map { it.title })
        assertEquals(repo.demoCourse.id, e.state.length.selectedId)
        e.onLengthSelected(options[0].id)
        assertEquals(repo.demoCourse.id, e.state.length.selectedId)

        e.onLengthConfirmed()
        assertEquals(DemoStep.CONFIRM, e.state.step)
        assertTrue(e.state.confirm.canStartGuidance)
        e.onCancel()
        assertEquals(DemoStep.LENGTH, e.state.step)
    }

    @Test fun fullDemoRunsApproachThenAboutOneMinuteDriveThenResult() {
        val e = engine()
        e.startDemo()
        assertEquals(DemoStep.APPROACH, e.state.step)
        assertEquals(3.2, e.state.approach.distanceToStartKm!!, 0.1)

        val tickMs = 50L
        var approachMs = 0L
        var drivingMs = 0L
        var sawTurn = false
        var sawApproachTurn = false
        var lastRemaining = Double.MAX_VALUE
        var remainingIncreases = 0
        var guard = 0
        while (e.state.step != DemoStep.RESULT && guard++ < 10_000) {
            when (e.state.step) {
                DemoStep.APPROACH -> {
                    approachMs += tickMs
                    if (e.state.approach.nextTurn != null) sawApproachTurn = true
                }
                DemoStep.DRIVING -> {
                    drivingMs += tickMs
                    val d = e.state.driving
                    assertTrue(d.isRecording)
                    if (d.nextTurn != null) sawTurn = true
                    d.remainingKm?.let {
                        if (it > lastRemaining + 0.05) remainingIncreases++
                        lastRemaining = it
                    }
                }
                else -> error("예상 못 한 단계 ${e.state.step}")
            }
            e.tick(tickMs)
        }
        assertEquals(DemoStep.RESULT, e.state.step)
        assertTrue(sawApproachTurn)
        assertTrue(sawTurn)
        assertEquals("남은 거리가 늘어난 횟수", 0, remainingIncreases)
        // 시작점 안내 약 14초, 주행 약 1분 (배속 30·90)
        assertEquals(14.0, approachMs / 1000.0, 3.0)
        assertEquals(61.0, drivingMs / 1000.0, 5.0)

        val r = e.state.result
        assertEquals("부천시", r.regionName)
        // 접근 구간 3.2km를 뺀 코스 구간만
        assertEquals(50.5, r.distanceKm, 1.0)
        assertEquals(r.durationSec * 1000, r.endedAtEpochMs - r.startedAtEpochMs)
        assertTrue(r.durationSec in 85 * 60L..100 * 60L)
        val eff = checkNotNull(r.efficiency)
        assertEquals(EfficiencyUnit.KM_PER_KWH, eff.unit)
        assertEquals(6.0, eff.value, 0.3)
        assertEquals(UploadStatus.DONE, r.upload)
        assertNotNull(r.resultUrl)
        assertTrue(r.drivenPath.size > 100)

        val json = checkNotNull(e.state.demo.driveResultJson)
        val decoded = ContractCodec.decodeDriveResult(json)
        assertTrue(decoded.synthetic)
        assertEquals(repo.demoCourse.id, decoded.courseId)
        assertEquals(r.distanceKm, decoded.distanceKm, 0.01)
        assertFalse(e.state.demo.playing)
        assertEquals(1.0, e.state.demo.courseProgress, 0.0)
    }

    @Test fun drivingScreenShowsArrivalTimeLikeARealDrive() {
        val e = engine()
        e.startDemo()
        e.skipToCourseStart()
        assertEquals(DemoStep.DRIVING, e.state.step)
        e.tick(1_000)
        val d = e.state.driving
        val arrival = checkNotNull(d.arrivalAtEpochMs)
        // 코스가 약 91분이므로 도착 예정은 안내 시작 후 80~110분 사이
        val minutes = (arrival - wall) / 60_000
        assertTrue("도착 예정 $minutes 분", minutes in 80..110)
        assertNotNull(d.remainingKm)
        assertTrue(d.drivenPath.isNotEmpty())
    }

    @Test fun skipControlsReachResult() {
        val e = engine()
        e.startDemo()
        e.skipToCourseStart()
        assertEquals(DemoStep.DRIVING, e.state.step)
        assertTrue(e.state.demo.speed >= 90.0)
        e.skipToNearFinish()
        assertTrue(e.state.demo.courseProgress >= 0.95)
        var guard = 0
        while (e.state.step != DemoStep.RESULT && guard++ < 2_000) e.tick(100)
        assertEquals(DemoStep.RESULT, e.state.step)
        assertEquals(50.5, e.state.result.distanceKm, 1.0)
    }

    @Test fun boostPauseAndRestart() {
        val e = engine()
        e.startDemo()
        e.setBoost(2.0)
        assertEquals(60.0, e.state.demo.speed, 0.001)
        e.setPlaying(false)
        val before = e.state.demo.scriptSec
        e.tick(5_000)
        assertEquals(before, e.state.demo.scriptSec)
        e.setPlaying(true)
        e.tick(1_000)
        assertTrue(e.state.demo.scriptSec > before)
        e.restart()
        assertEquals(DemoStep.REGION, e.state.step)
        assertEquals("", e.state.region.query)
        assertNull(e.state.demo.driveResultJson)
    }

    @Test fun mapFrameFitsCourseIntoUnitBox() {
        val shape = repo.demoCourse.preview.map { MapPoint(it.lat, it.lon) }
        val frame = MapFrame.fit(shape)
        val pts = frame.toUnit(shape)
        assertTrue(pts.all { (x, y) -> x in 0f..1f && y in 0f..1f })
        // 북쪽(위도 큼)이 위(y 작음)
        val north = shape.maxBy { it.lat }
        val south = shape.minBy { it.lat }
        assertTrue(frame.toUnit(north).second < frame.toUnit(south).second)
    }
}
