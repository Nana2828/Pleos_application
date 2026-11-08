# vehicle-probe: 차량 값 확인 시험 앱 (D)

> 💡 시험용 앱입니다. 팀 앱(`app/`)이 아니며 제출하지 않습니다. 확인한 결과와 `Efficiency.kt`(효율 계산)는 앱 코어로 옮깁니다.

`team-roles.md` D 역할의 "연비·거리·시간: Vehicle State(Vehicle SDK)"와 `scenario-flow.md` 7장 "확인 필요 2. 연비"를 **에뮬레이터에서 직접 확인**한 결과입니다.

## 1. 결론 (2026-11-08, Pleos Connect Emulator v2.1.0 · Vehicle SDK 2.3.2)

| 값 | 읽는 API | 결과 |
|---|---|---|
| 평균 연비 (내연기관) | Vehicle SDK `Fuel.getCurrentFuelEconomy` 등 연비 4종, `getFuelPercent` | ❌ `ERROR_NOT_SUPPORTED_PROPERTY` |
| 평균 전비 (전기차) | Vehicle SDK `EvBattery.getCurrentEvEfficiency` | ❌ `ERROR_NOT_SUPPORTED_PROPERTY` |
| 이번 주행 거리·시간 | Vehicle SDK `Odometer.getTripCurrentDrivingDistance` / `…DrivingTime` | ❌ `ERROR_NOT_SUPPORTED_PROPERTY` |
| **누적 주행거리** | Vehicle SDK `Odometer.getCurrentDistanceDriven`, `registerDistanceDriven` | ✅ km, 0.1km 단위, 이벤트 약 4~5초 간격 |
| 배터리 잔량 % | Vehicle SDK `EvBattery.getEvBatteryStateOfCharge` | ✅ (100% 고정. 콘솔 에너지 값과 따로 움직임) |
| **남은 배터리 에너지** | AAOS `EV_BATTERY_LEVEL` | ✅ Wh |
| 연료 종류 | AAOS `INFO_FUEL_TYPE` | ✅ `[10]` = **전기** (에뮬레이터 차는 전기차) |
| 남은 연료 | AAOS `FUEL_LEVEL` | ✅ 15000mL 고정 (전기차라 의미 없음) |
| AAOS 누적거리 | AAOS `PERF_ODOMETER` | ❌ `CAR_MILEAGE` 권한이 시스템 전용이라 일반 앱은 못 읽음 |

**그래서 평균 효율은 계산으로 구합니다.** 주행 시작·끝의 `누적 주행거리 차이 ÷ 쓴 배터리 에너지`(내연기관은 연료)를 씁니다. `Efficiency.kt`가 그 계산입니다.

- 콘솔로 3.0km를 목표 전비 6.0km/kWh로 달렸을 때, 앱 계산 결과는 **6.00km/kWh**였습니다(1회). 주행 중에는 누적거리가 0.1km 단위라 5.8~6.0으로 흔들립니다.
- 콘솔의 속도·기어만 바꾸면 누적거리와 배터리가 **저절로 바뀌지 않습니다.** 2.08km를 달려도 0km 그대로였습니다. 가상주행에서는 두 값을 직접 넣어야 합니다(3장).
- > ⚠️ 확인 필요: 실차(특히 내연기관)에서 `getCurrentFuelEconomy`가 되는지. 되면 그 값을 먼저 씁니다(`fuel_economy_source: vehicle_sdk`).

## 2. 권한

| 권한 | 등급 | 비고 |
|---|---|---|
| `pleos.car.permission.CAR_ENERGY` | normal | Fuel, EvBattery. 설치만 하면 허용 |
| `pleos.car.permission.CAR_DRIVE` | normal | Odometer |
| `android.car.permission.CAR_ENERGY` | dangerous | AAOS `EV_BATTERY_LEVEL`, `FUEL_LEVEL`. **앱 코어에서 실행 중 권한 요청이 필요** |
| `android.car.permission.CAR_INFO` | normal | `INFO_FUEL_TYPE` |
| `android.car.permission.CAR_MILEAGE` | 시스템 전용 | 일반 앱은 받을 수 없음 |

SDK 매니페스트가 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 권한과 `<queries>`를 스스로 합쳐 넣습니다. 앱이 따로 적을 것은 없습니다.

## 3. 가상 콘솔 쓰기 API (공개 문서에 없음)

콘솔 페이지가 읽는 `GET http://127.0.0.1:8088/vehicle/catalog`에 쓰기 경로가 나옵니다. 쓰기 전에 1초마다 `POST /browser/session?client=<id>`를 보내야 합니다(3초 끊기면 거부).

| 값 | 요청 |
|---|---|
| 기어 | `PUT /vehicle/gear?gear=Drive` (`Park` `Reverse` `Neutral`) |
| 속도 | `PUT /vehicle/controls?speed=90&accelerator=20&brake=0` |
| 누적 주행거리 | `PUT /vehicle/odometer?traveled_distance_km=1001.3` (**줄이면 400** `traveled_distance_must_not_decrease`) |
| 배터리 에너지 | `PUT /vehicle/ev-battery?field=battery_level&value=69.5` (kWh) |
| 현재 상태 | `GET /vehicle/state` |
| 위치 | 콘솔이 아니라 `adb emu geo fix <경도> <위도>` |

`tools/console_drive.py`가 이를 묶어 "기어 D → 직진 → 누적거리·배터리 감소 → 기어 P"를 합니다. C의 가상주행이 같은 방식을 쓰면 앱은 실제 SDK 경로로 값을 받습니다.

## 4. 실행 방법 (Windows, Git Bash)

```sh
export MSYS_NO_PATHCONV=1 JAVA_HOME="<JDK 17 경로>" ANDROID_HOME="<Android SDK 경로>"
# 저장소 경로에 한글이 있으면 빌드 산출물을 영문 경로로 보낸다(선택)
export PROBE_BUILD_DIR="C:/Android/build/vehicle-probe"
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r "$PROBE_BUILD_DIR/app/outputs/apk/debug/app-debug.apk"
adb shell pm grant --user 10 ai.pleosapp.probe android.car.permission.CAR_ENERGY
adb shell am start --user 10 -n ai.pleosapp.probe/.ProbeActivity
adb logcat -s VehicleProbe:I          # 10초마다 모든 값을 남긴다

adb forward tcp:8088 tcp:8088
python -I tools/console_drive.py "$(command -v adb)" 120 90 6.0 70   # 120초, 90km/h, 목표 6.0km/kWh, 배터리 70kWh에서 시작
```

- 에뮬레이터 앱 사용자는 `--user 10`입니다.
- 기존 AVD가 이상하면(터치 지연, ANR, 콘솔 `emulator_inactive`) 같은 이미지로 AVD를 새로 만들면 정상이었습니다(D의 PC, 2026-11-06).

## 5. 파일

| 파일 | 내용 | 앱 코어로 옮김 |
|---|---|---|
| `Efficiency.kt` | 효율 계산 (안드로이드 의존 없음, 단위 테스트 7개) | ✅ 그대로 |
| `PleosReader.kt` | Vehicle SDK 읽기·지원 확인·이벤트 | 필요한 부분만 래퍼로 |
| `AaosReader.kt` | AAOS Car API 읽기 | 필요한 부분만 래퍼로 |
| `ProbeActivity.kt` | 시험 화면(로그) | ❌ |
| `tools/console_drive.py` | 콘솔 가상주행 | C와 상의 |
