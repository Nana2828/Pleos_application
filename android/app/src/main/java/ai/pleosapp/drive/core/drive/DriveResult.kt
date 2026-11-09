package ai.pleosapp.drive.core.drive

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** 규격 ③ 주행 결과 (`docs/data-contracts.md` 3장, `schema: drive-result/1`). D가 만들어 `POST /drive`로 보낸다. */
data class DriveResult(
    val courseId: String?,
    val startedAt: OffsetDateTime,
    val endedAt: OffsetDateTime,
    val durationSec: Long,
    val movingSec: Long,
    val distanceKm: Double,
    val energyType: EnergyType,
    /** km/L. 전기차면 null */
    val avgFuelEconomy: Double?,
    /** km/kWh. 내연기관이면 null(JSON에서 생략) */
    val avgEvEfficiency: Double?,
    /** 효율 값이 없으면 null */
    val fuelEconomySource: Efficiency.Source?,
    val maxKph: Double,
    /** 코스 없이 달렸으면 null */
    val onCourseRatio: Double?,
    val polyline: List<GeoPoint>,
    val recordSource: DriveOrigin.Kind,
    val synthetic: Boolean,
    val appVersion: String,
) {
    companion object {
        /** 결과로 보낼 궤적의 점 간격(m). 규격 ③ "점 사이 10m 이상". */
        const val POLYLINE_SPACING_M = 10.0

        /** 규격 0장 예시가 한국 시간이라 기본값을 +09:00으로 둔다. */
        val KST: ZoneOffset = ZoneOffset.ofHours(9)

        fun efficiencyOf(record: DriveRecord, energyType: EnergyType): Efficiency.Resolved? =
            Efficiency.resolve(
                electric = energyType == EnergyType.EV,
                start = record.startSnapshot,
                end = record.endSnapshot,
                vehicleKmPerKwh = record.vehicleKmPerKwh,
                vehicleLitersPer100Km = record.vehicleLitersPer100Km,
            )

        /**
         * 저장된 주행 한 건으로 결과를 만든다.
         * @param clockOffsetMs 재생 파일처럼 시간이 "시작 기준 경과 ms"일 때 더할 Unix 시각. 실차 기록이면 0.
         * @param efficiency 기본은 차 값 → 전후 차이 순으로 계산. 시연용 예시 값을 쓰려면 `Efficiency.sample`을 넘긴다.
         */
        fun from(
            record: DriveRecord,
            energyType: EnergyType,
            appVersion: String,
            course: Course? = null,
            match: CourseMatch? = course?.let { CourseMatcher.evaluate(it, record.points) },
            efficiency: Efficiency.Resolved? = efficiencyOf(record, energyType),
            clockOffsetMs: Long = 0,
            zone: ZoneOffset = KST,
        ): DriveResult {
            val electric = energyType == EnergyType.EV
            if (efficiency != null) {
                val expected = if (electric) Efficiency.Unit.KM_PER_KWH else Efficiency.Unit.KM_PER_L
                require(efficiency.unit == expected) { "효율 단위(${efficiency.unit})가 연료 종류($energyType)와 맞지 않습니다" }
            }
            return DriveResult(
                courseId = course?.courseId,
                startedAt = time(record.startMs + clockOffsetMs, zone),
                endedAt = time(record.endMs + clockOffsetMs, zone),
                durationSec = record.durationMs / 1000,
                movingSec = record.movingMs / 1000,
                distanceKm = record.distanceM / 1000,
                energyType = energyType,
                avgFuelEconomy = if (electric) null else efficiency?.value,
                avgEvEfficiency = if (electric) efficiency?.value else null,
                fuelEconomySource = efficiency?.source,
                maxKph = record.maxKph,
                onCourseRatio = if (course == null) null else match?.onCourseRatio,
                polyline = thin(record.points.map { it.point }, POLYLINE_SPACING_M),
                recordSource = record.origin.kind,
                synthetic = record.origin.synthetic,
                appVersion = appVersion,
            )
        }

        private fun time(epochMs: Long, zone: ZoneOffset): OffsetDateTime =
            Instant.ofEpochMilli(epochMs).atOffset(zone).truncatedTo(ChronoUnit.SECONDS)
    }
}
