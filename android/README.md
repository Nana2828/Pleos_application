# 📱 android/ — 인포 앱

담당: D 재원(코어), E 나연(화면)

- 기술: Jetpack Compose, MVVM(ViewModel + StateFlow), Retrofit + OkHttp, kotlinx.serialization
- Pleos SDK: NaviHelper(경로 안내), Vehicle `ai.pleos.playground:Vehicle:2.3.2`(연비·거리·시간)
- 테스트: Pleos Connect Emulator(API 34)

## ▶️ 열기·빌드·실행 (PLEOS-33)

프로젝트는 이미 만들어져 있어요. 새로 만들지 말고 **Android Studio → Open → 이 `android/` 폴더**를 열어요.

| 항목 | 값 |
|---|---|
| 패키지 | `ai.pleosapp.drive` (앱 이름이 정해지면 바꿀 수 있어요) |
| minSdk / targetSdk / compileSdk | 28 / 34 / 34 |
| 도구 | Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, JDK 17 |
| Compose | BOM 2024.12.01, Material3 |

```bash
cd android
./gradlew assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # 켜져 있는 에뮬레이터에 설치
```

- 실행하면 앱 이름만 보이는 빈 화면이 떠요. 진짜 화면은 `ui/`에서 만들어요.
- 권한은 지금 Vehicle SDK용(`pleos.car.permission.CAR_ENERGY`, `CAR_DRIVE`, `android.car.permission.CAR_ENERGY`)만 있어요. 내비·위치·통신 권한은 각 티켓에서 추가해요.
- **Windows에서 사용자 폴더 이름이 한글이면** 빌드가 실패할 수 있어요. 그럴 땐 환경 변수로 영문 경로를 지정해요.
  - `GRADLE_USER_HOME=C:/Android/.gradle` (Gradle 캐시)
  - `PLEOS_BUILD_DIR=C:/Android/build/pleos-drive` (빌드 결과물. 이때 APK는 이 폴더 아래에 생겨요)

## 패키지 나누기 (충돌 줄이기)

```
app/src/main/java/ai/pleosapp/drive/
├── MainActivity.kt  # 앱 진입점
├── core/        # D 재원: SDK 연결, 서버 통신, 주행 기록, 가상주행 모드
│   ├── navi/
│   ├── vehicle/
│   ├── network/
│   └── model/   # shared/ JSON과 같은 모양의 data class
└── ui/
    ├── state/       # D 재원: 화면별 UiState·Actions·ViewModel, 가짜 데이터(FakeUiStates) (PLEOS-58)
    ├── region/      # E 나연: PLEOS-42 지역 입력
    ├── courses/     # E 나연: PLEOS-43 코스 목록·미리보기
    ├── confirm/     # E 나연: PLEOS-44 안내 확인 (+ 시작점까지 안내)
    ├── driving/     # E 나연: PLEOS-45 주행 중
    ├── result/      # E 나연: PLEOS-46 결과 + QR
    └── theme/       # E 나연
```

### D·E 경계 (가안: D가 ViewModel·상태 담당)

- 화면은 `XxxUiState`를 받아 그대로 그리고, 버튼을 누르면 `XxxActions`의 함수를 불러요.
  - 예: `CoursesScreen(state: CoursesUiState, actions: CoursesActions)`
- 화면을 먼저 만들 때는 `FakeUiStates.courses`, `CoursesActions.Noop`을 넣어요. 값은 UI 시안(`docs/ui/page-1~6`)과 같아요.
- 화면 사이 이동(내비게이션)은 화면 쪽(E)에서 해요. 예: `onRegionSelected`를 부른 뒤 코스 선택 화면으로 이동.
- 상태에는 값과 단위만 있어요. 문구("연비"/"효율"), 숫자 형식, 색은 화면 쪽에서 정해요.
- 값이 더 필요하면 D에게 말해 주세요. `ui/state/`는 D가 고쳐요.

| 화면 | 상태 | 동작 | 가짜 데이터 |
|---|---|---|---|
| 1 지역 입력 | `RegionUiState` | `RegionActions` | `FakeUiStates.region` |
| 2 코스 선택 | `CoursesUiState` | `CoursesActions` | `FakeUiStates.courses` |
| 3 선택 확인 | `ConfirmUiState` | `ConfirmActions` | `FakeUiStates.confirm` |
| 4 시작점까지 안내 | `ApproachUiState` | `ApproachActions` | `FakeUiStates.approach` |
| 5 주행 기록 중 | `DrivingUiState` | `DrivingActions` | `FakeUiStates.driving` |
| 6 도착 결과 | `ResultUiState` | `ResultActions` | `FakeUiStates.result`, `resultUploadFailed` |
