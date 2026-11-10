package ai.pleosapp.drive

import ai.pleosapp.drive.ui.DemoApp
import ai.pleosapp.drive.ui.state.DemoFlowViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

/**
 * 앱 진입점.
 * 화면(ui/DemoApp)이 D 의 DemoFlowViewModel 에 연결돼 있어요. 디버그 화면(DemoDebugScreen)은 더 이상 띄우지 않아요.
 */
class MainActivity : ComponentActivity() {
    private val demo: DemoFlowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DemoApp(demo) }
    }

    override fun onStart() {
        super.onStart()
        demo.onAppForeground()
    }

    override fun onStop() {
        demo.onAppBackground()
        super.onStop()
    }
}
