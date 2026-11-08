package ai.pleosapp.core

import kotlin.math.cos

/** 시험용 좌표 도우미. 판교 부근 기준점에서 동쪽 x m, 북쪽 y m 지점을 만든다. */
object TestGeo {
    val origin = GeoPoint(37.40, 127.10)
    private const val M_PER_DEG_LAT = 111_195.0

    fun at(xM: Double, yM: Double) = GeoPoint(
        origin.lat + yM / M_PER_DEG_LAT,
        origin.lon + xM / (M_PER_DEG_LAT * cos(Math.toRadians(origin.lat))),
    )

    /** 선을 따라 일정한 속도로 달린 1초 간격 위치. */
    fun driveAlong(line: List<GeoPoint>, mps: Double, startMs: Long = 0): List<TrackPoint> {
        val total = lengthM(line)
        val count = (total / mps).toInt() + 1
        return resample(line, count).mapIndexed { i, p -> TrackPoint(startMs + i * 1000L, p) }
    }

    fun course(
        shape: List<GeoPoint>,
        id: String = "test",
        corridorM: Double = Course.DEFAULT_CORRIDOR_M,
    ) = Course(
        courseId = id,
        shape = shape,
        start = NamedPoint(shape.first(), "출발"),
        destination = NamedPoint(shape.last(), "도착"),
        corridorM = corridorM,
    )

    fun resource(name: String): String =
        checkNotNull(TestGeo::class.java.classLoader.getResource(name)) { "시험 파일 없음: $name" }.readText(Charsets.UTF_8)
}
