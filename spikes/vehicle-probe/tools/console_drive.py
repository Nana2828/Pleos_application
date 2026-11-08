"""Pleos 에뮬레이터 가상 콘솔로 '차량 신호까지 움직이는' 가상주행을 한다.

합성 재생(JSONL)과 달리, 앱이 실제 SDK 경로(Vehicle SDK Odometer, AAOS 기어·속도·배터리, 위치)로 값을 받는다.
기어 D → 일정 속도로 직진(geo fix) → 매초 누적 주행거리 증가·배터리 감소 → 기어 P.

usage:
  python -I console_drive.py <adb 경로> <달리는 초> <km/h> [km/kWh=6.0] [시작 배터리 kWh=70]

누적 주행거리는 콘솔의 현재 값에서 이어 간다. 콘솔이 줄어드는 값을 거부한다(traveled_distance_must_not_decrease).

먼저 `adb forward tcp:8088 tcp:8088`을 실행한다.
콘솔 쓰기 경로는 콘솔 페이지의 /vehicle/catalog에서 읽은 것이다(공개 문서에는 없음, Emulator v2.1.0, 2026-11-08).
콘솔은 브라우저 세션 신호가 3초 끊기면 쓰기를 거부하므로 1초마다 보낸다.
"""
import json
import math
import subprocess
import sys
import threading
import time
import urllib.request

BASE = "http://127.0.0.1:8088"
START = (37.4018, 127.1086)  # 판교 부근. 동쪽으로 직진한다.
M_PER_DEG_LAT = 111_194.9


def call(method, path):
    req = urllib.request.Request(BASE + path, method=method)
    with urllib.request.urlopen(req, timeout=5) as res:
        return json.loads(res.read().decode("utf-8"))


def heartbeat(stop):
    while not stop.is_set():
        try:
            call("POST", "/browser/session?client=console-drive")
        except OSError as exc:
            print("세션 신호 실패", exc, flush=True)
        time.sleep(1)


def geo_fix(adb, lat, lon):
    subprocess.run([adb, "emu", "geo", "fix", f"{lon:.6f}", f"{lat:.6f}"],
                   stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, timeout=10, check=False)


def set_odometer(km):
    call("PUT", f"/vehicle/odometer?traveled_distance_km={km:.1f}")


def set_battery(kwh):
    call("PUT", f"/vehicle/ev-battery?field=battery_level&value={kwh:.3f}")


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(2)
    adb, seconds, kph = sys.argv[1], int(sys.argv[2]), float(sys.argv[3])
    km_per_kwh = float(sys.argv[4]) if len(sys.argv) > 4 else 6.0
    kwh0 = float(sys.argv[5]) if len(sys.argv) > 5 else 70.0

    stop = threading.Event()
    threading.Thread(target=heartbeat, args=(stop,), daemon=True).start()
    time.sleep(2)
    call("PUT", "/vehicle/gear?gear=Park")
    # 0.1km 단위로 올림해 시작한다. 그대로 쓰면 반올림으로 값이 줄어 거부될 수 있다.
    odo0 = math.ceil(call("GET", "/vehicle/state")["odometer"]["traveled_distance_km"] * 10) / 10
    set_odometer(odo0)
    set_battery(kwh0)
    geo_fix(adb, *START)
    print(f"시작: 누적 {odo0:.1f}km, 배터리 {kwh0:.3f}kWh, 목표 전비 {km_per_kwh}km/kWh", flush=True)
    time.sleep(12)  # 앱이 시작 값을 읽을 시간
    call("PUT", "/vehicle/gear?gear=Drive")

    begin = time.time()
    m_per_deg_lon = M_PER_DEG_LAT * math.cos(math.radians(START[0]))
    km = 0.0
    while time.time() - begin < seconds:
        km = kph * (time.time() - begin) / 3600
        call("PUT", f"/vehicle/controls?speed={kph:.0f}&accelerator=20&brake=0")
        geo_fix(adb, START[0], START[1] + km * 1000 / m_per_deg_lon)
        set_odometer(odo0 + km)
        set_battery(kwh0 - km / km_per_kwh)
        time.sleep(1)
    call("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=0")
    time.sleep(2)
    call("PUT", "/vehicle/gear?gear=Park")
    # 누적거리는 0.1km 단위로 반올림되어 들어가므로 기대 전비도 그 값으로 계산한다.
    odo_end = round(odo0 + km, 1)
    print(f"끝: 누적 {odo_end:.1f}km, 배터리 {kwh0 - km / km_per_kwh:.3f}kWh, "
          f"달린 거리 {km:.2f}km, 기대 전비 약 {(odo_end - odo0) / (km / km_per_kwh):.2f}km/kWh", flush=True)
    time.sleep(12)  # 앱이 끝 값을 읽을 시간
    stop.set()


main()
