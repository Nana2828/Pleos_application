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
└── ui/          # E 나연: 화면과 ViewModel
    ├── region/      # PLEOS-42 지역 입력
    ├── courses/     # PLEOS-43 코스 목록·미리보기
    ├── confirm/     # PLEOS-44 안내 확인
    ├── driving/     # PLEOS-45 주행 중
    ├── result/      # PLEOS-46 결과 + QR
    └── theme/
```

`ui/`는 `core/`의 인터페이스만 써요. `core/`가 아직 없으면 가짜 데이터로 화면부터 만들어요.
