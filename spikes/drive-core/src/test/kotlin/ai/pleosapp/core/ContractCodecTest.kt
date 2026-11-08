package ai.pleosapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 규격 예시 파일(docs/contracts/samples/)이 그대로 읽히는지, 틀린 입력을 알아듣게 거르는지 본다. */
class ContractCodecTest {
    private val courseText = TestGeo.resource("contracts/course.json")
    private val replayText = TestGeo.resource("contracts/drive-signals.jsonl")
    private val resultText = TestGeo.resource("contracts/drive-result.json")

    // ---------- ① 코스 ----------

    @Test fun sampleCourseIsRead() {
        val c = ContractCodec.decodeCourse(courseText)
        assertEquals("sample-loop", c.courseId)
        assertEquals("A", c.label)
        assertEquals(5, c.shape.size)
        // [경도, 위도] 순서를 바르게 뒤집었는지
        assertEquals(GeoPoint(37.412700, 127.094700), c.shape.first())
        assertEquals(GeoPoint(37.415900, 127.101200), c.shape[1])
        assertEquals(2, c.waypoints.size)
        assertEquals("경유 1", c.waypoints[0].name)
        assertEquals(60.0, c.corridorM, 1e-9)
        assertEquals(3200.0, c.distanceM!!, 1e-9)
        assertEquals(600L, c.estDurationSec)
        assertTrue(c.isLoop)
    }

    @Test fun swappedCoordinatesAreRejectedWithHint() {
        val swapped = courseText.replace("[127.094700, 37.412700]", "[37.412700, 127.094700]")
        val e = assertThrows(ContractFormatException::class.java) { ContractCodec.decodeCourse(swapped) }
        assertTrue(e.message!!, e.message!!.contains("[경도, 위도]"))
    }

    @Test fun wrongSchemaIsRejected() {
        val e = assertThrows(ContractFormatException::class.java) {
            ContractCodec.decodeCourse(courseText.replace("course/1", "course/2"))
        }
        assertTrue(e.message!!.contains("course/1"))
    }

    @Test fun missingCorridorFallsBackToDefault() {
        val without = courseText.replace(Regex(""",\s*"corridor_m"\s*:\s*60"""), "")
        assertTrue("예시 파일 형식이 바뀌었으면 이 시험을 고친다", !without.contains("corridor_m"))
        assertEquals(Course.DEFAULT_CORRIDOR_M, ContractCodec.decodeCourse(without).corridorM, 1e-9)
    }

    @Test fun brokenJsonGivesReadableError() {
        val e = assertThrows(ContractFormatException::class.java) { ContractCodec.decodeCourse("{ not json") }
        assertTrue(e.message!!.startsWith("코스 JSON을 읽지 못했습니다"))
    }

    // ---------- ② 주행 신호 ----------

    @Test fun sampleReplayIsRead() {
        val r = ContractCodec.decodeReplay(replayText)
        assertEquals(2, r.version)
        assertEquals("sample-loop", r.courseId)
        assertEquals(EnergyType.EV, r.energyType)
        assertTrue(r.synthetic)
        assertEquals(12, r.signals.size)
        assertEquals(DriveSignal.Position(0, GeoPoint(37.412700, 127.094700), 5.0), r.signals[0])
        assertEquals(DriveSignal.Odometer(0, 12345.6), r.signals[1])
        assertEquals(DriveSignal.Battery(0, 70000.0), r.signals[2])
        assertEquals(DriveSignal.GearChange(3000, Gear.DRIVE), r.signals[3])
        assertEquals(DriveSignal.GearChange(8000, Gear.PARK), r.signals[9])
        assertEquals(8000L, r.durationMs)
        assertTrue(r.skippedKinds.isEmpty())
    }

    @Test fun version1FilesStillRead() {
        val v1 = """
            {"format": "drive-replay", "version": 1, "name": "옛 파일"}
            {"t": 0, "k": "gear", "v": "D"}
            {"t": 1000, "k": "speed", "v": 30}
        """.trimIndent()
        val r = ContractCodec.decodeReplay(v1)
        assertEquals(1, r.version)
        assertEquals(2, r.signals.size)
        assertTrue("표시가 없으면 합성으로 본다", r.synthetic)
        assertNull(r.energyType)
    }

    @Test fun unknownSignalKindsAreSkippedAndCounted() {
        val text = replayText + "\n{\"t\": 9000, \"k\": \"tire_pressure\", \"v\": 35}\n{\"t\": 9500, \"k\": \"tire_pressure\", \"v\": 34}\n"
        val r = ContractCodec.decodeReplay(text)
        assertEquals(12, r.signals.size)
        assertEquals(mapOf("tire_pressure" to 2), r.skippedKinds)
    }

    @Test fun timeGoingBackwardsIsRejectedWithLineNumber() {
        val text = replayText + "\n{\"t\": 100, \"k\": \"speed\", \"v\": 1}"
        val e = assertThrows(ContractFormatException::class.java) { ContractCodec.decodeReplay(text) }
        assertTrue(e.message!!, e.message!!.contains("번째 줄") && e.message!!.contains("거꾸로"))
    }

    @Test fun notAReplayFileIsRejected() {
        assertThrows(ContractFormatException::class.java) { ContractCodec.decodeReplay(courseText.replace("\n", " ")) }
        assertThrows(ContractFormatException::class.java) { ContractCodec.decodeReplay("") }
        assertThrows(ContractFormatException::class.java) {
            ContractCodec.decodeReplay("{\"format\": \"drive-replay\", \"version\": 9}")
        }
    }

    @Test fun signalsRoundTrip() {
        val signals = listOf(
            DriveSignal.GearChange(0, Gear.DRIVE),
            DriveSignal.Speed(1000, 42.5),
            DriveSignal.Position(1000, GeoPoint(37.123456, 127.654321), 4.0),
            DriveSignal.Position(2000, GeoPoint(37.1, 127.2)),
            DriveSignal.Odometer(3000, 12345.6),
            DriveSignal.Battery(3000, 69_000.0),
            DriveSignal.Fuel(3000, 40_000.0),
            DriveSignal.FuelEconomy(3000, 7.5),
            DriveSignal.EvEfficiency(3000, 6.2),
            DriveSignal.GearChange(4000, Gear.PARK),
        )
        val header = ContractCodec.encodeReplayHeader("왕복", synthetic = true, courseId = "c1", energyType = EnergyType.EV)
        val text = (listOf(header) + signals.map(ContractCodec::encodeSignal)).joinToString("\n")
        val r = ContractCodec.decodeReplay(text)
        assertEquals(signals, r.signals)
        assertEquals("c1", r.courseId)
        assertEquals(2, r.version)
    }

    // ---------- ③ 주행 결과 ----------

    @Test fun sampleResultIsReadAndWrittenBackTheSame() {
        val r = ContractCodec.decodeDriveResult(resultText)
        assertEquals("sample-loop", r.courseId)
        assertEquals(EnergyType.FUEL, r.energyType)
        assertEquals(13.5, r.avgFuelEconomy!!, 1e-9)
        assertEquals(Efficiency.Source.SAMPLE, r.fuelEconomySource)
        assertEquals(GeoPoint(37.412700, 127.094700), r.polyline.first())
        assertEquals("2026-11-08T14:03+09:00", r.startedAt.toString())

        val again = ContractCodec.decodeDriveResult(ContractCodec.encodeDriveResult(r))
        assertEquals(r, again)
    }

    @Test fun writtenResultHasExactlyTheSampleFields() {
        val r = ContractCodec.decodeDriveResult(resultText)
        val written = kotlinx.serialization.json.Json.parseToJsonElement(ContractCodec.encodeDriveResult(r))
        val sample = kotlinx.serialization.json.Json.parseToJsonElement(resultText)
        assertEquals(
            (sample as kotlinx.serialization.json.JsonObject).keys,
            (written as kotlinx.serialization.json.JsonObject).keys,
        )
    }

    @Test fun resultTimesKeepOffset() {
        val text = ContractCodec.encodeDriveResult(ContractCodec.decodeDriveResult(resultText))
        assertTrue(text, text.contains("\"started_at\":\"2026-11-08T14:03:00+09:00\""))
    }
}
