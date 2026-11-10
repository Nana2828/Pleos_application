package ai.pleosapp.drive.ui.state

/** 6. 도착 결과 + 결과 QR (PLEOS-46, docs/ui/page-6.png) */
data class ResultUiState(
    val courseName: String = "",
    /** "위치" 칸. 예: "수원시" */
    val regionName: String = "",
    val distanceKm: Double = 0.0,
    val durationSec: Long = 0,
    /** 효율을 못 구했으면 null */
    val efficiency: Efficiency? = null,
    val startedAtEpochMs: Long = 0,
    val endedAtEpochMs: Long = 0,
    /** 실제로 달린 길 (지도 위 빨간 선) */
    val drivenPath: List<MapPoint> = emptyList(),
    /** 코스 모양 (지도 위 파란 선) */
    val courseShape: List<MapPoint> = emptyList(),
    /** QR에 담을 결과 페이지 주소. 아직 없으면 null (전송 중·실패) */
    val resultUrl: String? = null,
    val upload: UploadStatus = UploadStatus.UPLOADING,
)

enum class UploadStatus { UPLOADING, DONE, FAILED }

interface ResultActions {
    /** 결과 전송이 실패했을 때 다시 보낸다. */
    fun onRetryUpload()

    /** 결과 화면을 닫고 처음으로 돌아간다. */
    fun onDone()

    object Noop : ResultActions {
        override fun onRetryUpload() {}
        override fun onDone() {}
    }
}
