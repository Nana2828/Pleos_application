package ai.pleosapp.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 디버그용 보고서. 환경 변수 SCENARIO_DIR의 *.jsonl(규격 ②)을 모두 돌려 주행·코스·효율 판정 값을 표로 찍는다.
 * 코스 파일은 SCENARIO_DIR/courses/<course_id>.json 또는 이름이 course_id로 끝나는 파일에서 찾는다.
 * 환경 변수가 없으면 건너뛴다(평소 `gradlew test`에는 영향 없음).
 *
 *   SCENARIO_DIR=C:/work/scenarios ./gradlew test --tests '*ScenarioReport*' -i
 */
class ScenarioReport {
    @Test fun report() {
        val dir = System.getenv("SCENARIO_DIR")?.let(::File)
        assumeTrue("SCENARIO_DIR 없음", dir != null && dir.isDirectory)
        val courses = File(dir, "courses").listFiles { f -> f.extension == "json" }.orEmpty()
            .map { ContractCodec.decodeCourse(it.readText()) }
            .associateBy { it.courseId }
        val lines = mutableListOf(
            "| 시나리오 | 기록 | 거리 m (GPS+끊김) | 시간 s / 움직임 s | 진입 s | 완주 | 진행률 | 일치율 | 커버리지 | 효율 |",
            "|---|---|---|---|---|---|---|---|---|---|",
        )
        for (file in dir!!.listFiles { f -> f.extension == "jsonl" }.orEmpty().sortedBy { it.name }) {
            val replay = ContractCodec.decodeReplay(file.readText())
            val session = DriveSession(DriveOrigin(DriveOrigin.Kind.REPLAY, replay.name, replay.synthetic))
            val events = replay.signals.mapNotNull(session::handle)
            val record = (events.lastOrNull() as? SessionEvent.Finished)?.record
            if (record == null) {
                lines += "| ${file.nameWithoutExtension} | ❌ ${events.lastOrNull()} | | | | | | | | |"
                continue
            }
            val course = replay.courseId?.let(courses::get)
            val match = course?.let { CourseMatcher.evaluate(it, record.points) }
            val eff = DriveResult.efficiencyOf(record, replay.energyType ?: EnergyType.EV)
            fun f(v: Double?, d: Int = 2) = v?.let { "%.${d}f".format(it) } ?: "-"
            lines += "| ${file.nameWithoutExtension} | ✅ 구간 ${record.segments.size} | " +
                "${f(record.distanceM, 0)} (${f(record.gapDistanceM, 0)}) | " +
                "${record.durationMs / 1000} / ${record.movingMs / 1000} | " +
                "${match?.enteredAtMs?.let { f((it - record.startMs) / 1000.0, 1) } ?: "-"} | " +
                "${match?.finished ?: "-"} | ${f(match?.progressRatio)} | ${f(match?.onCourseRatio)} | " +
                "${f(match?.coverageRatio)} | ${eff?.let { "${f(it.value)} ${it.source.code}" } ?: "-"} |"
        }
        val out = File(dir, "report.md")
        out.writeText(lines.joinToString("\n") + "\n")
        println(out.readText())
    }
}
