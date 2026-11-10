package ai.pleosapp.drive.ui

import ai.pleosapp.drive.ui.common.SampleRoute
import ai.pleosapp.drive.ui.confirm.ConfirmScreen
import ai.pleosapp.drive.ui.guidance.StartGuidanceScreen
import ai.pleosapp.drive.ui.length.CourseLengthScreen
import ai.pleosapp.drive.ui.length.LengthOption
import ai.pleosapp.drive.ui.region.CourseItem
import ai.pleosapp.drive.ui.region.FakeRegionSource
import ai.pleosapp.drive.ui.region.RegionScreen
import ai.pleosapp.drive.ui.region.RegionUiState
import ai.pleosapp.drive.ui.result.ResultScreen
import ai.pleosapp.drive.ui.theme.PleosTheme
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * TEMP: 화면 이동을 가짜 데이터로 이어 보는 임시 연결.
 * 진짜 화면 이동과 상태는 ViewModel(D)이 맡으니, 연결되면 이 파일은 지워요.
 *
 * 지금 연결된 흐름:
 *   지역 입력 + 코스 선택 → 코스 길이 선택 → 선택 확인 → 시작점까지 안내 → 도착 결과(임시)
 * 각 화면에서 이전 버튼/취소/뒤로가기를 누르면 한 단계 앞 화면으로 돌아가요.
 */
@Composable
fun DemoFlow(initialQuery: String = "") {
    var query by remember { mutableStateOf(initialQuery) }
    var chosen by remember { mutableStateOf<CourseItem?>(null) }        // 길이 선택으로 넘어간 코스
    var lastId by remember { mutableStateOf<String?>(null) }            // 돌아왔을 때 다시 보여줄 선택
    var lengthId by remember { mutableStateOf<String?>("long") }
    var confirming by remember { mutableStateOf<LengthOption?>(null) }  // 선택 확인으로 넘어간 길이
    var guiding by remember { mutableStateOf(false) }                   // 시작점까지 안내 중
    var finished by remember { mutableStateOf(false) }                  // 도착 결과 (TEMP: 주행 화면은 아직 없어서 바로 연결)

    PleosTheme {
        val course = chosen
        val option = confirming
        when {
            course != null && option != null && guiding && finished -> {
                BackHandler { finished = false }
                ResultScreen(
                    title = "주행 완료 · ${course.name}",
                    distance = "42.3km", duration = "1:24:10", efficiency = "12.8km/L", place = when (course.id) { "leopard" -> "용인시 기흥구"; "candle" -> "용인시 처인구"; else -> "수원시" },
                    period = "2026.11.05  10:12 ~ 11:36",
                    route = course.shape, mapRes = course.mapRes, qrCaption = "QR을 찍어 친구와 비교해요",
                    onBack = { finished = false },
                )
            }
            course != null && option != null && guiding -> {
                BackHandler { guiding = false }
                StartGuidanceScreen(
                    courseTitle = "${course.label} ${course.name}",
                    distanceToStart = "3.2km",
                    etaToStart = "약 8분",
                    route = course.shape,
                    start = SampleRoute.startOf(course.shape),
                    currentPosition = SampleRoute.currentNear(SampleRoute.startOf(course.shape)),
                    mapRes = course.mapRes,
                    onConfirm = { finished = true }, // TODO: 주행 기록 화면이 생기면 거기로
                    onBack = { guiding = false },
                )
            }
            course != null && option != null -> {
                BackHandler { confirming = null }
                ConfirmScreen(
                    title = "${course.label} ${course.name.removeSuffix(" 코스")} 경로로 안내합니다",
                    summary = "경기도 ${course.name} · ${option.distanceKm}km · 약 ${option.minutes}분",
                    notice = "먼저 코스 시작점까지 안내해 드릴게요",
                    onCancel = { confirming = null },
                    onConfirm = { guiding = true },
                    mapRes = course.mapRes,
                )
            }
            course != null -> {
                BackHandler { chosen = null }
                val options = lengthOptionsFor(course)
                CourseLengthScreen(
                    courseTitle = "${course.label} ${course.name}",
                    subtitle = "같은 ${course.name.removeSuffix(" 코스")} 모양을 작게, 또는 크게 그려요",
                    options = options,
                    selectedId = lengthId,
                    onOptionSelected = { lengthId = it },
                    onConfirm = { confirming = options.firstOrNull { it.id == lengthId } ?: options.last() },
                    onBack = { chosen = null },
                    mapRes = course.mapRes,
                )
            }
            else -> RegionScreen(
                state = RegionUiState(query, FakeRegionSource.search(query), selectedId = lastId),
                onQueryChange = { query = it; lastId = null },
                onClear = { query = ""; lastId = null },
                onCourseSelected = { lastId = it.id; chosen = it },
            )
        }
    }
}

/** TEMP: 짧은 코스는 긴 코스의 약 62% 로 가정한 임시 값 (시안: 42km·80분 → 26km·50분) */
private fun lengthOptionsFor(c: CourseItem) = listOf(
    LengthOption("short", "짧은 코스", "가볍게 한 바퀴", (c.distanceKm * 0.62).toInt(), (c.minutes * 0.62).toInt(), false, SampleRoute.scaled(c.shape, 0.6f), c.mapRes),
    LengthOption("long", "긴 코스", "${c.name.removeSuffix(" 코스")} 모양을 크게 그려요", c.distanceKm, c.minutes, true, c.shape, c.mapRes),
)
