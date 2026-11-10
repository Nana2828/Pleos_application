package ai.pleosapp.drive.core.drive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.floor

/**
 * 규격 ② 주행 신호 → 주행 세션 → 코스 판정·효율 → 규격 ③ 주행 결과 JSON까지 한 번에 지나가 본다.
 * 앱에서 "가상주행 → 결과 화면·서버 전송"이 이 순서로 이어진다.
 */
class IntegrationTest {
    private val course = ContractCodec.decodeCourse(TestGeo.resource("contracts/course.json"))
    private val baseMs = Instant.parse("2026-11-08T05:03:00Z").toEpochMilli() // 한국 시간 14:03:00

    /** 규격 ③에 있는 필드 전부(전기차). 내연기관이면 avg_ev_efficiency만 빠진다. */
    private val evFields = setOf(
        "schema", "course_id", "started_at", "ended_at", "duration_sec", "moving_sec", "distance_km",
        "energy_type", "avg_fuel_economy", "avg_ev_efficiency", "fuel_economy_source", "max_kph",
        "on_course_ratio", "polyline", "record_source", "synthetic", "app_version",
    )

    private fun run(replay: ReplayScript, limits: DriveLimits = DriveLimits()): DriveRecord {
        val session = DriveSession(DriveOrigin(DriveOrigin.Kind.REPLAY, replay.name, replay.synthetic), limits)
        val events = replay.signals.mapNotNull(session::handle)
        return (events.last() as SessionEvent.Finished).record
    }

    @Test fun sampleSignalsBecomeAResultWithEveryContractField() {
        val replay = ContractCodec.decodeReplay(TestGeo.resource("contracts/drive-signals.jsonl"))
        // 예시 파일은 5초짜리라 기본 기준이면 "너무 짧은 주행"으로 버려진다. 형식 확인용으로 기준을 푼다.
        val record = run(replay, DriveLimits(minDistanceM = 0.0, minDurationMs = 0))
        val result = DriveResult.from(record, EnergyType.EV, "0.1.0", course = course, clockOffsetMs = baseMs)
        val json = Json.parseToJsonElement(ContractCodec.encodeDriveResult(result)).jsonObject

        assertEquals(evFields, json.keys)
        assertEquals("drive-result/1", json.str("schema"))
        assertEquals("sample-loop", json.str("course_id"))
        assertEquals("2026-11-08T14:03:03+09:00", json.str("started_at"))
        assertEquals("2026-11-08T14:03:08+09:00", json.str("ended_at"))
        assertEquals("replay", json.str("record_source"))
        assertEquals("true", json["synthetic"]!!.jsonPrimitive.content)
        // 누적거리가 그대로라 효율을 못 구한다 → 값과 출처 모두 null. 전기차라 연비는 항상 null.
        assertEquals(JsonNull, json["avg_ev_efficiency"])
        assertEquals(JsonNull, json["fuel_economy_source"])
        assertEquals(JsonNull, json["avg_fuel_economy"])
        // 30m도 못 가서 코스 진입 전이다.
        assertEquals(JsonNull, json["on_course_ratio"])
    }

    @Test fun fullLoopVirtualDriveGivesEfficiencyAndCourseRatio() {
        val text = loopReplayText(kmPerKwh = 6.0)
        val replay = ContractCodec.decodeReplay(text)
        val record = run(replay)
        val result = DriveResult.from(record, EnergyType.EV, "0.1.0", course = course, clockOffsetMs = baseMs)
        val json = Json.parseToJsonElement(ContractCodec.encodeDriveResult(result, prettyPrint = true)).jsonObject

        assertEquals(evFields, json.keys)
        assertEquals("2026-11-08T14:03:02+09:00", json.str("started_at"))
        val distanceKm = json.num("distance_km")
        assertEquals(course.shapeLengthM / 1000, distanceKm, course.shapeLengthM / 1000 * 0.03)
        // 누적거리는 0.1km 단위로만 오므로 6.0에서 조금 벗어날 수 있다.
        assertEquals(6.0, json.num("avg_ev_efficiency"), 0.3)
        assertEquals("estimated", json.str("fuel_economy_source"))
        assertTrue(json.num("on_course_ratio") >= 0.95)
        assertEquals(36.0, json.num("max_kph"), 1e-9)

        val coords = json["polyline"]!!.jsonObject["coordinates"]!!.jsonArray.map {
            val pair = it.jsonArray
            GeoPoint(pair[1].jsonPrimitive.content.toDouble(), pair[0].jsonPrimitive.content.toDouble())
        }
        assertEquals(course.start.point.lat, coords.first().lat, 1e-4)
        coords.zipWithNext { a, b -> assertTrue(distanceM(a, b) >= DriveResult.POLYLINE_SPACING_M - 0.5) }
        assertTrue("궤적이 줄어들어야 한다", coords.size < record.points.size)
    }

    @Test fun fuelCarResultMatchesSampleFieldSet() {
        val text = loopReplayText(kmPerKwh = 6.0)
        val record = run(ContractCodec.decodeReplay(text))
        val sampleEff = Efficiency.sample(13.5, electric = false)
        val result = DriveResult.from(record, EnergyType.FUEL, "0.1.0", course = course, efficiency = sampleEff, clockOffsetMs = baseMs)
        val json = Json.parseToJsonElement(ContractCodec.encodeDriveResult(result)).jsonObject
        val sampleKeys = Json.parseToJsonElement(TestGeo.resource("contracts/drive-result.json")).jsonObject.keys
        assertEquals(sampleKeys, json.keys)
        assertEquals(13.5, json.num("avg_fuel_economy"), 1e-9)
        assertEquals("sample", json.str("fuel_economy_source"))
    }

    /**
     * 에뮬레이터 가상 콘솔로 실제로 달린 기록(2026-11-08, 규격 ② v2).
     * 출발점 서남서쪽 300m 밖에서 직선으로 들어와 고리를 한 바퀴 돈 뒤 출발점에서 P. 50km/h, 꺾이는 곳 25km/h.
     */
    @Test fun emulatorRecordingApproachThenLoop() {
        val replay = ContractCodec.decodeReplay(TestGeo.resource("emulator-sample-loop-approach300.jsonl"))
        val record = run(replay)
        val match = CourseMatcher.evaluate(course, record.points)
        val result = DriveResult.from(record, EnergyType.EV, "0.1.0", course = course, match = match, clockOffsetMs = baseMs)
        val json = Json.parseToJsonElement(ContractCodec.encodeDriveResult(result)).jsonObject

        val enteredAfterS = (match.enteredAtMs!! - record.startMs) / 1000.0
        val enteredPoint = record.points.first { it.timeMs >= match.enteredAtMs!! }.point
        val summary = "entered_after_s=$enteredAfterS entered_dist_to_start_m=${"%.1f".format(distanceM(enteredPoint, course.start.point))} " +
            "finished=${match.finished} progress=${"%.3f".format(match.progressRatio)} on_course=${"%.3f".format(match.onCourseRatio)} " +
            "coverage=${"%.3f".format(match.coverageRatio)} distance_km=${"%.3f".format(record.distanceM / 1000)} " +
            "duration_s=${record.durationMs / 1000} moving_s=${record.movingMs / 1000} max_kph=${record.maxKph} " +
            "eff=${json.num("avg_ev_efficiency")} source=${json.str("fuel_economy_source")} " +
            "points=${record.points.size} polyline=${result.polyline.size}"
        println(summary)

        assertEquals(evFields, json.keys)
        // 진입은 300m 접근(50km/h로 약 22초)을 지나 출발점 근처에서 판정돼야 한다.
        assertTrue(summary, enteredAfterS >= 15.0)
        assertTrue(summary, distanceM(enteredPoint, course.start.point) <= CourseMatchParams().startRadiusM)
        assertTrue(summary, match.finished)
        assertTrue(summary, match.onCourseRatio!! >= 0.9)
        assertTrue(summary, match.coverageRatio >= 0.9)
        // 누적거리 1007.8 → 1011.1km(3.3km), 배터리 70000.0 → 69457.2Wh(0.5428kWh) → 약 6.08km/kWh
        assertEquals(summary, 3.3 / 0.5428, json.num("avg_ev_efficiency"), 0.01)
        assertEquals("estimated", json.str("fuel_economy_source"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun efficiencyUnitMustMatchEnergyType() {
        val record = run(ContractCodec.decodeReplay(loopReplayText(6.0)))
        DriveResult.from(record, EnergyType.EV, "0.1.0", efficiency = Efficiency.sample(13.5, electric = false))
    }

    /** 예시 고리 코스를 초속 10m로 한 바퀴 도는 가상주행 JSONL. C의 가상주행 도구가 낼 모양과 같다. */
    private fun loopReplayText(kmPerKwh: Double): String {
        val lines = ArrayList<String>()
        lines += ContractCodec.encodeReplayHeader("예시 고리 한 바퀴 (합성)", synthetic = true, courseId = course.courseId, energyType = EnergyType.EV)
        val odoStartKm = 12345.6
        val batteryStartWh = 70_000.0
        lines += ContractCodec.encodeSignal(DriveSignal.Position(0, course.start.point, 5.0))
        lines += ContractCodec.encodeSignal(DriveSignal.Odometer(0, odoStartKm))
        lines += ContractCodec.encodeSignal(DriveSignal.Battery(0, batteryStartWh))
        lines += ContractCodec.encodeSignal(DriveSignal.GearChange(2000, Gear.DRIVE))
        val track = TestGeo.driveAlong(course.shape, mps = 10.0, startMs = 3000)
        var walkedM = 0.0
        track.forEachIndexed { i, tp ->
            if (i > 0) walkedM += distanceM(track[i - 1].point, tp.point)
            lines += ContractCodec.encodeSignal(DriveSignal.Speed(tp.timeMs, 36.0))
            lines += ContractCodec.encodeSignal(DriveSignal.Position(tp.timeMs, tp.point, 5.0))
            if (i % 5 == 0 || i == track.lastIndex) {
                // 에뮬레이터처럼 누적거리는 0.1km 단위로 내림해 보낸다.
                lines += ContractCodec.encodeSignal(DriveSignal.Odometer(tp.timeMs, odoStartKm + floor(walkedM / 100) / 10))
                lines += ContractCodec.encodeSignal(DriveSignal.Battery(tp.timeMs, batteryStartWh - walkedM / 1000 / kmPerKwh * 1000))
            }
        }
        val end = track.last().timeMs
        lines += ContractCodec.encodeSignal(DriveSignal.Speed(end + 1000, 0.0))
        lines += ContractCodec.encodeSignal(DriveSignal.GearChange(end + 2000, Gear.PARK))
        return lines.joinToString("\n")
    }

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.num(key: String) = this[key]!!.jsonPrimitive.content.toDouble()
}
