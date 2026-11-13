# 팀 역할 분담과 기술 스택 (5명, v3)

표기: **[Pleos 공식]** = Pleos 공식 문서에 있는 것, **[추천]** = 일반적인 선택이라 팀이 확정할 것, **[확인 필요]** = 문서로 확인하지 못한 것.

## 1. 역할표
| 역할 | 담당 |
|---|---|
| **A. 기획 + 폰 웹** | 기획서(기술 내용 제외), 폰 웹(결과, 친구 비교, QR), 테스트 체크리스트, 가상주행 지원(B·C와 시나리오·검증 데이터 정리) |
| **B. 지도 데이터·DB** | 경로 프리셋 제작, 경로 JSON 규격 초안, DB 스키마와 프리셋 적재 |
| **C. 서버·가상주행** | FastAPI API, 가상주행과 주행 정보 정리 |
| **D. 인포 앱 코어** | JSON을 받아 지도 표시, 경로 안내, 주행 기록 |
| **E. 인포 화면 UI + 일정·GitHub·Jira 관리** | 인포 화면, 일정, GitHub·Jira 관리(이슈·보드·브랜치 규칙·풀 리퀘스트 확인) |

## 2. 역할별 기술 스택
### A. 기획 + 폰 웹
- 폰 웹: HTML/CSS/JavaScript의 정적 페이지. 결과 QR 주소의 `#` 뒤 값을 브라우저 안에서 풀어 보여 주고, 받은 기록은 `localStorage`에 모아 친구 비교·랭킹을 브라우저에서 계산한다. 주행 기록용 서버 API는 부르지 않는다(데이터 처리 원칙, 시나리오 문서 6·7장). 규모가 커지면 React **[추천]**
- 가상주행 지원: 시연 시나리오(어느 코스를 몇 배속으로 달릴지), 기대 결과값 표, 가상주행 결과와 화면 숫자가 맞는지 검증

### B. 지도 데이터·DB
- 경로 데이터: 좌표 목록 JSON(경로선은 위도·경도 배열, GeoJSON LineString 형식 추천) **[추천]**
- 중요: NaviHelper SDK는 목적지·경유지 설정과 경유지를 포함한 경로 요청을 지원한다. 따라서 프리셋은 **모양 좌표 + 경유지 목록** 두 가지로 만든다 **[Pleos 공식 NaviHelper 기능]**
- 도로에 맞추는 작업: 지도 서비스의 길찾기 결과를 따서 모양에 맞는 경로 만들기, 또는 OSRM 같은 오픈소스 경로 엔진의 맵 매칭 **[추천·확인 필요]**
- DB: 데모는 SQLite, 이후 PostgreSQL로 옮길 수 있게 SQLAlchemy 사용 **[추천]**
- 테이블 초안: 코스(courses)만. 주행 기록·사용자·친구는 데이터 처리 원칙에 따라 서버 DB에 두지 않는다(시나리오 문서 2장)

### C. 서버·가상주행
- Python 3 + FastAPI + Pydantic(요청·응답 형식 검증) + Uvicorn **[추천, 팀이 이미 FastAPI로 합의]**
- DB 접근: SQLAlchemy **[추천]**
- API: `GET /courses?region=`, `GET /courses/{course_id}` (시나리오 문서 6번). 서버는 지역·코스 텍스트만 받고 코스 JSON만 응답한다. 주행 기록 저장·랭킹 API는 두지 않는다
- QR: 차 앱이 결과 요약을 주소의 `#` 뒤에 담아 직접 만든다(ZXing). 서버는 관여하지 않는다
- 가상주행: 프리셋 좌표를 따라 시간 간격으로 위치·속도·연비 값을 만들어 내는 Python 스크립트. 거리는 하버사인 공식으로 계산. 출력 JSON은 D가 실제 SDK로 만드는 기록과 같은 형식 **[추천]**

### D. 인포 앱 코어
- 언어·환경: Kotlin, Android Studio, 프로젝트 템플릿 Car(Automotive)의 Empty Activity, minSdk 28 이상 **[Pleos 공식 설치 가이드]**
- 테스트: Pleos Connect Emulator(API 34, RAM 4GB, Cold boot), Pleos Connect Virtual Console **[Pleos 공식]**
- 경로 안내: **NaviHelper SDK** — `initialize()`, `addListener()`, NaviHelperEventListener, 목적지·경유지 설정, 경로 탐색·주행 상태 이벤트 **[Pleos 공식]**
- 연비·거리·시간: **Vehicle State(Vehicle SDK)** — 의존성 `ai.pleos.playground:Vehicle:2.3.1`, Fuel 도메인(연비), Odometer 도메인(트립 거리·시간), 권한 `pleos.car.permission.CAR_ENERGY`, `pleos.car.permission.CAR_DRIVE`, 사용 전 `checkXXXCapabilities` 확인 **[Pleos 공식]**
- 위치(이동 경로 기록): FusedLocation SDK는 현재 Coming Soon **[Pleos 공식 SDK 메뉴]**. 대체안은 NaviHelper가 주는 내비 정보나 Android 표준 위치 API **[확인 필요]**
- 서버 통신: Retrofit + OkHttp, JSON은 kotlinx.serialization **[추천]**. 보내는 값은 지역·코스 텍스트뿐이고, 주행 기록은 차 안에 저장한다
- 지도 표시: Pleos에는 지도 화면 SDK가 없다고 알려져 있어 외부 지도 라이브러리나 자체 그리기(Canvas)가 필요하다 **[확인 필요]**. 데모는 경로선을 Compose Canvas로 그리는 방법도 가능하다 **[추천]**
- 가상주행 연동: 앱에 "가상주행 모드"를 두어 같은 코드로 가짜 위치·연비를 주입 **[추천]**

### E. 인포 화면 UI + 일정·GitHub·Jira 관리
- 관리: Jira(PLEOS 보드)로 할 일 관리, GitHub Pull Request로 코드 리뷰, 문서는 레포 `docs/`
- 규칙: 브랜치 `feature/PLEOS-번호-내용`, 커밋과 PR 제목 앞에 `PLEOS-번호`, 자세한 건 `CONTRIBUTING.md`
- 인포 화면: Jetpack Compose(Compose for Android Automotive) **[Pleos 안내 + 추천]**, 구조는 MVVM + ViewModel + StateFlow **[추천]**
- 디자인: 지금까지 만든 UI 8장(`docs/ui/`)을 기준으로 구현, 필요하면 Figma **[추천]**
- 주행 중 화면 제한 등 차량 UX 규정: 공식 문서의 차량 앱 기획 가이드를 읽고 반영 **[확인 필요]**

### 담당 미정
- Pleos SDK 상태 확인(PLEOS-51, 52): Pleos 문서(document.pleos.ai)의 SDK 제공 상태(Coming Soon 여부)를 주기적으로 확인하고, 변경이 있으면 팀에 공유 **[Pleos 공식 문서]**
- 발표·데모 시나리오, 데모 영상·리허설(PLEOS-12, 56)

## 3. 공통 규칙
- 레포: `Nana2828/Pleos_application`, 폴더는 `android/`(D·E), `server/`(C), `simulator/`(C), `data/`(B), `web/`(A), `shared/`(B·C·D), `docs/`(A·E)
- 공유 계약: 경로 JSON 규격과 주행 기록 JSON 규격을 `shared/`에 먼저 확정한 뒤 개발. 주행 기록 규격은 서버 전송용이 아니라 차 안 저장·결과 QR 요약·가상주행 검증용이다
- 모든 값(연비·거리 등)은 실제 SDK 값과 가상주행 값을 같은 형식으로 다룬다
