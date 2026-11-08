package ai.pleosapp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToLong

class ContractFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 규격 ② 재생 파일 한 개. 신호 시간은 시작 기준 경과 ms. */
data class ReplayScript(
    val version: Int,
    val name: String,
    val synthetic: Boolean,
    val courseId: String?,
    val energyType: EnergyType?,
    val signals: List<DriveSignal>,
    /** 읽지 않고 건너뛴 신호 종류(`k`)와 줄 수. 새 신호가 추가돼도 옛 앱이 멈추지 않게 한다. */
    val skippedKinds: Map<String, Int> = emptyMap(),
) {
    val durationMs: Long get() = signals.lastOrNull()?.timeMs ?: 0L
}

/**
 * 역할 사이 데이터 규격(`docs/data-contracts.md`) 읽기·쓰기.
 * - 좌표 배열은 항상 [경도, 위도]. 이름 붙은 점은 {"lat", "lon"}.
 * - 틀린 입력은 [ContractFormatException]과 사람이 읽을 수 있는 이유로 알린다.
 */
object ContractCodec {
    const val COURSE_SCHEMA = "course/1"
    const val RESULT_SCHEMA = "drive-result/1"
    const val REPLAY_FORMAT = "drive-replay"
    val REPLAY_VERSIONS = setOf(1, 2)

    private val json = Json { prettyPrint = false }
    private val pretty = Json { prettyPrint = true }

    // ---------- ① 코스 ----------

    fun decodeCourse(text: String): Course {
        val obj = parseObject(text, "코스")
        checkSchema(obj, COURSE_SCHEMA)
        val shapeObj = obj.obj("shape")
        if (shapeObj.string("type") != "LineString") throw ContractFormatException("shape.type은 LineString이어야 합니다")
        val shape = decodeCoordinates(shapeObj["coordinates"], "shape.coordinates")
        if (shape.size < 2) throw ContractFormatException("shape 점이 2개 이상이어야 합니다")
        return Course(
            courseId = obj.string("course_id"),
            shape = shape,
            start = decodeNamedPoint(obj.obj("start"), "start"),
            destination = decodeNamedPoint(obj.obj("destination"), "destination"),
            waypoints = (obj["waypoints"] as? JsonArray)?.mapIndexed { i, e ->
                decodeNamedPoint(e as? JsonObject ?: throw ContractFormatException("waypoints[$i]가 객체가 아닙니다"), "waypoints[$i]")
            } ?: emptyList(),
            region = obj.optString("region") ?: "",
            label = obj.optString("label") ?: "",
            name = obj.optString("name") ?: "",
            distanceM = obj.optDouble("distance_m"),
            estDurationSec = obj.optDouble("est_duration_sec")?.roundToLong(),
            corridorM = obj.optDouble("corridor_m") ?: Course.DEFAULT_CORRIDOR_M,
        )
    }

    // ---------- ② 주행 신호 JSONL ----------

    fun decodeReplay(text: String): ReplayScript {
        val lines = text.lineSequence().withIndex().filter { it.value.isNotBlank() }.toList()
        if (lines.isEmpty()) throw ContractFormatException("빈 재생 파일입니다")
        val header = parseObject(lines.first().value, "재생 파일 머리말")
        if (header.optString("format") != REPLAY_FORMAT) throw ContractFormatException("재생 파일이 아닙니다(format이 $REPLAY_FORMAT 아님)")
        val version = header.optDouble("version")?.toInt()
        if (version !in REPLAY_VERSIONS) throw ContractFormatException("지원하지 않는 재생 파일 버전입니다: $version")
        val skipped = LinkedHashMap<String, Int>()
        val signals = ArrayList<DriveSignal>()
        for ((index, line) in lines.drop(1)) {
            val where = "${index + 1}번째 줄"
            val signal = try {
                decodeSignal(parseObject(line, where))
            } catch (e: ContractFormatException) {
                throw ContractFormatException("$where: ${e.message}", e)
            }
            when (signal) {
                is Decoded.Signal -> {
                    signals.lastOrNull()?.let { prev ->
                        if (signal.value.timeMs < prev.timeMs) throw ContractFormatException("$where: 시간이 거꾸로 갑니다(${signal.value.timeMs} < ${prev.timeMs})")
                    }
                    signals.add(signal.value)
                }
                is Decoded.Unknown -> skipped[signal.kind] = (skipped[signal.kind] ?: 0) + 1
            }
        }
        return ReplayScript(
            version = version!!,
            name = header.optString("name") ?: "",
            // 표시가 없으면 합성으로 본다. 실제 주행으로 잘못 보이는 쪽이 더 나쁘다.
            synthetic = header.optBoolean("synthetic") ?: true,
            courseId = header.optString("course_id"),
            energyType = header.optString("energy_type")?.let(EnergyType::fromCode),
            signals = signals,
            skippedKinds = skipped,
        )
    }

    /** 신호 한 개를 JSONL 한 줄로. 가상주행 도구나 기록 일지가 같은 형식을 쓰게 한다. */
    fun encodeSignal(signal: DriveSignal): String = json.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("t", signal.timeMs)
        when (signal) {
            is DriveSignal.GearChange -> { put("k", "gear"); put("v", signal.gear.code) }
            is DriveSignal.Speed -> { put("k", "speed"); put("v", signal.kph) }
            is DriveSignal.Position -> {
                put("k", "pos")
                put("lat", round(signal.point.lat, 6))
                put("lon", round(signal.point.lon, 6))
                signal.accuracyM?.let { put("acc", it) }
            }
            is DriveSignal.Odometer -> { put("k", "odo"); put("v", signal.km) }
            is DriveSignal.Battery -> { put("k", "battery"); put("v", signal.wh) }
            is DriveSignal.Fuel -> { put("k", "fuel"); put("v", signal.ml) }
            is DriveSignal.FuelEconomy -> { put("k", "fuel_eco"); put("v", signal.litersPer100Km) }
            is DriveSignal.EvEfficiency -> { put("k", "ev_eff"); put("v", signal.kmPerKwh) }
        }
    })

    fun encodeReplayHeader(name: String, synthetic: Boolean, courseId: String?, energyType: EnergyType?): String =
        json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("format", REPLAY_FORMAT)
            put("version", 2)
            put("name", name)
            put("synthetic", synthetic)
            courseId?.let { put("course_id", it) }
            energyType?.let { put("energy_type", it.code) }
        })

    private sealed interface Decoded {
        data class Signal(val value: DriveSignal) : Decoded
        data class Unknown(val kind: String) : Decoded
    }

    private fun decodeSignal(obj: JsonObject): Decoded {
        val t = (obj["t"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            ?: throw ContractFormatException("t(ms 정수)가 없습니다")
        val kind = obj.string("k")
        val signal = when (kind) {
            "gear" -> DriveSignal.GearChange(t, Gear.fromCode(obj.string("v")))
            "speed" -> DriveSignal.Speed(t, obj.number("v"))
            "pos" -> DriveSignal.Position(t, point(obj.number("lat"), obj.number("lon"), "pos"), obj.optDouble("acc"))
            "odo" -> DriveSignal.Odometer(t, obj.number("v"))
            "battery" -> DriveSignal.Battery(t, obj.number("v"))
            "fuel" -> DriveSignal.Fuel(t, obj.number("v"))
            "fuel_eco" -> DriveSignal.FuelEconomy(t, obj.number("v"))
            "ev_eff" -> DriveSignal.EvEfficiency(t, obj.number("v"))
            else -> return Decoded.Unknown(kind)
        }
        return Decoded.Signal(signal)
    }

    // ---------- ③ 주행 결과 ----------

    fun encodeDriveResult(result: DriveResult, prettyPrint: Boolean = false): String {
        val obj = buildJsonObject {
            put("schema", RESULT_SCHEMA)
            put("course_id", result.courseId)
            put("started_at", result.startedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("ended_at", result.endedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("duration_sec", result.durationSec)
            put("moving_sec", result.movingSec)
            put("distance_km", round(result.distanceKm, 2))
            put("energy_type", result.energyType.code)
            // 규격: 전기차면 avg_fuel_economy는 null, 내연기관이면 avg_ev_efficiency는 생략.
            put("avg_fuel_economy", result.avgFuelEconomy?.let { round(it, 1) })
            if (result.energyType == EnergyType.EV) put("avg_ev_efficiency", result.avgEvEfficiency?.let { round(it, 2) })
            put("fuel_economy_source", result.fuelEconomySource?.code)
            put("max_kph", result.maxKph.roundToLong())
            put("on_course_ratio", result.onCourseRatio?.let { round(it, 2) })
            put("polyline", buildJsonObject {
                put("type", "LineString")
                put("coordinates", buildJsonArray {
                    result.polyline.forEach { p ->
                        add(buildJsonArray { add(JsonPrimitive(round(p.lon, 6))); add(JsonPrimitive(round(p.lat, 6))) })
                    }
                })
            })
            put("record_source", result.recordSource.code)
            put("synthetic", result.synthetic)
            put("app_version", result.appVersion)
        }
        return (if (prettyPrint) pretty else json).encodeToString(JsonObject.serializer(), obj)
    }

    /** C·E 쪽 시험과 왕복 확인용. */
    fun decodeDriveResult(text: String): DriveResult {
        val obj = parseObject(text, "주행 결과")
        checkSchema(obj, RESULT_SCHEMA)
        val energyType = EnergyType.fromCode(obj.string("energy_type"))
            ?: throw ContractFormatException("energy_type은 ev 또는 fuel이어야 합니다")
        val polyline = obj.obj("polyline")
        if (polyline.string("type") != "LineString") throw ContractFormatException("polyline.type은 LineString이어야 합니다")
        return DriveResult(
            courseId = obj.optString("course_id"),
            startedAt = obj.time("started_at"),
            endedAt = obj.time("ended_at"),
            durationSec = obj.number("duration_sec").roundToLong(),
            movingSec = obj.number("moving_sec").roundToLong(),
            distanceKm = obj.number("distance_km"),
            energyType = energyType,
            avgFuelEconomy = obj.optDouble("avg_fuel_economy"),
            avgEvEfficiency = obj.optDouble("avg_ev_efficiency"),
            fuelEconomySource = obj.optString("fuel_economy_source")?.let {
                Efficiency.Source.fromCode(it) ?: throw ContractFormatException("알 수 없는 fuel_economy_source: $it")
            },
            maxKph = obj.number("max_kph"),
            onCourseRatio = obj.optDouble("on_course_ratio"),
            polyline = decodeCoordinates(polyline["coordinates"], "polyline.coordinates"),
            recordSource = DriveOrigin.Kind.entries.firstOrNull { it.code == obj.string("record_source") }
                ?: throw ContractFormatException("record_source는 car 또는 replay여야 합니다"),
            synthetic = obj.optBoolean("synthetic") ?: throw ContractFormatException("synthetic이 없습니다"),
            appVersion = obj.string("app_version"),
        )
    }

    // ---------- 공통 ----------

    private fun parseObject(text: String, what: String): JsonObject {
        val element = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ContractFormatException("$what JSON을 읽지 못했습니다: ${e.message}", e)
        }
        return element as? JsonObject ?: throw ContractFormatException("$what 맨 바깥이 객체가 아닙니다")
    }

    private fun checkSchema(obj: JsonObject, expected: String) {
        val schema = obj.optString("schema")
        if (schema != expected) throw ContractFormatException("schema가 $expected 이어야 합니다(받은 값: $schema)")
    }

    /** GeoJSON 좌표 배열 [[경도, 위도], ...]. */
    private fun decodeCoordinates(element: JsonElement?, where: String): List<GeoPoint> {
        val array = element as? JsonArray ?: throw ContractFormatException("$where 배열이 없습니다")
        return array.mapIndexed { i, item ->
            val pair = item as? JsonArray
            if (pair == null || pair.size < 2) throw ContractFormatException("$where[$i]는 [경도, 위도] 배열이어야 합니다")
            val lon = pair[0].numberOrNull() ?: throw ContractFormatException("$where[$i] 경도가 숫자가 아닙니다")
            val lat = pair[1].numberOrNull() ?: throw ContractFormatException("$where[$i] 위도가 숫자가 아닙니다")
            point(lat, lon, "$where[$i]")
        }
    }

    private fun decodeNamedPoint(obj: JsonObject, where: String) =
        NamedPoint(point(obj.number("lat"), obj.number("lon"), where), obj.optString("name") ?: "")

    private fun point(lat: Double, lon: Double, where: String): GeoPoint {
        if (lat !in -90.0..90.0) throw ContractFormatException("$where 위도 범위 오류($lat). 좌표 순서가 [경도, 위도]인지 확인하세요")
        if (lon !in -180.0..180.0) throw ContractFormatException("$where 경도 범위 오류($lon)")
        return GeoPoint(lat, lon)
    }

    private fun round(value: Double, digits: Int): Double {
        var scale = 1.0
        repeat(digits) { scale *= 10 }
        return Math.round(value * scale) / scale
    }

    private fun JsonElement.numberOrNull(): Double? =
        (this as? JsonPrimitive)?.takeUnless { it.isString || it is JsonNull }?.doubleOrNull?.takeIf { it.isFinite() }

    private fun JsonObject.obj(key: String): JsonObject =
        this[key] as? JsonObject ?: throw ContractFormatException("$key 객체가 없습니다")

    private fun JsonObject.string(key: String): String =
        optString(key) ?: throw ContractFormatException("$key 문자열이 없습니다")

    private fun JsonObject.optString(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Double =
        this[key]?.numberOrNull() ?: throw ContractFormatException("$key 숫자가 없습니다")

    private fun JsonObject.optDouble(key: String): Double? = this[key]?.numberOrNull()

    private fun JsonObject.optBoolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

    private fun JsonObject.time(key: String): OffsetDateTime = try {
        OffsetDateTime.parse(string(key))
    } catch (e: DateTimeParseException) {
        throw ContractFormatException("$key 시각 형식 오류(예: 2026-11-08T14:03:00+09:00)", e)
    }
}
