package ai.pleosapp.drive.ui.state

import ai.pleosapp.drive.core.course.DemoCourseFiles
import ai.pleosapp.drive.core.course.DemoCourseRepository
import ai.pleosapp.drive.core.drive.ContractCodec
import ai.pleosapp.drive.core.navi.DemoRouteCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시연 흐름 전체를 실제 assets/demo 데이터(코스 3개)로 재생해 본다. PLEOS-60, PLEOS-62 */
class DemoFlowEngineTest {

    private fun asset(name: String): String =
        checkNotNull(javaClass.classLoader.getResource("demo/$name")) { "assets/demo/$name 없음" }.readText(Charsets.UTF_8)

    private val repo by lazy { DemoCourseRepository { asset(it) } }
    private val data by lazy {
        repo.realCourses.associate { c ->
            c.id to DemoCourseData(
                DemoRouteCodec.decode(asset(DemoCourseFiles.route(c.id))),
                ContractCodec.decodeReplay(asset(DemoCourseFiles.drive(c.id))),
            )
        }
    }

    /** 2026-11-14 10:12 KST */
    private val wall = 1_794_618_720_000L

    private fun engine() = DemoFlowEngine(repo, data, wallClock = { wall })

    /** 지역을 입력하고 진짜 코스를 골라 안내를 시작한다. */
    private fun DemoFlowEngine.startDemo(query: String = "부천", courseId: String = DemoCourseFiles.CAT) {
        onQueryChange(query)
        onCourseChosen(courseId)
        onStartGuidance()
    }

    @Test fun cityShowsItsRealCourseAndTwoDummies() {
        val e = engine()
        val expected = mapOf(
            "부천" to ("고양이 코스" to DemoCourseFiles.CAT),
            "화성" to ("하트 코스" to DemoCourseFiles.HEART),
            "동두천" to ("강아지 코스" to DemoCourseFiles.DOG),
            "양주" to ("강아지 코스" to DemoCourseFiles.DOG),
        )
        for ((query, pair) in expected) {
            val (name, id) = pair
            e.onQueryChange(query)
            val courses = e.state.courses.courses
            assertEquals(query, listOf("A", "B", "C"), courses.map { it.label })
            assertEquals(query, listOf(true, false, false), courses.map { it.enabled })
            assertEquals(query, name, courses[0].name)
            assertEquals(query, id, e.state.courses.mapCourse?.id)
            // 더미는 진짜 코스와 모양이 겹치지 않는다
            assertFalse(query, courses.drop(1).any { it.name == name })
        }
    }

    @Test fun gyeonggiShowsOnlyTheThreeRealCourses() {
        val e = engine()
        e.onQueryChange("경기도")
        val courses = e.state.courses.courses
        assertEquals(listOf("고양이 코스", "하트 코스", "강아지 코스"), courses.map { it.name })
        assertEquals(listOf("A", "B", "C"), courses.map { it.label })
        assertTrue(courses.all { it.enabled })
        assertNull("경기도는 지도에 미리 띄우지 않는다", e.state.courses.mapCourse)

        e.onQueryChange("서울")
        assertTrue(e.state.courses.courses.isEmpty())
        assertNull(e.state.courses.mapCourse)
    }

    @Test fun dummyCourseDoesNothing() {
        val e = engine()
        e.onQueryChange("부천")
        val before = e.state
        val dummy = before.courses.courses.first { !it.enabled }
        e.onCourseChosen(dummy.id)
        e.onCoursePreview(dummy.id)
        assertEquals(before, e.state)
        assertEquals(DemoStep.REGION, e.state.step)
        assertNull(e.state.courses.errorMessage)
    }

    @Test fun realCourseOpensPreviewAndBackKeepsTheList() {
        val e = engine()
        e.onQueryChange("화성")
        e.onCourseChosen(DemoCourseFiles.HEART)
        assertEquals(DemoStep.PREVIEW, e.state.step)
        val p = e.state.preview
        assertTrue(p.canStartGuidance)
        assertEquals("화성시", p.place)
        assertEquals("화성 국도·고속도로 하트 코스", p.course?.title)
        assertEquals(45.3, p.distanceToStartKm!!, 1.0)
        assertTrue(p.courseRoute.size > 100)
        assertNotNull(p.startPoint)

        e.onPreviewBack()
        assertEquals(DemoStep.REGION, e.state.step)
        assertEquals("화성", e.state.region.query)
        assertEquals(DemoCourseFiles.HEART, e.state.courses.mapCourse?.id)
    }

    @Test fun cancelGuidanceReturnsToPreview() {
        val e = engine()
        e.startDemo()
        assertEquals(DemoStep.APPROACH, e.state.step)
        e.onCancelGuidance()
        assertEquals(DemoStep.PREVIEW, e.state.step)
        assertFalse(e.state.demo.playing)
        e.onStartGuidance()
        assertEquals(DemoStep.APPROACH, e.state.step)
    }

    /** 세 코스 모두: 시작점 안내 약 20초 → 주행 약 1분 → 결과 */
    @Test fun everyRealCourseRunsToResult() {
        val cases = listOf(
            Triple("부천", DemoCourseFiles.CAT, "부천시" to 50.5),
            Triple("화성", DemoCourseFiles.HEART, "화성시" to 65.5),
            Triple("동두천", DemoCourseFiles.DOG, "양주시" to 95.6),
        )
        for ((query, id, expect) in cases) {
            val (place, km) = expect
            val e = engine()
            e.startDemo(query, id)
            assertEquals(id, DemoStep.APPROACH, e.state.step)

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
                    else -> error("$id: 예상 못 한 단계 ${e.state.step}")
                }
                e.tick(tickMs)
            }
            assertEquals(id, DemoStep.RESULT, e.state.step)
            assertTrue(id, sawApproachTurn)
            assertTrue(id, sawTurn)
            assertEquals("$id 남은 거리가 늘어난 횟수", 0, remainingIncreases)
            // 배속은 코스마다 목표 시간(시작점 안내 20초, 주행 60초)에 맞춘다
            assertEquals("$id 시작점 안내 시간", 20.0, approachMs / 1000.0, 4.0)
            assertEquals("$id 주행 시간", 60.0, drivingMs / 1000.0, 6.0)

            val r = e.state.result
            assertEquals(id, place, r.regionName)
            // 접근 구간을 뺀 코스 구간만
            assertEquals(id, km, r.distanceKm, 1.5)
            assertEquals(r.durationSec * 1000, r.endedAtEpochMs - r.startedAtEpochMs)
            val eff = checkNotNull(r.efficiency)
            assertEquals(EfficiencyUnit.KM_PER_KWH, eff.unit)
            assertEquals(id, 6.0, eff.value, 0.3)
            assertEquals(UploadStatus.DONE, r.upload)
            assertNotNull(r.resultUrl)
            assertTrue(r.drivenPath.size > 100)

            val json = checkNotNull(e.state.demo.driveResultJson)
            val decoded = ContractCodec.decodeDriveResult(json)
            assertTrue(decoded.synthetic)
            assertEquals(id, decoded.courseId)
            assertEquals(r.distanceKm, decoded.distanceKm, 0.01)
            assertFalse(e.state.demo.playing)
            assertEquals(1.0, e.state.demo.courseProgress, 0.0)
        }
    }

    @Test fun drivingScreenShowsArrivalTimeLikeARealDrive() {
        val e = engine()
        e.startDemo()
        e.skipToCourseStart()
        assertEquals(DemoStep.DRIVING, e.state.step)
        e.tick(1_000)
        val d = e.state.driving
        val arrival = checkNotNull(d.arrivalAtEpochMs)
        // 가산에서 시작점까지 약 20분 + 코스 약 91분이므로 도착 예정은 안내 시작 후 100~130분 사이
        val minutes = (arrival - wall) / 60_000
        assertTrue("도착 예정 $minutes 분", minutes in 100..130)
        assertNotNull(d.remainingKm)
        assertTrue(d.drivenPath.isNotEmpty())
    }

    @Test fun skipControlsReachResult() {
        val e = engine()
        e.startDemo()
        e.skipToCourseStart()
        assertEquals(DemoStep.DRIVING, e.state.step)
        assertTrue(e.state.demo.speed >= 60.0)
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
        val base = e.state.demo.speed
        e.setBoost(2.0)
        assertEquals(base * 2, e.state.demo.speed, 0.001)
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
        for (course in repo.realCourses) {
            val shape = course.preview.map { MapPoint(it.lat, it.lon) }
            val frame = MapFrame.fit(shape)
            val pts = frame.toUnit(shape)
            assertTrue(course.id, pts.all { (x, y) -> x in 0f..1f && y in 0f..1f })
            // 북쪽(위도 큼)이 위(y 작음)
            val north = shape.maxBy { it.lat }
            val south = shape.minBy { it.lat }
            assertTrue(frame.toUnit(north).second < frame.toUnit(south).second)
        }
    }
}
