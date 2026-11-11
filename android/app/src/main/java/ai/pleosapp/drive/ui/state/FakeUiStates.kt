package ai.pleosapp.drive.ui.state

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 화면을 먼저 만들 때 쓰는 가짜 데이터. 값은 UI 시안(docs/ui/page-1~6)과 맞췄다.
 *
 * 쓰는 법 (Compose 미리보기 예):
 * ```
 * @Preview
 * @Composable
 * fun CoursesScreenPreview() {
 *     CoursesScreen(state = FakeUiStates.courses, actions = CoursesActions.Noop)
 * }
 * ```
 * 좌표는 수원 근처에 모양만 흉내 낸 것이라 실제 도로와 맞지 않는다.
 */
object FakeUiStates {

    private val suwonCenter = MapPoint(lat = 37.275, lng = 127.010)

    /** 하트 모양. 첫 점이 아래쪽 꼭짓점(코스 시작점)이다. */
    val heartShape: List<MapPoint> = closedShape(points = 72) { t ->
        // 하트 곡선. t=0이 아래 꼭짓점이 되도록 PI만큼 돌린다.
        val a = t + PI
        val x = 16 * sin(a).pow(3)
        val y = 13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)
        x / 16 * 0.060 to y / 17 * 0.040
    }

    /** 표범 자리 표시용 둥근 모양 (모양은 B가 줄 데이터로 바뀐다) */
    val leopardShape: List<MapPoint> = closedShape(points = 48, center = MapPoint(37.300, 126.975)) { t ->
        val r = 1 + 0.15 * sin(5 * t)
        r * cos(t) * 0.045 to r * sin(t) * 0.025
    }

    /** 촛불 자리 표시용 모양 (모양은 B가 줄 데이터로 바뀐다) */
    val candleShape: List<MapPoint> = listOf(
        -0.010 to -0.040, 0.010 to -0.040, 0.010 to 0.010, 0.000 to 0.030,
        -0.010 to 0.010, -0.010 to -0.040,
    ).map { (dLng, dLat) -> MapPoint(37.250 + dLat, 127.050 + dLng) }

    val leopardCourse = CourseSummary(
        id = "suwon-leopard", label = "A", name = "표범 코스",
        distanceKm = 26.0, estimatedMinutes = 55, shape = leopardShape,
    )
    val heartCourse = CourseSummary(
        id = "suwon-heart", label = "B", name = "하트 코스",
        distanceKm = 42.0, estimatedMinutes = 80, shape = heartShape,
    )
    val candleCourse = CourseSummary(
        id = "suwon-candle", label = "C", name = "촛불 코스",
        distanceKm = 63.0, estimatedMinutes = 95, shape = candleShape,
    )

    // 1. 지역 입력
    val region = RegionUiState(
        query = "수원",
        suggestions = listOf(
            RegionSuggestion(id = "suwon", name = "수원시", detail = "경기도"),
            RegionSuggestion(id = "suwon-station", name = "수원역", detail = "경기 수원시 팔달구"),
            RegionSuggestion(id = "suwon-hwaseong", name = "수원화성", detail = "경기 수원시 장안구"),
        ),
    )

    // 2. 코스 선택
    val courses = CoursesUiState(
        regionName = "수원",
        courses = listOf(leopardCourse, heartCourse, candleCourse),
        selectedCourseId = heartCourse.id,
    )

    // 2-2. 코스 미리보기 + 안내 시작 (PLEOS-62)
    val preview = PreviewUiState(
        course = heartCourse.copy(title = "화성 국도·고속도로 하트 코스", area = "경기 남부 (화성·오산·평택 일대)"),
        courseRoute = heartShape,
        startPoint = heartShape.first(),
        place = "화성시",
        distanceToStartKm = 45.3,
        minutesToStart = 35,
        canStartGuidance = true,
    )

    // 3. 선택 확인
    val confirm = ConfirmUiState(
        regionName = "수원",
        course = heartCourse,
        canStartGuidance = true,
    )

    // 4. 시작점까지 안내
    val approach = ApproachUiState(
        course = heartCourse,
        startPoint = heartShape.first(),
        currentPosition = heartShape.first().let { MapPoint(it.lat - 0.004, it.lng - 0.020) },
        distanceToStartKm = 3.2,
        minutesToStart = 8,
        guidance = GuidanceStatus.GUIDING,
        showRecordingHint = true,
    )

    private val driveStart = LocalDateTime.of(2026, 11, 5, 10, 12)
        .atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
    private const val DRIVE_SECONDS = 5_050L // 1:24:10

    // 5. 주행 기록 중
    val driving = DrivingUiState(
        courseName = heartCourse.name,
        isRecording = true,
        nextTurn = TurnInfo(direction = TurnDirection.RIGHT, distanceM = 450),
        arrivalAtEpochMs = driveStart + DRIVE_SECONDS * 1000,
        remainingKm = 15.2,
        remainingMinutes = 31,
        drivenKm = 27.1,
        elapsedSec = 3_180,
        courseShape = heartShape,
        drivenPath = heartShape.take(heartShape.size * 2 / 3),
        currentPosition = heartShape[heartShape.size * 2 / 3],
    )

    // 5-1. 들를 곳에 도착해 기록을 멈춘 상태 (PLEOS-63)
    val drivingAtStop = driving.copy(
        isRecording = false,
        nextTurn = null,
        arrivalAtEpochMs = null,
        remainingKm = null,
        remainingMinutes = null,
        speedKph = 0.0,
        stop = StopStatus(name = "화성식염온천", phase = StopPhase.ARRIVED),
        stopPoints = listOf(heartShape[heartShape.size * 2 / 3].let { MapPoint(it.lat + 0.01, it.lng + 0.01) }),
        detourRoute = heartShape[heartShape.size * 2 / 3].let { MapPoint(it.lat, it.lng) }.let { from ->
            listOf(from, MapPoint(from.lat + 0.005, from.lng + 0.002), MapPoint(from.lat + 0.01, from.lng + 0.01))
        },
        currentPosition = heartShape[heartShape.size * 2 / 3].let { MapPoint(it.lat + 0.01, it.lng + 0.01) },
    )

    // 6. 도착 결과
    val result = ResultUiState(
        courseName = heartCourse.name,
        regionName = "수원시",
        distanceKm = 42.3,
        durationSec = DRIVE_SECONDS,
        efficiency = Efficiency(value = 12.8, unit = EfficiencyUnit.KM_PER_L, source = EfficiencySource.SAMPLE),
        startedAtEpochMs = driveStart,
        endedAtEpochMs = driveStart + DRIVE_SECONDS * 1000,
        // 실제 주행은 코스와 조금 어긋난다는 느낌만 낸다.
        drivenPath = heartShape.mapIndexed { i, p -> MapPoint(p.lat + 0.0006 * sin(i * 0.7), p.lng + 0.0006 * cos(i * 0.5)) },
        courseShape = heartShape,
        resultUrl = "http://192.168.0.12:5173/",
        upload = UploadStatus.DONE,
    )

    /** 전송 실패 화면을 확인할 때 */
    val resultUploadFailed = result.copy(resultUrl = null, upload = UploadStatus.FAILED)

    /** 0..2π를 [points]개로 나눠 [offset](경도 차, 위도 차)으로 닫힌 모양을 만든다. */
    private fun closedShape(
        points: Int,
        center: MapPoint = suwonCenter,
        offset: (Double) -> Pair<Double, Double>,
    ): List<MapPoint> {
        val open = (0 until points).map { i ->
            val (dLng, dLat) = offset(2 * PI * i / points)
            MapPoint(lat = center.lat + dLat, lng = center.lng + dLng)
        }
        return open + open.first()
    }
}
