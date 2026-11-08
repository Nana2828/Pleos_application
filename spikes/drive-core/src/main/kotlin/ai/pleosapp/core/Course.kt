package ai.pleosapp.core

/** 이름 붙은 지점. 규격 ①의 `start`, `waypoints`, `destination`. */
data class NamedPoint(val point: GeoPoint, val name: String = "")

/** 규격 ① 코스 (`docs/data-contracts.md` 1장, `schema: course/1`). */
data class Course(
    val courseId: String,
    /** 지도에 그릴 모양. 진입·일치율 판정의 기준선. */
    val shape: List<GeoPoint>,
    val start: NamedPoint,
    val destination: NamedPoint,
    val waypoints: List<NamedPoint> = emptyList(),
    val region: String = "",
    val label: String = "",
    val name: String = "",
    val distanceM: Double? = null,
    val estDurationSec: Long? = null,
    /** "코스 위에 있다"고 볼 선 좌우 폭. 규격에 없으면 [DEFAULT_CORRIDOR_M]. */
    val corridorM: Double = DEFAULT_CORRIDOR_M,
) {
    init {
        require(shape.size >= 2) { "코스 모양은 점이 2개 이상이어야 합니다" }
        require(corridorM > 0) { "코스 폭은 0보다 커야 합니다" }
    }

    /** 모양 선을 따라 잰 길이. 규격의 `distance_m`(도로 기준 예상 거리)과 다를 수 있다. */
    val shapeLengthM: Double by lazy { lengthM(shape) }

    /** 출발점과 도착점이 같은(가까운) 고리 코스인지. */
    val isLoop: Boolean get() = distanceM(start.point, destination.point) < LOOP_THRESHOLD_M

    companion object {
        /** 규격 예시 값과 같다. */
        const val DEFAULT_CORRIDOR_M = 60.0
        private const val LOOP_THRESHOLD_M = 100.0
    }
}
