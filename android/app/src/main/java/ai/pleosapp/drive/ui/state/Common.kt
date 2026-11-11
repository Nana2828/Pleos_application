package ai.pleosapp.drive.ui.state

/*
 * 화면들이 같이 쓰는 값.
 *
 * 경계 규칙 (D·E 가안):
 * - D(재원)가 이 폴더(ui/state)의 UiState·Actions·ViewModel을 만든다.
 * - E(나연)는 화면(Composable)에서 UiState를 그대로 그리고, 버튼을 누르면 Actions를 부른다.
 * - 화면 문구(예: "연비"/"효율", 숫자 형식)는 E가 정한다. 여기에는 값과 단위만 둔다.
 */

/** 지도 위 한 점 (위도·경도, WGS84). */
data class MapPoint(
    val lat: Double,
    val lng: Double,
)

/** 화면에 보여 줄 코스 요약. 코스 선택·선택 확인 화면이 같이 쓴다. */
data class CourseSummary(
    val id: String,
    /** 목록 앞 글자. 예: "A" */
    val label: String,
    /** 예: "하트 코스" */
    val name: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    /** 코스 모양 좌표. 미리보기와 지도 경로선에 쓴다. */
    val shape: List<MapPoint>,
    /** 고를 수 있으면 true. 시연의 더미(준비 중) 코스는 false → 화면은 흐리게 그리고 눌러도 아무 일도 없다. */
    val enabled: Boolean = true,
    /** 코스 전체 이름. 예: "부천·인천 고양이 코스". 모르면 [name]과 같다 */
    val title: String = name,
    /** 지나는 지역 설명. 예: "부천 소사구·오정구, 인천 남동구·부평구" */
    val area: String = "",
)

/** 효율(연비·전비) 단위. 에뮬레이터 차는 전기차라 KM_PER_KWH가 나온다. */
enum class EfficiencyUnit { KM_PER_L, KM_PER_KWH }

/** 효율 값을 어떻게 얻었는지. 화면에서 "추정" 같은 표시가 필요할 때 쓴다. */
enum class EfficiencySource {
    /** 차가 직접 준 값 (Vehicle SDK) */
    VEHICLE,

    /** 주행거리와 연료·배터리 변화로 앱이 계산한 값 */
    CALCULATED,

    /** 시연용 예시 값 */
    SAMPLE,
}

data class Efficiency(
    val value: Double,
    val unit: EfficiencyUnit,
    val source: EfficiencySource,
)

/**
 * 위경도를 0~1 좌표로 바꾼다. E의 지도 그림(사진 위에 0~1 좌표로 선을 그림)에 바로 넘길 수 있다.
 * x는 서→동(0→1), y는 북→남(0→1, 화면 아래로 커짐). 가로세로 비율은 위도에 맞춰 보정하고, 남는 쪽은 가운데 맞춘다.
 *
 * 예: `val frame = MapFrame.fit(course.shape + approachRoute)` → `frame.toUnit(point)`
 */
class MapFrame private constructor(
    private val maxLat: Double,
    private val minLng: Double,
    private val spanDeg: Double,
    private val lngScale: Double,
    private val padX: Double,
    private val padY: Double,
) {
    /** 0~1 좌표 (x, y) */
    fun toUnit(p: MapPoint): Pair<Float, Float> {
        val x = padX + (p.lng - minLng) * lngScale / spanDeg
        val y = padY + (maxLat - p.lat) / spanDeg
        return x.toFloat() to y.toFloat()
    }

    fun toUnit(points: List<MapPoint>): List<Pair<Float, Float>> = points.map(::toUnit)

    companion object {
        /** [points]가 모두 들어가게 맞춘다. [padding]은 가장자리 여백 비율. */
        fun fit(points: List<MapPoint>, padding: Double = 0.06): MapFrame {
            require(points.isNotEmpty()) { "점이 없습니다" }
            val minLat = points.minOf { it.lat }
            val maxLat = points.maxOf { it.lat }
            val minLng = points.minOf { it.lng }
            val maxLng = points.maxOf { it.lng }
            val lngScale = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2))
            val w = (maxLng - minLng) * lngScale
            val h = maxLat - minLat
            val inner = 1 - 2 * padding
            val span = maxOf(w, h, 1e-9) / inner
            // 남는 쪽을 가운데로
            val padX = padding + (inner - w / span) / 2
            val padY = padding + (inner - h / span) / 2
            return MapFrame(maxLat, minLng, span, lngScale, padX, padY)
        }
    }
}
