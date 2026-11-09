# 📱 android/ — 인포 앱

담당: D 재원(코어), E 나연(화면)

- Android Studio에서 **이 폴더를 프로젝트 위치로** 새 프로젝트를 만들어요.
  - 템플릿: Car(Automotive) → Empty Activity, 언어 Kotlin, minSdk 28 이상
- 기술: Jetpack Compose, MVVM(ViewModel + StateFlow), Retrofit + OkHttp, kotlinx.serialization
- Pleos SDK: NaviHelper(경로 안내), Vehicle `ai.pleos.playground:Vehicle:2.3.1`(연비·거리·시간)
- 테스트: Pleos Connect Emulator(API 34)

## 패키지 나누기 (충돌 줄이기)

```
app/src/main/java/.../
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
