package ai.pleosapp.probe

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 시험 전용 화면. 앱을 켜면 Pleos Vehicle SDK와 AAOS Car API 값을 10초마다 읽어 화면과 logcat(태그 VehicleProbe)에 남긴다.
 * 정차 중 개발자 확인용이며 운전자에게 보이는 화면이 아니다.
 */
class ProbeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private lateinit var pleos: PleosReader
    private lateinit var aaos: AaosReader
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
    private var round = 0
    private var odometerKm: Double? = null
    private var start: Efficiency.Snapshot? = null

    private val tick = object : Runnable {
        override fun run() {
            round += 1
            log("── ${round}회차 읽기 ──")
            pleos.readAll()
            aaos.readAll().forEach(::log)
            logEfficiency()
            handler.postDelayed(this, PERIOD_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setPadding(24, 24, 24, 24)
        }
        scroll = ScrollView(this).apply { addView(output) }
        setContentView(scroll)

        pleos = PleosReader(this, ::log) { km -> handler.post { odometerKm = km } }
        aaos = AaosReader(this)

        pleos.initialize()
        aaos.connect(::log)
        // initialize()에 연결 완료 콜백이 없어 잠시 기다린 뒤 확인한다.
        handler.postDelayed({
            pleos.checkCapabilities()
            pleos.registerListeners()
            handler.post(tick)
        }, CONNECT_WAIT_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pleos.release()
        aaos.release()
        super.onDestroy()
    }

    /** 앱을 켠 뒤 처음 값이 모두 모인 시점을 시작으로 보고, 지금까지의 평균 효율을 계산한다. */
    private fun logEfficiency() {
        val electric = aaos.isElectric()
        val now = Efficiency.Snapshot(odometerKm, aaos.batteryWh(), aaos.fuelMl())
        val first = start
        if (first == null) {
            if (now.odometerKm != null && (now.batteryWh != null || now.fuelMl != null)) {
                start = now
                log("계산 시작점 저장: $now, 전기차=$electric")
            } else {
                log("계산 시작점 대기: $now")
            }
            return
        }
        val result = Efficiency.between(first, now, electric ?: false)
        log("계산 효율 = ${result?.let { "%.2f %s (%.1fkm)".format(it.value, it.unit.code, it.distanceKm) } ?: "아직 없음"} / 지금 $now")
    }

    /** SDK 콜백이 어느 스레드에서 오는지 몰라 화면 갱신은 메인 스레드로 넘긴다. */
    private fun log(line: String) {
        Log.i(TAG, line)
        handler.post {
            output.append("${clock.format(Date())}  $line\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    companion object {
        private const val TAG = "VehicleProbe"
        private const val PERIOD_MS = 10_000L
        private const val CONNECT_WAIT_MS = 1_500L
    }
}
