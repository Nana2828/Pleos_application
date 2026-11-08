# D(인포 앱 코어) 작업 맥락 — AI 작업자용

> AI(Claude Code 등)에게 D 관련 작업을 맡길 때 **이 파일을 먼저 읽게** 하세요. 사람용 설명이 아니라, AI가 같은 실수를 반복하지 않도록 사실·결정·함정을 모은 문서입니다.
> 기준 시점: 2026-11-08. 내용이 바뀌면 이 파일과 맨 아래 변경 이력을 함께 고칩니다.

## 0. 표기 규칙 (신뢰 수준)

| 표기 | 뜻 | AI가 할 일 |
|---|---|---|
| **[확인]** | 에뮬레이터·빌드에서 직접 확인함 (날짜·환경 명시) | 사실로 써도 됨 |
| **[문서]** | Pleos 공식 문서 기준, 직접 확인 안 함 | 쓰되 "문서 기준"이라고 밝힘 |
| **[제안]** | D가 낸 안, 팀 합의 전 | 본문에 확정처럼 쓰지 말 것 |
| **[미정]** | 아직 아무도 정하지 않음 | 지어내지 말고 "합의 필요"라고 멈출 것 |

## 1. 프로젝트와 역할

- 현대자동차그룹 Pleos 차량 앱 공모전. 접수 마감 **2026-11-15**, 2차 PT(동작 시연 필수) 2026-12-03~04.
- 앱 흐름: 인포테인먼트에서 지역 입력 → 모양 코스 선택 → NaviHelper로 시작점까지 안내 → 코스 진입 시 기록 시작 → 도착 후 결과 QR → 폰 웹에서 친구와 비교. 근거: `docs/scenario-flow.md`
- 역할(`docs/team-roles.md`): A 기획·관리, B 지도 데이터·DB, C FastAPI 서버·가상주행, **D 인포 앱 코어(지도 표시·경로 안내·주행 기록)**, E 화면 UI·폰 웹.
- 이 문서의 작성자는 D 관점으로 작업했습니다. D 담당 확정은 **[미정]**.
- **다른 역할의 영역(B·C·E·A)은 직접 고치지 말고 제안으로 남깁니다.**

## 2. 현재 상태 (2026-11-08)

| 항목 | 상태 |
|---|---|
| 브랜치 `feature/d-vehicle-probe` | 차량 값 확인 시험 앱 `spikes/vehicle-probe/`. 로컬 커밋, push 전 |
| 브랜치 `docs/d-data-contracts` | 데이터 규격 초안 `docs/data-contracts.md` + 예시 `docs/contracts/samples/`. 로컬 커밋, push 전 |
| `app/` (팀 앱 본체) | **아직 없음.** D·E가 같이 쓰는 폴더라 구조를 먼저 상의해야 함 **[미정]** |
| 서버 `server/`, 데이터 `data/` | 없음 (C, B 담당) |
| 이전 시험 앱 `route-draw` | 팀 저장소 밖(작성자 PC)에 있음. 코스 그리기·NaviHelper 연동·주행 기록(세션·저장·복구·재생, 단위 테스트 89개). 팀 저장소에는 아직 안 옮김 |

## 3. 확인된 사실 — 차량 값 [확인 2026-11-08, Emulator v2.1.0, Vehicle SDK 2.3.2]

상세와 재현 방법: `spikes/vehicle-probe/README.md`

- **에뮬레이터 차는 전기차**입니다. AAOS `INFO_FUEL_TYPE` = `[10]`, 배터리 77.4kWh.
- **안 읽힘** (`ERROR_NOT_SUPPORTED_PROPERTY`):
  - Fuel 연비 4종과 `getFuelPercent`
  - EvBattery `getCurrentEvEfficiency`
  - Odometer `getTripCurrentDrivingDistance` / `getTripCurrentDrivingTime`
- **읽힘**:
  - Vehicle SDK Odometer `getCurrentDistanceDriven`과 `registerDistanceDriven`: km, 0.1 단위, 이벤트 약 4~5초 간격
  - AAOS `EV_BATTERY_LEVEL`: Wh. `android.car.permission.CAR_ENERGY`가 dangerous 권한이라 실행 중에 요청해야 함
- AAOS `PERF_ODOMETER`는 `CAR_MILEAGE`가 시스템 권한이라 일반 앱은 읽을 수 없습니다.
- Vehicle SDK 예외 클래스는 난독화되어 있습니다(`...callback.exception.a`). 오류 코드는 `toString()`에 문자열로 들어 있습니다.
- `Vehicle.initialize()`에는 연결 완료 콜백이 없습니다. 시험 앱은 1.5초 기다린 뒤 호출했습니다.
- 실차, 특히 내연기관에서 연비가 읽히는지는 **[미정]**(확인 안 됨)입니다.

**효율 계산 [확인]:** 주행 전후 `누적 주행거리 차이 ÷ 배터리(또는 연료) 사용량`. `spikes/vehicle-probe/.../Efficiency.kt`에 있고 단위 테스트 7개가 있습니다. 콘솔에 3.0km와 0.5kWh 사용을 넣자 앱이 6.00km/kWh로 계산했습니다(1회).

## 4. 확인된 사실 — 가상 콘솔 [확인 2026-11-08]

- `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088`. 쓰기 전 **1초마다** `POST /browser/session?client=<id>` 필요 (3초 끊기면 `emulator_inactive`).
- 쓰기 경로는 공개 문서에 없고 `GET /vehicle/catalog`에서 찾음:
  - 기어 `PUT /vehicle/gear?gear=Drive|Park|Reverse|Neutral`
  - 속도 `PUT /vehicle/controls?speed=&accelerator=&brake=`
  - 누적거리 `PUT /vehicle/odometer?traveled_distance_km=` — **줄이면 400** `traveled_distance_must_not_decrease`
  - 배터리 `PUT /vehicle/ev-battery?field=battery_level&value=<kWh>`
  - 상태 `GET /vehicle/state`
- **속도만 바꾸면 누적거리·배터리는 변하지 않음.** 가상주행은 둘을 직접 넣어야 함 → `spikes/vehicle-probe/tools/console_drive.py`
- 위치는 콘솔이 아니라 `adb emu geo fix <경도> <위도>`.

## 5. 확인된 사실 — NaviHelper 3.0.5 [확인 2026-11-05, 이전 시험 앱]

- 문서는 경유지 **최대 2개**라고 적고 있습니다 **[문서]**. 하지만 에뮬레이터 `requestRouteWithWaypoints`에서는 30개까지 순서대로 들어갔습니다 **[확인]**. 실차와 App Market 심사에서 되는지는 **[미정]**.
- **내비가 그린 경로선 좌표를 받는 API는 없음.** 지도에는 B의 코스 `shape`를 그린다.
- `onRouteEnded`는 경로 시작 직후에도 옴 → **도착 신호로 쓰지 말 것.**
- `onRouteStarted`의 거리는 전체가 아니라 첫 경유지까지 값으로 보임.
- 에뮬레이터 내비는 GPS를 따라 움직이지 않음(판교 근처 고정) → 도착·경유지 통과 이벤트를 에뮬레이터에서 만들 수 없음.
- `addWaypoint`/`removeWaypoint`는 안내를 끊고 재탐색함. 경로 요청 때 내비 앱이 앞으로 나옴.
- `onWayPointInfo`의 위경도는 핀이 달라도 같은 값으로 옴. 믿을 것은 개수와 이름뿐.
- **결론 [제안]:** 코스 진입·이탈·도착은 내비 이벤트가 아니라 **앱이 받은 위치 + 코스 `shape` + `corridor_m`**으로 판정한다.

## 6. 데이터 규격 요약 [제안] — 전체: `docs/data-contracts.md`

| # | 규격 | 흐름 | 핵심 필드 |
|---|---|---|---|
| ① | 코스 JSON `course/1` | B → C → D | `course_id`, `shape`(GeoJSON LineString), `start`/`waypoints`/`destination`, `corridor_m` |
| ② | 주행 신호 JSONL `drive-replay` v2 | 차량 / C 가상주행 → D | 한 줄 `{"t","k","v"}`. `k` = `pos`·`gear`·`speed`·`odo`·`battery`·`fuel`·`fuel_eco`·`ev_eff` |
| ③ | 주행 결과 JSON `drive-result/1` | D → `POST /drive` → C → E | `distance_km`, `duration_sec`, `avg_fuel_economy`(km/L) 또는 `avg_ev_efficiency`, `fuel_economy_source`, `polyline`, 응답 `result_url` |

- **좌표 배열은 항상 `[경도, 위도]`** (GeoJSON). 점 객체는 `{"lat","lon"}`.
- 시각 ISO 8601 + `+09:00`. 단위는 필드 이름에(`_m`, `_km`, `_sec`).
- 합성 데이터는 `"synthetic": true`. 실제 주행으로 오해되지 않게 할 것.

## 7. 앱 코어 설계 방향 [제안]

1. **신호 입력을 한 인터페이스로.** 실차 소스와 재생 소스를 바꿔 끼웁니다(`DriveSignalSource`). 시연은 재생과 콘솔 가상주행으로 하고, 코드 경로는 같게 둡니다.
2. **주행 세션:** 기어 D에서 시작하고 P에서 끝냅니다. 짧은 주행(300m 또는 1분 미만)은 제외하고, 강제 종료 대비로 일지에서 복구합니다. 이전 시험 앱에 구현과 테스트가 있습니다.
3. **효율 순서:**
   1. 차가 주는 값(`vehicle_sdk`)
   2. 전후 차이로 계산(`estimated`)
   3. 둘 다 없으면 null
   
   예시 값을 쓰면 `sample`로 표시합니다.
4. **결과 QR**은 `POST /drive`가 성공한 뒤 `result_url`로 띄웁니다. 실패하면 차에 보관했다가 다시 보냅니다.
5. **SDK 접근은 래퍼로 감쌉니다.** 목업으로 바꿀 수 있어야 합니다.

## 8. 정하지 않은 것 — 지어내지 말 것 [미정]

앱 이름 · D 담당 확정 · `app/` 폴더 구조 · 화면의 "연비/전비/효율" 표기 · 효율 단위와 전기차/내연기관 비교 방식 · 시연 서버 주소(폰이 QR로 접속 가능해야 함) · 위치 궤적 서버 저장 범위와 동의 문구 · 경유지 개수 정책과 Pleos 문의 여부 · 친구 식별 방식(시나리오 7장 "확인 필요")

## 9. 작업 규칙

- **하지 말 것:**
  - 사람이 요청하지 않은 `git push`, `main` 병합, 강제 push
  - 다른 역할 파일 직접 수정 → 제안이나 PR 댓글로 남김
  - 확인 안 한 SDK 동작을 사실처럼 쓰기
- 브랜치 규칙(`team-roles.md`, **[제안]**): `feature/이슈번호-내용`. 이슈가 없어서 지금 브랜치에는 번호가 없습니다.
- 커밋 메시지: `<타입>: <요약>` (`feat` `fix` `docs` `refactor` `chore`). 이전 저장소 관례이고, 팀 합의는 **[미정]**.
- 주행 중 UX: 복잡한 터치, 긴 글, 영상은 금지. 기록은 버튼 없이 자동으로 시작합니다.
- 위치 궤적은 개인정보입니다. 필요한 만큼만 저장하고 보냅니다.
- 심사 제출물(제출용 저장소, 영상, 앱 화면)에 이름·연락처·계정이 드러나면 안 됩니다. 이 작업 저장소를 그대로 제출하지 않습니다.
- 제3자 라이브러리·데이터는 라이선스를 확인하고 출처를 남깁니다. 공개 저장소 "대동차지도"는 라이선스 파일이 없어 **코드를 복사하지 않습니다.**

## 10. 개발 환경 함정 (작성자 PC 기준, Windows)

- 저장소 경로에 한글이 있으면 단위 테스트가 실패한 적이 있습니다. `PROBE_BUILD_DIR`처럼 빌드 산출물을 영문 경로로 보냅니다.
- Git Bash에서는 `MSYS_NO_PATHCONV=1`을 씁니다. 안 쓰면 `/vehicle/...` 같은 인자가 Windows 경로로 바뀝니다.
- 에뮬레이터 앱 사용자는 `--user 10`입니다. 예: `adb shell am start --user 10 -n <패키지>/<액티비티>`, `pm grant --user 10 ...`
- **AVD가 깨지는 경우가 있습니다.** 증상은 ANR, 터치 지연, 콘솔 `emulator_inactive`, logcat `Participant was not created` 반복입니다. 앱 버그로 오해하기 쉬운데, 같은 이미지로 AVD를 새로 만들면 정상이었습니다.
- `adb exec-out` 화면 캡처는 PNG가 깨질 수 있습니다. `screencap -p /data/local/tmp/x.png`로 찍은 뒤 `adb pull`로 받습니다.
- Vehicle SDK 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`, `ai.pleos.playground:Vehicle:2.3.2`. NaviHelper는 `3.0.5`.
- Pleos 문서 사이트(document.pleos.ai)는 클라이언트 렌더링이라 일반 요청으로는 빈 페이지가 옵니다. JS 번들의 경로 표에서 페이지 청크를 찾아 읽습니다.

## 11. 파일 지도

| 파일 | 내용 |
|---|---|
| `docs/scenario-flow.md` | 시나리오, 흐름도, API 초안, SDK 제공 상태 |
| `docs/team-roles.md` | 역할과 역할별 기술 스택 |
| `docs/data-contracts.md` | 데이터 규격 초안 3종과 합의 안건 |
| `docs/contracts/samples/` | `course.json`, `drive-signals.jsonl`, `drive-result.json` |
| `spikes/vehicle-probe/README.md` | 차량 값 실측표, 권한, 콘솔 API, 실행 방법 |
| `spikes/vehicle-probe/app/.../Efficiency.kt` | 효율 계산 (앱 코어로 옮길 대상) |
| `spikes/vehicle-probe/tools/console_drive.py` | 콘솔 가상주행 (기어·속도·위치·누적거리·배터리) |

## 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-11-08 | 처음 작성 (D) |
