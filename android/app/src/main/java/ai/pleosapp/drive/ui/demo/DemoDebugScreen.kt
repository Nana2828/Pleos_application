package ai.pleosapp.drive.ui.demo

import ai.pleosapp.drive.ui.state.DemoFlowActions
import ai.pleosapp.drive.ui.state.DemoFlowState
import ai.pleosapp.drive.ui.state.DemoFlowViewModel
import ai.pleosapp.drive.ui.state.DemoStep
import ai.pleosapp.drive.ui.state.EfficiencyUnit
import ai.pleosapp.drive.ui.state.MapFrame
import ai.pleosapp.drive.ui.state.MapPoint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TEMP(D): 시연 흐름 확인용 디버그 화면. E의 진짜 화면이 [DemoFlowViewModel]에 연결되면 지운다.
 * 왼쪽은 단계별 값과 버튼, 오른쪽은 지도 대신 코스·경로 선. 위쪽 줄은 발표자 조작(배속·건너뛰기·처음부터).
 */
@Composable
fun DemoDebugScreen(vm: DemoFlowViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.loadError.collectAsStateWithLifecycle()
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val e = error
            if (e != null) Text(e, Modifier.padding(24.dp), color = Color.Red) else DemoDebugContent(state, vm.actions)
        }
    }
}

@Composable
private fun DemoDebugContent(s: DemoFlowState, a: DemoFlowActions) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ControlBar(s, a)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { StepPanel(s, a) }
            RoutePreview(s, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun ControlBar(s: DemoFlowState, a: DemoFlowActions) {
    val d = s.demo
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "[${s.step}] ${d.scriptSec / 60}:${"%02d".format(d.scriptSec % 60)} / ${d.scriptTotalSec / 60}분 · " +
                "×${d.speed.toInt()} · 진행 ${(d.courseProgress * 100).toInt()}%" + if (d.synthetic) " · 합성 데이터" else "",
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
        OutlinedButton(onClick = { a.setPlaying(!d.playing) }) { Text(if (d.playing) "일시정지" else "재생") }
        listOf(1.0, 2.0, 4.0).forEach { b ->
            val chosen = d.boost == b
            if (chosen) Button(onClick = { a.setBoost(b) }) { Text("×${b.toInt()}") }
            else OutlinedButton(onClick = { a.setBoost(b) }) { Text("×${b.toInt()}") }
        }
        OutlinedButton(onClick = a::skipToCourseStart) { Text("시작점으로") }
        OutlinedButton(onClick = a::skipToNearFinish) { Text("도착 직전으로") }
        OutlinedButton(onClick = a::restart) { Text("처음부터") }
    }
}

@Composable
private fun StepPanel(s: DemoFlowState, a: DemoFlowActions) {
    when (s.step) {
        DemoStep.REGION -> {
            Title("1·2. 지역 입력 + 코스 선택")
            Text("입력: \"${s.region.query}\"")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { a.onQueryChange("경기도") }) { Text("\"경기도\" 입력") }
                OutlinedButton(onClick = { a.onQueryChange("부천") }) { Text("\"부천\" 입력") }
                OutlinedButton(onClick = a::onClearQuery) { Text("지우기") }
            }
            s.courses.errorMessage?.let { Text(it, color = Color(0xFFD32F2F)) }
            s.courses.courses.forEach { c ->
                val label = "${c.label}. ${c.name} · ${"%.1f".format(c.distanceKm)}km · 약 ${c.estimatedMinutes}분" +
                    if (c.enabled) "" else " (준비 중)"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { a.onCoursePreview(c.id) }) { Text("미리보기") }
                    FilledTonalButton(onClick = { a.onCourseChosen(c.id) }, enabled = c.enabled) { Text(label) }
                }
            }
        }
        DemoStep.LENGTH -> {
            Title("2-1. 코스 길이 — ${s.length.course?.label} ${s.length.course?.name}")
            s.length.options.forEach { o ->
                val chosen = o.id == s.length.selectedId
                val text = "${o.title} · ${"%.1f".format(o.course.distanceKm)}km · 약 ${o.course.estimatedMinutes}분 · ${o.description}" +
                    if (o.recommended) " · 추천" else ""
                if (chosen) Button(onClick = { a.onLengthSelected(o.id) }) { Text("✓ $text") }
                else OutlinedButton(onClick = { a.onLengthSelected(o.id) }, enabled = o.course.enabled) { Text(text) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = a::onLengthBack) { Text("이전") }
                Button(onClick = a::onLengthConfirmed) { Text("확인") }
            }
        }
        DemoStep.CONFIRM -> {
            val c = s.confirm.course
            Title("3. ${c?.label} ${c?.name?.removeSuffix(" 코스")} 경로로 안내합니다")
            Text("${s.confirm.regionName} ${c?.name} · ${c?.distanceKm?.let { "%.1f".format(it) }}km · 약 ${c?.estimatedMinutes}분")
            Text("먼저 코스 시작점까지 안내해 드릴게요")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = a::onCancel) { Text("취소") }
                Button(onClick = a::onStartGuidance, enabled = s.confirm.canStartGuidance) { Text("확인") }
            }
        }
        DemoStep.APPROACH -> {
            val p = s.approach
            Title("4. 시작점까지 안내 (${p.guidance})")
            Big("${p.distanceToStartKm?.let { "%.1f".format(it) } ?: "-"}km · 약 ${p.minutesToStart ?: "-"}분")
            p.nextTurn?.let { Text("${it.distanceM}m 앞 ${it.text} ${it.roadName}", fontSize = 20.sp) }
            if (p.showRecordingHint) {
                Text("시작점에 도착하면 기록이 시작돼요")
                Button(onClick = a::onDismissRecordingHint) { Text("확인") }
            }
            OutlinedButton(onClick = a::onCancelGuidance) { Text("안내 취소") }
        }
        DemoStep.DRIVING -> {
            val d = s.driving
            Title("5. ${d.courseName} · 기록 중")
            d.nextTurn?.let { Big("${it.distanceM}m 앞에서 ${it.text}") ; if (it.roadName.isNotBlank()) Text(it.roadName) }
            Text(
                "도착 ${d.arrivalAtEpochMs?.let(::clock) ?: "-"} · 남은 ${d.remainingKm?.let { "%.1f".format(it) } ?: "-"}km · " +
                    "${d.remainingMinutes ?: "-"}분 남음",
                fontSize = 20.sp,
            )
            Text("달린 거리 ${"%.1f".format(d.drivenKm)}km · ${d.elapsedSec / 60}분 ${d.elapsedSec % 60}초 · ${d.speedKph?.toInt() ?: "-"}km/h")
            OutlinedButton(onClick = a::onEndGuidance) { Text("안내 종료") }
        }
        DemoStep.RESULT -> {
            val r = s.result
            Title("6. 주행 완료 · ${r.courseName}")
            Big("${"%.1f".format(r.distanceKm)}km · ${r.durationSec / 3600}:${"%02d".format(r.durationSec / 60 % 60)}:${"%02d".format(r.durationSec % 60)}")
            val eff = r.efficiency
            Text(
                (eff?.let { "%.2f".format(it.value) + if (it.unit == EfficiencyUnit.KM_PER_KWH) "km/kWh" else "km/L" } ?: "효율 없음") +
                    " (${eff?.source}) · ${r.regionName}",
                fontSize = 20.sp,
            )
            Text("${date(r.startedAtEpochMs)} ~ ${clock(r.endedAtEpochMs)}")
            Text("QR: ${r.resultUrl} (${r.upload})")
            s.demo.driveResultJson?.let { Text(it.take(600), fontSize = 11.sp) }
            Button(onClick = a::onDone) { Text("처음으로") }
        }
    }
}

/** 지도 대신 선만 그린다: 회색 코스 모양, 파란 접근 경로, 빨간 달린 길, 점 = 현재 위치 */
@Composable
private fun RoutePreview(s: DemoFlowState, modifier: Modifier) {
    val (course, approach, driven, current, start) = when (s.step) {
        DemoStep.REGION -> {
            val c = s.courses.selectedCourse ?: s.courses.courses.firstOrNull()
            Lines(c?.shape.orEmpty(), emptyList(), emptyList(), null, c?.shape?.firstOrNull())
        }
        DemoStep.LENGTH -> Lines(s.length.selected?.course?.shape.orEmpty(), emptyList(), emptyList(), null, null)
        DemoStep.CONFIRM -> Lines(s.confirm.course?.shape.orEmpty(), emptyList(), emptyList(), null, null)
        DemoStep.APPROACH -> Lines(s.driving.courseShape, s.approach.routeToStart, emptyList(), s.approach.currentPosition, s.approach.startPoint)
        DemoStep.DRIVING -> Lines(s.driving.courseShape, emptyList(), s.driving.drivenPath, s.driving.currentPosition, null)
        DemoStep.RESULT -> Lines(s.result.courseShape, emptyList(), s.result.drivenPath, null, null)
    }
    // 틀은 코스·접근 경로(바뀌지 않는 선)로만 잡아 매 프레임 다시 계산하지 않는다
    val frame = remember(course, approach, start) {
        (course + approach + listOfNotNull(start)).takeIf { it.isNotEmpty() }?.let { MapFrame.fit(it) }
    }
    Canvas(modifier.background(Color(0xFF1B1F27))) {
        val f = frame ?: return@Canvas
        // 0~1 좌표를 가운데 정사각형에 그린다 (가로세로 비율 유지, 잘리지 않게)
        val side = minOf(size.width, size.height)
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        fun o(p: MapPoint) = f.toUnit(p).let { (x, y) -> Offset(left + x * side, top + y * side) }
        line(course.map(::o), Color(0xFF8A93A6), 5f)
        line(approach.map(::o), Color(0xFF3D7BFF), 7f)
        line(driven.map(::o), Color(0xFFE5484D), 7f)
        start?.let { drawCircle(Color(0xFFE5484D), 10f, o(it)) }
        current?.let { drawCircle(Color.White, 12f, o(it)); drawCircle(Color(0xFF3D7BFF), 8f, o(it)) }
    }
}

private data class Lines(
    val course: List<MapPoint>,
    val approach: List<MapPoint>,
    val driven: List<MapPoint>,
    val current: MapPoint?,
    val start: MapPoint?,
)

private fun DrawScope.line(points: List<Offset>, color: Color, width: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 22.sp, style = MaterialTheme.typography.titleLarge)

@Composable
private fun Big(text: String) = Text(text, fontSize = 32.sp, modifier = Modifier.fillMaxWidth())

private fun clock(ms: Long) = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(ms))
private fun date(ms: Long) = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(ms))
