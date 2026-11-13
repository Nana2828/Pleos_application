<p align="center">
  <img src="docs/images/icon.png" width="140" alt="IDID 앱 아이콘" />
</p>

<h1 align="center">IDID (I drive, I draw)</h1>

<p align="center">
  차 안에서 모양 코스를 골라 달리며 지도에 그림을 그리고,<br/>
  결과 QR로 친구와 기록을 비교하는 드라이브 앱
</p>

<p align="center">
  <img src="docs/images/overview.png" width="800" alt="IDID 전체 흐름" />
</p>

차 인포테인먼트 화면에서 **지역별 드라이브 코스(하트, 고양이, 강아지 꼬리 모양 등)** 를 골라 달리고, 도착하면 결과 카드와 **QR** 로 주행 기록을 폰에서 확인·공유하는 앱이에요. Pleos 공모전 팀 프로젝트입니다.

> 현재 버전은 **시연(데모) 버전**이에요. 코스 안내 데이터와 가짜 주행 신호가 앱 안(`assets/demo`)에 들어 있어서, **서버나 인터넷 없이** 앱만 설치하면 처음부터 끝까지 한 바퀴를 체험할 수 있어요.

## 데모 화면

<p align="center">
  <img src="docs/ui/page-2.png" width="48%" alt="코스 선택" />
  <img src="docs/ui/page-5.png" width="48%" alt="주행 기록" />
  <img src="docs/ui/page-6.png" width="48%" alt="결과 QR" />
  <img src="docs/ui/page-8.png" width="48%" alt="친구 비교" />
</p>

<p align="center"><sub>코스 선택 · 주행 기록 · 결과 QR · 친구 비교 (전체 8장: <a href="docs/ui/">docs/ui/</a>)</sub></p>

## 데모 흐름 (수원 하트 코스)

IDID(I drive, I draw)는 차량 인포테인먼트에서 지역과 모양 코스를 고르고, 달리며 지도에 그림을 그리고, 도착 후 결과 QR로 친구와 기록을 비교하는 서비스입니다.

1. **지역 입력**: 인포테인먼트가 "어디로 가시겠어요?"라고 묻고, 운전자가 "수원"이라고 말합니다.
2. **코스 선택**: 수원 근처 모양 코스(표범, 하트, 촛불)가 지도 위 미리보기와 함께 나오고, 운전자가 "하트 코스"를 고릅니다.
3. **경로 생성**: 선택한 지역과 코스가 JSON으로 FastAPI 서버에 전달되고, 서버가 DB의 프리셋 코스로 경로, 지도, 모양 데이터를 만들어 응답합니다.
4. **시작점까지 안내**: 확인을 누르면 코스 시작점까지 안내합니다(NaviHelper SDK).
5. **주행 기록**: 코스에 처음 진입하면 기록이 시작됩니다. 이동 경로, 평균 연비, 주행 시간, 거리를 저장합니다(NaviHelper, FusedLocation, Vehicle SDK).
6. **결과 화면**: 도착하면 지도 위에 그려진 경로와 연비, 거리, 시간이 나오고 결과 QR이 표시됩니다. 기록은 서버에 저장됩니다.
7. **내 결과 확인**: 스마트폰으로 QR을 찍으면 내 결과 페이지(거리, 시간, 연비, 경로)가 열립니다.
8. **친구 비교**: "11월 드라이브왕" 화면에서 내 최고 기록과 1~3위 시상대, 연비·누적시간·주행거리 부문 순위, 내 최근 기록을 봅니다

## 구성

| 구성요소 | 역할 |
|---|---|
| 인포테인먼트 (Pleos 앱) | 지역 입력, 코스 선택, 안내, 주행 기록, 결과 QR |
| 운전자 휴대폰 (웹) | 결과 확인, 친구 등록, 비교 |
| FastAPI 서버 | 코스 조회, 경로 생성, 기록 가공, 랭킹 계산 |
| DB | 프리셋 코스, 주행 기록, 친구 관계 |

## 준비물

| 항목 | 버전 |
|---|---|
| Android Studio | 최신 안정 버전 (Koala 이상 권장) |
| JDK | 17 (Android Studio 내장 JDK 사용 가능) |
| Android SDK | API 34 (compileSdk / targetSdk 34, minSdk 28) |
| Gradle | 8.9 (프로젝트의 `gradlew`가 알아서 내려받아요) |
| 에뮬레이터 | **Pleos Connect Emulator (API 34)** 권장 |

> 처음 빌드할 때 Gradle과 라이브러리를 내려받으니 **인터넷 연결**이 필요해요. Pleos Vehicle SDK는 `nexus-playground.pleos.ai` 저장소에서 받아요 (`android/settings.gradle.kts`에 이미 설정되어 있어요).

## 실행 방법

### 1. 내려받기

```bash
git clone https://github.com/Nana2828/Pleos_application.git
cd Pleos_application
```

### 2. Android Studio에서 열기

**File → Open** 에서 저장소 전체가 아니라 **`android/` 폴더** 를 선택해요. (Gradle 프로젝트가 `android/` 안에 있어요.) 열면 Gradle Sync가 자동으로 시작돼요. 끝날 때까지 기다려 주세요.

### 3. Pleos Connect Emulator 준비

Pleos 공식 문서의 안내에 따라 Pleos Connect Emulator(API 34)를 설치하고 실행해 주세요. 에뮬레이터가 켜져 있으면 Android Studio 상단의 기기 목록에 나타나요.

### 4. 앱 실행

Android Studio에서 기기를 에뮬레이터로 고르고 ▶ **Run** 을 누르면 돼요. 명령줄로 하려면:

```bash
cd android
./gradlew installDebug      # 켜져 있는 에뮬레이터에 설치
./gradlew assembleDebug     # APK만 만들기: app/build/outputs/apk/debug/app-debug.apk
```

Windows는 `./gradlew` 대신 `gradlew.bat` 을 써요.

### 5. 테스트 (선택)

```bash
cd android
./gradlew test
```

주행 계산, 코스 매칭, 안내, 화면 상태 로직에 대한 단위 테스트가 들어 있어요.

## 결과 QR 설정 (선택)

앱만 실행해도 코스 주행까지는 모두 동작해요. 결과 화면의 QR이 **폰 웹 페이지** 를 열도록 하려면 폰 웹 서버 주소를 알려줘야 해요.

1. 폰 웹 서버를 노트북에서 실행해요. 폰 웹(`web/drive-run-web`)은 이 저장소에 아직 포함되어 있지 않을 수 있으니 팀의 웹 담당에게 받아 주세요. 실행하면 `Wi-Fi: http://192.168.x.x:5173` 같은 주소가 나와요.
2. `android/local.properties` 파일에 아래 한 줄을 추가해요. (git에 올라가지 않는 파일이에요.)
   ```properties
   pleos.webUrl=http://192.168.x.x:5173/
   ```
3. 다시 빌드·설치해요: `./gradlew installDebug`
   (한 번만 다른 주소로 빌드하려면 `./gradlew installDebug -Ppleos.webUrl=http://...`)

주의할 점:
- 폰과 노트북이 **같은 와이파이** 에 있어야 QR이 열려요.
- 와이파이가 바뀌면 노트북 IP도 바뀌니 2번 값을 고치고 다시 빌드해요.
- 값이 없거나 형식이 틀리면(`http://` 누락, `?`·`#` 포함) QR 자리에 "웹 주소가 설정되지 않았어요"가 표시돼요. 이때도 앱의 나머지 기능은 정상 동작해요.

## 문제 해결

| 증상 | 해결 |
|---|---|
| Windows에서 사용자 폴더 이름이 한글이라 빌드 실패 | 환경 변수로 영문 경로를 지정해요. `GRADLE_USER_HOME=C:/Android/.gradle`, `PLEOS_BUILD_DIR=C:/Android/build/pleos-drive` (이때 APK는 `PLEOS_BUILD_DIR` 아래에 생겨요) |
| Gradle Sync에서 `ai.pleos.playground:Vehicle` 을 못 찾음 | 인터넷 연결과 사내망/방화벽에서 `nexus-playground.pleos.ai` 접근이 막혀 있지 않은지 확인해요 |
| `pleos.webUrl 이 없어요` 경고 | QR 설정을 하지 않았다는 안내예요. QR을 쓰지 않으면 무시해도 돼요 |
| JDK 관련 오류 | Android Studio → Settings → Build Tools → Gradle → Gradle JDK 를 17로 맞춰요 |

## 폴더 구조

```
Pleos_application/
├── android/     인포 앱 (Kotlin, Jetpack Compose, MVVM)  ← 지금 실행하는 부분
│   ├── app/src/main/java/ai/pleosapp/drive/
│   │   ├── core/    주행 계산, 코스 매칭, 내비 안내, 가짜 주행 재생
│   │   └── ui/      화면(지역·미리보기·확인·주행·결과)과 화면 상태
│   ├── app/src/main/assets/demo/   시연용 코스 · 경로 · 주행 신호 데이터
│   └── tools/demo/                 시연 데이터를 만드는 Python 스크립트
├── server/      API 서버 (FastAPI)
├── simulator/   가상주행
├── data/        경로 프리셋, DB
├── web/         폰 웹 (결과, 친구 비교, QR)
├── shared/      공유 JSON 규격
└── docs/        기획, 시나리오, 역할, UI 시안
```

## 기술 스택

Kotlin 2.0.21 · Jetpack Compose (BOM 2024.12.01, Material3) · MVVM (ViewModel + StateFlow) · kotlinx.serialization · ZXing(QR) · Pleos Vehicle SDK 2.3.2 · AGP 8.7.3

## 시연 데이터 만들기 (선택)

`android/tools/demo/build_demo_data.py` 가 코스 JSON을 받아 안내 경로와 가짜 주행 신호를 만들어요. OSRM 공개 서버를 호출하므로 인터넷이 필요하고, 앱 실행에는 필요하지 않아요.

```bash
python -I android/tools/demo/build_demo_data.py <코스 JSON> [--out <폴더>]
```

## 더 알아보기

- 팀 규칙·브랜치·PR: [CONTRIBUTING.md](CONTRIBUTING.md)
- 역할 분담: [docs/team-roles.md](docs/team-roles.md)
- 시나리오: [docs/scenario-flow.md](docs/scenario-flow.md)
- 안드로이드 앱 상세: [android/README.md](android/README.md)
- 앱 아이콘 원본(낮·밤, PNG·SVG): [docs/icons/](docs/icons/)

## 출처

경로 안내 데이터: © OpenStreetMap contributors (ODbL), OSRM
