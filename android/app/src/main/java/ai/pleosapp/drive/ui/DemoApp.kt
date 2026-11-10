package ai.pleosapp.drive.ui

import ai.pleosapp.drive.ui.confirm.ConfirmScreen
import ai.pleosapp.drive.ui.driving.DrivingScreen
import ai.pleosapp.drive.ui.guidance.StartGuidanceScreen
import ai.pleosapp.drive.ui.length.CourseLengthScreen
import ai.pleosapp.drive.ui.region.RegionScreen
import ai.pleosapp.drive.ui.result.ResultScreen
import ai.pleosapp.drive.ui.state.DemoFlowViewModel
import ai.pleosapp.drive.ui.state.DemoStep
import ai.pleosapp.drive.ui.theme.PleosColors
import ai.pleosapp.drive.ui.theme.PleosTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 인포 화면 전체. 화면 이동과 데이터는 모두 D 의 [DemoFlowViewModel] 이 정하고(state.step),
 * 여기서는 단계에 맞는 화면에 값과 동작을 넘겨주기만 해요. (ui/state/CONNECT_SCREENS.md)
 */
@Composable
fun DemoApp(vm: DemoFlowViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val error by vm.loadError.collectAsStateWithLifecycle()
    val a = vm.actions

    // 시스템 뒤로가기 = 화면의 이전/취소 버튼과 같은 동작
    when (s.step) {
        DemoStep.LENGTH -> BackHandler(onBack = a::onLengthBack)
        DemoStep.CONFIRM -> BackHandler(onBack = a::onCancel)
        DemoStep.APPROACH -> BackHandler(onBack = a::onCancelGuidance)
        DemoStep.RESULT -> BackHandler(onBack = a::onDone)
        DemoStep.REGION, DemoStep.DRIVING -> Unit
    }

    PleosTheme {
        when (s.step) {
            DemoStep.REGION -> RegionScreen(s.region, s.courses, a, a)
            DemoStep.LENGTH -> CourseLengthScreen(s.length, a)
            DemoStep.CONFIRM -> ConfirmScreen(s.confirm, a)
            DemoStep.APPROACH -> StartGuidanceScreen(s.approach, s.driving.courseShape, a)
            DemoStep.DRIVING -> DrivingScreen(s.driving, a)
            DemoStep.RESULT -> ResultScreen(s.result, a)
        }
        error?.let {
            Box(Modifier.fillMaxSize().background(PleosColors.Rail), contentAlignment = Alignment.Center) {
                Text(it, color = PleosColors.DangerText, fontSize = 28.sp)
            }
        }
    }
}
