# 화면(E) ↔ 시연 흐름(D) 연결 안내

1차 제출 시연용. D가 `DemoFlowViewModel`로 흐름과 데이터를 모두 만들고, 화면은 값을 그리고 버튼에서 함수를 부르기만 한다.

```kotlin
val vm: DemoFlowViewModel = viewModel()
val s by vm.state.collectAsStateWithLifecycle()
val a = vm.actions
when (s.step) {
    DemoStep.REGION -> ...   // 지역 입력 + 코스 선택 (한 화면)
    DemoStep.LENGTH -> ...   // 코스 길이 선택
    DemoStep.CONFIRM -> ...  // 선택 확인
    DemoStep.APPROACH -> ... // 시작점까지 안내
    DemoStep.DRIVING -> ...  // 주행 기록 중
    DemoStep.RESULT -> ...   // 도착 결과 + QR
}
```

화면을 바꾸는 것은 ViewModel이 한다(`s.step`). 화면 쪽 `remember`로 단계를 들고 있을 필요가 없다. 뒤로가기는 아래 표의 함수를 부른다.

## 화면별 값

| E 화면 인자 | 넘길 값 |
|---|---|
| **RegionScreen** query | `s.region.query` → 입력은 `a.onQueryChange(it)`, 지우기 `a.onClearQuery()` |
| 코스 목록 | `s.courses.courses` (`label`, `name`, `distanceKm`, `estimatedMinutes`, `shape`, **`enabled`**) |
| 선택(미리보기) | `s.courses.selectedCourseId` → 누르면 `a.onCoursePreview(id)` |
| 코스 고르기 | `a.onCourseChosen(id)`. `enabled = false`(준비 중)면 넘어가지 않고 `s.courses.errorMessage`에 "준비 중인 코스예요" |
| **CourseLengthScreen** courseTitle | `"${s.length.course?.label} ${s.length.course?.name}"` |
| options | `s.length.options` (`title`, `description`, `course.distanceKm`, `course.estimatedMinutes`, `recommended`, `course.shape`, `course.enabled`) |
| selectedId / 고르기 / 확인 / 이전 | `s.length.selectedId` / `a.onLengthSelected(id)` / `a.onLengthConfirmed()` / `a.onLengthBack()` |
| **ConfirmScreen** title·summary | `s.confirm.course` (`label`, `name`, `distanceKm`, `estimatedMinutes`), `s.confirm.regionName` |
| 확인 / 취소 | `a.onStartGuidance()` / `a.onCancel()` |
| **StartGuidanceScreen** distanceToStart / eta | `s.approach.distanceToStartKm` / `s.approach.minutesToStart` (재생 중 계속 줄어듦) |
| route / start / currentPosition | `s.approach.routeToStart` / `s.approach.startPoint` / `s.approach.currentPosition` (+ 코스 모양 `s.driving.courseShape`) |
| 회전 안내 (선택) | `s.approach.nextTurn` (`distanceM`, `text`, `roadName`) |
| 확인(문구 닫기) / 뒤로 | `a.onDismissRecordingHint()` / `a.onCancelGuidance()` |
| **주행 화면** (아직 없음) | `s.driving`: `nextTurn`(450m 앞 우회전), `arrivalAtEpochMs`, `remainingKm`, `remainingMinutes`, `drivenKm`, `elapsedSec`, `speedKph`, `courseShape`, `drivenPath`, `currentPosition`. 안내 종료 `a.onEndGuidance()` |
| **ResultScreen** | `s.result`: `courseName`, `distanceKm`, `durationSec`, `efficiency`(`value`, `unit`), `regionName`, `startedAtEpochMs`~`endedAtEpochMs`, `drivenPath`, `courseShape`, `resultUrl`(QR 주소). 닫기 `a.onDone()` |

시작점 안내 화면은 시작점에 들어서면 자동으로 주행 화면(`DRIVING`)으로 바뀐다. "확인" 버튼으로 넘기지 않는다.

## 지도 좌표

값은 모두 위도·경도(`MapPoint`)다. 지도 사진 위 0~1 좌표가 필요하면:

```kotlin
val frame = MapFrame.fit(s.driving.courseShape + s.approach.routeToStart)   // 한 화면에 담을 점 전부
val unit = frame.toUnit(point)  // (x, y) 0~1, 북쪽이 위
Offset(unit.first, unit.second)
```

⚠️ 지금 지도 사진(`map_suwon.png`)은 수원이라 부천 코스와 맞지 않는다. 부천 지도 사진이 생기기 전까지는 사진 없이 선만 그리거나, 사진의 위경도 범위를 알려 주면 `MapFrame`을 그 범위에 맞추는 함수를 D가 추가한다.

## 효율 표기

`efficiency.unit`이 `KM_PER_KWH`(전기차, 시연 영상)면 "km/kWh", `KM_PER_L`이면 "km/L". 차량 종류는 재생 파일의 `energy_type`으로 정해진다.

## 시연 조작 (발표자용, 화면 구석 숨김 버튼 등)

`a.setPlaying(bool)`, `a.setBoost(1.0/2.0/4.0)`, `a.skipToCourseStart()`, `a.skipToNearFinish()`, `a.restart()`. 현재 값은 `s.demo` (`speed`, `courseProgress`, `scriptSec`, `synthetic`).

## 아직 임시인 것

- `ui/demo/DemoDebugScreen.kt`와 `MainActivity`가 디버그 화면을 띄우는 부분: E 화면이 연결되면 지운다.
- QR 주소 `DemoConfig.resultUrl`: 시연용 폰 웹 주소가 정해지면 바꾼다.
