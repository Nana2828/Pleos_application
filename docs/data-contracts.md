# 데이터 규격 (초안 v0.1, D 작성 · B·C·E 검토 필요)

> 💡 **제안 문서입니다.** 역할표 3장 "경로 JSON 규격과 주행 기록 JSON 규격을 `docs/`에 먼저 확정한 뒤 개발"에 따라 D(인포 앱 코어)가 초안을 씁니다. 각 규격의 **만드는 쪽**이 최종 결정권을 갖고, 고칠 곳은 PR 댓글로 남겨 주세요.
> 필드 이름은 `scenario-flow.md` 6장 API 초안(`course_id`, `polyline`, `avg_fuel_economy`, `distance_km`, `duration_sec`, `started_at`, `ended_at`)을 그대로 살리고, 빠진 것만 더했습니다.

| # | 규격 | 만드는 쪽 | 쓰는 쪽 | 예시 파일 |
|---|---|---|---|---|
| 1 | 코스 JSON | B (프리셋 제작) | C (`GET /courses`, `POST /course/route`), D (지도·안내·진입 판정) | `contracts/samples/course.json` |
| 2 | 주행 신호 JSONL | D (실차 기록), C (가상주행) | D (재생·기록 계산) | `contracts/samples/drive-signals.jsonl` |
| 3 | 주행 결과 JSON | D | C (`POST /drive` 저장·랭킹), E (결과 화면·폰 웹) | `contracts/samples/drive-result.json` |

## 0. 공통 규칙

- **좌표 배열은 항상 `[경도, 위도]`** (GeoJSON 순서). 점 하나를 객체로 쓸 때는 `{"lat": .., "lon": ..}`처럼 이름을 붙입니다. 순서를 헷갈려 지도가 바다로 가는 실수가 가장 흔합니다.
- 좌표는 소수 6자리(약 0.1m)까지만 씁니다.
- 시각은 ISO 8601에 시간대를 붙입니다: `2026-11-08T14:03:00+09:00`.
- 거리 단위는 필드 이름에 붙입니다(`_m`, `_km`, `_sec`). 단위 없는 숫자 필드는 만들지 않습니다.
- 모든 최상위 객체에 `schema` 필드를 둡니다(예: `"course/1"`). 형식이 바뀌면 숫자를 올립니다.
- 오류 응답(C): `{"error": {"code": "COURSE_NOT_FOUND", "message": "사람이 읽는 설명"}}` + 적절한 HTTP 상태 코드.

## 1. 코스 JSON (B → C → D)

```json
{
  "schema": "course/1",
  "course_id": "suwon-heart",
  "region": "수원",
  "label": "B",
  "name": "하트",
  "shape": { "type": "LineString", "coordinates": [[127.0101, 37.2801], [127.0123, 37.2834]] },
  "start": { "lat": 37.2801, "lon": 127.0101, "name": "출발점 이름" },
  "waypoints": [ { "lat": 37.2834, "lon": 127.0123, "name": "경유 1" } ],
  "destination": { "lat": 37.2801, "lon": 127.0101, "name": "도착점 이름" },
  "distance_m": 12400,
  "est_duration_sec": 2100,
  "corridor_m": 60
}
```

| 필드 | 뜻 | 쓰는 곳 |
|---|---|---|
| `shape` | 지도에 그릴 모양(도로를 따라간 선). 코스 미리보기, 결과 화면 비교선 | D 지도, E 폰 웹 |
| `start` / `waypoints` / `destination` | 내비(NaviHelper)에 넘길 지점. `waypoints`는 지나는 순서대로 | D 안내 |
| `corridor_m` | "코스 위에 있다"고 볼 폭(선에서 좌우 거리). 기록 시작 판정에 씀 | D 진입 판정 |
| `est_duration_sec` | 예상 소요 시간. 코스 목록 표시용 | D·E 화면 |

- `GET /courses?region=수원`은 위 객체의 목록을 돌려주되, `shape`는 미리보기용으로 점을 줄인 것이어도 됩니다(`💡 제안:` 50점 이하).
- `POST /course/route {region, course_id, origin}`은 코스 하나를 전부 돌려줍니다. `origin`(현재 위치)은 C가 "출발점까지 거리"를 붙여 줄 때만 씁니다.

**B가 알아 두면 좋은 내비 제약 (D가 에뮬레이터에서 시험한 결과, 2026-11-05)**
- NaviHelper 문서는 경유지 **최대 2개**라고 적고 있습니다. 에뮬레이터에서는 30개까지 들어가고 순서대로 핀이 찍혔지만, 실차와 App Market 심사에서 되는지는 모릅니다. > ⚠️ 확인 필요: Pleos 측 문의(partnership@pleos.ai)
- 💡 제안: 프리셋마다 경유지를 **2개 이하 버전과 많은 버전** 두 가지로 만들어 두면, 심사 결과에 따라 고를 수 있습니다. 많은 버전도 12개 안팎을 권합니다(점 14개보다 7개일 때 내비 경로가 모양과 더 많이 겹쳤습니다: 62% → 82%, 각 1회 측정).
- 내비는 경유지 사이 길을 **스스로 다시 계산**합니다. 그린 모양과 내비 경로가 어긋날 수 있으므로, 모양이 망가지기 쉬운 곳(꺾이는 곳, 갈림길)에 경유지를 둡니다.
- 내비가 실제로 그린 경로선 좌표를 앱이 받을 방법은 찾지 못했습니다. 그래서 지도에는 B의 `shape`를 그립니다.

## 2. 주행 신호 JSONL (D 실차 / C 가상주행 → D)

한 줄에 신호 하나인 JSON Lines입니다. **C의 가상주행 스크립트가 이 형식으로 내보내면 D 앱이 같은 코드로 재생**하므로, 실차 기록과 가상주행 결과가 같은 계산을 거칩니다(역할표 "모든 값은 실제 SDK 값과 가상주행 값을 같은 형식으로").

첫 줄은 머리말, 그 뒤가 신호입니다. `t`는 시작 기준 경과 시간(ms)입니다.

```jsonl
{"format": "drive-replay", "version": 2, "name": "수원 하트 (합성)", "synthetic": true, "course_id": "suwon-heart", "energy_type": "ev"}
{"t": 0, "k": "pos", "lat": 37.280100, "lon": 127.010100, "acc": 5.0}
{"t": 3000, "k": "gear", "v": "D"}
{"t": 5000, "k": "speed", "v": 32.5}
{"t": 5000, "k": "odo", "v": 12345.6}
{"t": 5000, "k": "battery", "v": 70000.0}
```

| `k` | `v` 뜻 · 단위 | 출처 (실차) |
|---|---|---|
| `pos` | `lat`, `lon`, `acc`(정확도 m, 생략 가능) | Android 위치 API (FusedLocation SDK는 준비 중) |
| `gear` | `P` `R` `N` `D` | AAOS Car API `GEAR_SELECTION` |
| `speed` | km/h | AAOS Car API `PERF_VEHICLE_SPEED` |
| `odo` | 누적 주행거리 km | Vehicle SDK Odometer `getCurrentDistanceDriven` ✅ 에뮬레이터 확인 |
| `battery` | 남은 배터리 에너지 **Wh** | AAOS `EV_BATTERY_LEVEL` ✅ 에뮬레이터 확인 |
| `fuel` | 남은 연료 mL | AAOS `FUEL_LEVEL` (에뮬레이터는 전기차라 값이 고정) |
| `fuel_eco` | **L/100km** (낮을수록 좋음, SDK 값 그대로) | Vehicle SDK Fuel `getCurrentFuelEconomy` ❌ 에뮬레이터 미지원 |
| `ev_eff` | km/kWh (높을수록 좋음) | Vehicle SDK EvBattery `getCurrentEvEfficiency` ❌ 에뮬레이터 미지원 |

- 버전 1(현재 D 시험 앱)은 `pos`·`gear`·`speed`만 있습니다. 버전 2에서 `odo`·`battery`·`fuel`·`fuel_eco`·`ev_eff`를 더합니다.
- 평균 효율은 차가 주는 값(`fuel_eco`, `ev_eff`)이 있으면 그것을, 없으면 **주행 전후 `odo` 차이 ÷ `battery`(또는 `fuel`) 차이**로 계산합니다(`spikes/vehicle-probe` 4장).
- `gear`가 `D`가 되면 주행 시작, `P`가 되면 주행 끝으로 봅니다. 가상주행도 처음에 `D`, 끝에 `P`를 꼭 넣어 주세요.
- 위치는 1초 간격 정도면 충분합니다. 연비·누적거리는 5~10초 간격이어도 됩니다.
- 합성 데이터는 머리말에 `"synthetic": true`를 넣어, 실제 주행으로 오해되지 않게 합니다.
- 에뮬레이터 실측(2026-11-08): `odo`는 0.1km 단위, 약 4~5초마다 이벤트가 옵니다. `fuel_eco`·`ev_eff`·이번 주행 거리·시간은 `ERROR_NOT_SUPPORTED_PROPERTY`입니다. 실차에서 되는지는 > ⚠️ 확인 필요.
- **C에게 💡 제안:** 가상주행을 JSONL로만 만들지 말고 **에뮬레이터 가상 콘솔에 직접 값을 넣는 방식**도 쓸 수 있습니다. 그러면 앱이 실제 SDK 경로로 값을 받습니다. 예시 도구 `spikes/vehicle-probe/tools/console_drive.py`(기어·속도·위치·누적거리·배터리)를 참고해 주세요.

## 3. 주행 결과 JSON (D → `POST /drive` → C → E)

```json
{
  "schema": "drive-result/1",
  "course_id": "suwon-heart",
  "started_at": "2026-11-08T14:03:00+09:00",
  "ended_at": "2026-11-08T14:41:30+09:00",
  "duration_sec": 2310,
  "moving_sec": 2040,
  "distance_km": 12.8,
  "energy_type": "fuel",
  "avg_fuel_economy": 14.2,
  "fuel_economy_source": "vehicle_sdk",
  "max_kph": 62,
  "on_course_ratio": 0.86,
  "polyline": { "type": "LineString", "coordinates": [[127.0101, 37.2801], [127.0110, 37.2812]] },
  "record_source": "car",
  "synthetic": false,
  "app_version": "0.1.0"
}
```

| 필드 | 뜻 |
|---|---|
| `course_id` | 고른 코스. 코스 없이 달린 기록이면 `null` |
| `started_at` / `ended_at` | 기록 시작(코스 첫 진입)과 끝(도착) 시각 |
| `duration_sec` / `moving_sec` | 전체 시간 / 실제로 움직인 시간(신호 대기 제외) |
| `avg_fuel_economy` | 평균 연비 **km/L** (높을수록 좋음). SDK의 L/100km를 `100 ÷ 값`으로 바꿉니다. 전기차면 `null` |
| `avg_ev_efficiency` | 전기차 평균 전비 km/kWh. 내연기관이면 생략 |
| `fuel_economy_source` | `vehicle_sdk`(차에서 읽음) / `estimated`(누적거리·남은 에너지 차이로 계산, **에뮬레이터는 이것**) / `sample`(시연용 예시 값) |
| `on_course_ratio` | 기록 중 코스 폭(`corridor_m`) 안에 있던 비율 0~1 |
| `polyline` | 실제 달린 궤적. 점 사이 10m 이상으로 줄여 보냅니다 |
| `record_source` | `car`(실차·에뮬레이터 신호) / `replay`(가상주행 재생) |

**응답 (C)**
```json
{ "drive_id": "d_8f3k2a", "result_url": "https://<서버>/r/d_8f3k2a" }
```
- 결과 QR에는 `result_url`을 그대로 담습니다. 💡 제안: QR 그림은 앱이 `result_url`로 직접 만듭니다(서버 왕복 없이 화면 표시, 담당 E).
- 전송이 실패하면 D가 기록을 차에 보관하고 다시 보냅니다. QR은 전송 성공 후에 띄웁니다.

## 4. 합의가 필요한 것

| # | 안건 | 관련 | D의 제안 |
|---|---|---|---|
| 1 | 연비 단위 | C·E | 화면·랭킹은 km/L(한국에서 익숙함), 신호 기록은 SDK 원값 L/100km |
| 2 | 전기차와 내연기관 연비 비교 | C·E | 같은 부문에 섞지 않음. 부문을 나누거나 "내 평소 대비 개선율"로 비교 |
| 3 | 연비를 못 읽는 경우 | A·C | 결과에 `fuel_economy_source`를 남기고, 시연 화면에는 `sample`일 때 "예시 값" 표시 |
| 4 | 위치 궤적 서버 저장 | A·C | 위치는 개인정보. 결과 화면에 필요한 만큼만(점 줄이기) 보내고, 보관 기간·동의 문구를 정함 |
| 5 | 시연 때 서버 주소 | C | 에뮬레이터에서 PC의 서버는 `http://10.0.2.2:<포트>`. 하지만 **폰이 QR로 열려면 폰에서 닿는 주소**(같은 Wi-Fi의 PC IP, 또는 외부 공개 주소)가 필요 |
| 6 | 경유지 개수 | B·A | 1장 "내비 제약" 참고. Pleos 문의 여부 |
| 7 | **에뮬레이터 차는 전기차** (연료 종류 코드 10, 배터리 77.4kWh) | A·E | 시연 결과 화면의 "연비"는 실제로는 전비(km/kWh)입니다. 화면·발표 용어를 "연비"로 둘지 "효율"·"전비"로 할지 정합니다 |

## 5. 변경 이력 참고: 에뮬레이터 실측 근거

`spikes/vehicle-probe/README.md`에 원본 기록과 재현 방법이 있습니다.

## 변경 이력

| 날짜 | 내용 | 작성 |
|---|---|---|
| 2026-11-08 | 초안 v0.1 | D |
