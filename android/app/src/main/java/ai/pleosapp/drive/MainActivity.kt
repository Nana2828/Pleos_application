package ai.pleosapp.drive

import ai.pleosapp.drive.ui.demo.DemoDebugScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * 앱 진입점.
 * TEMP(D): 지금은 시연 흐름 확인용 디버그 화면(DemoDebugScreen)을 띄운다.
 * 실제 화면과 테마, 화면 이동은 E가 `ui/`에서 만들고, DemoFlowViewModel에 연결하면 여기서 바꾼다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DemoDebugScreen() }
    }
}
