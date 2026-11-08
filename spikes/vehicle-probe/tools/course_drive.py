"""코스 모양을 따라 달리는 가상주행. Pleos 에뮬레이터 가상 콘솔과 geo fix를 쓴다.

console_drive.py는 동쪽 직선으로만 달린다. 이 스크립트는 규격 ① 코스 JSON(course/1)의
shape(LineString, [경도, 위도] 순서)을 따라 위치를 옮긴다. 앱은 실제 SDK 경로로 값을 받는다.

  기어 P → (선택) 코스 밖 접근 구간 → 코스 따라 주행 → 정차 → 기어 P
  주행 중 매초: 속도, 위치(geo fix), 누적 주행거리, 배터리 잔량을 함께 쓴다.

usage:
  python -I course_drive.py <adb 경로> <코스 JSON> [옵션]

옵션:
  --kph 60            주행 속도 (km/h)
  --corner-kph 30     꺾이는 곳(방향이 45도 넘게 바뀌는 곳) 앞뒤 속도. 0이면 감속 안 함
  --approach-m 0      코스 시작점 전에 코스 밖에서 이만큼 달려 들어온다 (코스 진입 판정 시험용)
  --km-per-kwh 6.0    배터리를 줄이는 비율 (전비)
  --battery-kwh 70    시작 배터리 (kWh)
  --no-vehicle        누적 주행거리·배터리는 쓰지 않고 기어·속도·위치만 넣는다
  --log <경로>        보낸 신호를 규격 ② 주행 신호 JSONL(drive-replay v2)로 남긴다. 앱 계산 코드 시험용

까다로운 상황 시험용 옵션 (앱 계산 코드 디버그용):
  --reverse           코스를 거꾸로 달린다
  --shortcut a:b      코스 a m 지점에서 b m 지점으로 곧게 건너뛴다 (지름길)
  --tunnel a:l        달린 거리 a m부터 l m 동안 위치를 보내지 않는다 (터널. 속도는 계속 보냄)
  --stops a:s         달린 거리 a m에서 s초 멈춘다 (신호 대기). 쉼표로 여러 개
  --gps-noise 5       위치에 표준편차 5m 흔들림을 더한다 (멈춰 있을 때도)
  --dry-run           에뮬레이터 없이 --log 파일만 만든다 (가짜 시간, 바로 끝남)

먼저 `adb forward tcp:8088 tcp:8088`을 실행한다.
콘솔 쓰기 경로는 콘솔 페이지의 /vehicle/catalog에서 읽은 것이다(공개 문서에는 없음, Emulator v2.1.0, 2026-11-08).
콘솔은 브라우저 세션 신호가 3초 끊기면 쓰기를 거부하므로 1초마다 보낸다.
누적 주행거리는 줄어드는 값을 거부하므로 콘솔의 현재 값에서 이어 간다.
"""
import argparse
import json
import math
import random
import subprocess
import sys
import threading
import time
import urllib.request

BASE = "http://127.0.0.1:8088"
EARTH_R = 6_371_000.0
TICK_S = 1.0
CORNER_DEG = 45.0
CORNER_ZONE_M = 40.0


def call(method, path):
    req = urllib.request.Request(BASE + path, method=method)
    with urllib.request.urlopen(req, timeout=5) as res:
        return json.loads(res.read().decode("utf-8"))


def heartbeat(stop):
    while not stop.is_set():
        try:
            call("POST", "/browser/session?client=course-drive")
        except OSError as exc:
            print("세션 신호 실패", exc, flush=True)
        time.sleep(1)


def geo_fix(adb, lat, lon):
    subprocess.run([adb, "emu", "geo", "fix", f"{lon:.6f}", f"{lat:.6f}"],
                   stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, timeout=10, check=False)


def dist_m(a, b):
    """두 (위도, 경도) 점 사이 거리 (haversine)."""
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * EARTH_R * math.asin(math.sqrt(h))


def bearing_deg(a, b):
    la1, la2 = math.radians(a[0]), math.radians(b[0])
    dlo = math.radians(b[1] - a[1])
    y = math.sin(dlo) * math.cos(la2)
    x = math.cos(la1) * math.sin(la2) - math.sin(la1) * math.cos(la2) * math.cos(dlo)
    return math.degrees(math.atan2(y, x)) % 360


def offset(p, bearing, meters):
    """p에서 bearing 방향으로 meters 떨어진 점 (짧은 거리용 평면 근사)."""
    dlat = meters * math.cos(math.radians(bearing)) / 111_194.9
    dlon = meters * math.sin(math.radians(bearing)) / (111_194.9 * math.cos(math.radians(p[0])))
    return (p[0] + dlat, p[1] + dlon)


class Path:
    """점 목록을 따라 누적 거리로 위치를 찾는다."""

    def __init__(self, points):
        # 같은 점이 연달아 있으면 길이 0 구간이 생기므로 뺀다.
        self.points = [points[0]] + [q for p, q in zip(points, points[1:]) if dist_m(p, q) > 0.01]
        self.cum = [0.0]
        for p, q in zip(self.points, self.points[1:]):
            self.cum.append(self.cum[-1] + dist_m(p, q))
        self.length = self.cum[-1]
        self.corners = self._corners()

    def _corners(self):
        out = []
        for i in range(1, len(self.points) - 1):
            turn = abs((bearing_deg(self.points[i], self.points[i + 1])
                        - bearing_deg(self.points[i - 1], self.points[i]) + 180) % 360 - 180)
            if turn > CORNER_DEG:
                out.append(self.cum[i])
        return out

    def at(self, s):
        s = max(0.0, min(s, self.length))
        i = max(0, min(len(self.cum) - 2, next((k - 1 for k, c in enumerate(self.cum) if c >= s), len(self.cum) - 2)))
        seg = self.cum[i + 1] - self.cum[i]
        f = 0.0 if seg == 0 else (s - self.cum[i]) / seg
        a, b = self.points[i], self.points[i + 1]
        return (a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f)

    def near_corner(self, s):
        return any(abs(s - c) <= CORNER_ZONE_M for c in self.corners)


class SignalLog:
    """보낸 값을 규격 ② 주행 신호 JSONL(drive-replay v2)로 남긴다. t는 첫 신호 기준 ms."""

    def __init__(self, path, course, clock):
        self.f = open(path, "w", encoding="utf-8") if path else None
        self.clock = clock
        self.t0 = clock.now()
        self.write({"format": "drive-replay", "version": 2,
                    "name": f"{course.get('name', course.get('course_id'))} (에뮬레이터 가상주행)",
                    "synthetic": True, "course_id": course.get("course_id"), "energy_type": "ev"})

    def write(self, obj):
        if self.f:
            self.f.write(json.dumps(obj, ensure_ascii=False) + "\n")

    def sig(self, k, **fields):
        self.write({"t": round((self.clock.now() - self.t0) * 1000), "k": k, **fields})

    def close(self):
        if self.f:
            self.f.close()


def load_course(path):
    with open(path, encoding="utf-8") as f:
        course = json.load(f)
    if course.get("schema") != "course/1":
        sys.exit(f"course/1 규격이 아닙니다: schema={course.get('schema')!r}")
    # GeoJSON은 [경도, 위도] 순서다.
    pts = [(lat, lon) for lon, lat in course["shape"]["coordinates"]]
    if len(pts) < 2:
        sys.exit("shape 점이 2개보다 적습니다")
    return course, pts


def pairs(text):
    """'a:b,c:d' → [(a, b), (c, d)]"""
    if not text:
        return []
    return [tuple(float(x) for x in item.split(":")) for item in text.split(",")]


def build_path(pts, args):
    """접근·역방향·지름길 옵션을 반영한 달릴 선. (경로, 접근 길이 m)"""
    if args.reverse:
        pts = list(reversed(pts))
    if args.shortcut:
        # 코스 선의 a m 지점에서 b m 지점으로 곧게 건너뛴다.
        a, b = pairs(args.shortcut)[0]
        base = Path(pts)
        before = [p for p, c in zip(base.points, base.cum) if c < a] + [base.at(a)]
        after = [base.at(b)] + [p for p, c in zip(base.points, base.cum) if c > b]
        pts = before + after
    approach = []
    if args.approach_m > 0:
        # 첫 구간의 반대 방향에서 시작점으로 곧게 들어온다.
        back = (bearing_deg(pts[0], pts[1]) + 180) % 360
        approach = [offset(pts[0], back, args.approach_m)]
    return Path(approach + pts), args.approach_m


class Clock:
    """실제 시간. --dry-run이면 가짜 시간으로 바로 끝낸다."""

    def __init__(self, dry):
        self.dry = dry
        self.t = 0.0

    def now(self):
        return self.t if self.dry else time.time()

    def sleep(self, s):
        if self.dry:
            self.t += s
        else:
            time.sleep(s)


def main():
    ap = argparse.ArgumentParser(description="코스 모양을 따라 달리는 가상주행")
    ap.add_argument("adb")
    ap.add_argument("course")
    ap.add_argument("--kph", type=float, default=60.0)
    ap.add_argument("--corner-kph", type=float, default=30.0)
    ap.add_argument("--approach-m", type=float, default=0.0)
    ap.add_argument("--km-per-kwh", type=float, default=6.0)
    ap.add_argument("--battery-kwh", type=float, default=70.0)
    ap.add_argument("--no-vehicle", action="store_true")
    ap.add_argument("--log")
    ap.add_argument("--reverse", action="store_true", help="코스를 거꾸로 달린다")
    ap.add_argument("--shortcut", help="a:b — 코스 a m 지점에서 b m 지점으로 곧게 건너뛴다")
    ap.add_argument("--tunnel", help="a:l[,a:l] — 달린 거리 a m부터 l m 동안 위치를 보내지 않는다(속도는 보냄)")
    ap.add_argument("--stops", help="a:s[,a:s] — 달린 거리 a m에서 s초 멈춘다(신호 대기)")
    ap.add_argument("--gps-noise", type=float, default=0.0, help="위치에 더할 흔들림 표준편차 m (멈춰 있을 때도)")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--dry-run", action="store_true", help="에뮬레이터 없이 --log 파일만 만든다(가짜 시간)")
    args = ap.parse_args()
    if args.dry_run and not args.log:
        sys.exit("--dry-run에는 --log가 필요합니다")

    clock = Clock(args.dry_run)
    rng = random.Random(args.seed)
    tunnels = pairs(args.tunnel)
    stops = sorted(pairs(args.stops))

    def put(path_):
        return {"actual": "dry-run"} if args.dry_run else call("PUT", path_)

    def fix(lat, lon):
        if not args.dry_run:
            geo_fix(args.adb, lat, lon)

    def noisy(p):
        if args.gps_noise <= 0:
            return p
        return offset(offset(p, 0, rng.gauss(0, args.gps_noise)), 90, rng.gauss(0, args.gps_noise))

    course, pts = load_course(args.course)
    path, approach_m = build_path(pts, args)
    course_len = path.length - approach_m
    print(f"코스: {course.get('name', course.get('course_id'))} · 달릴 선 {course_len / 1000:.2f}km"
          f" + 접근 {approach_m / 1000:.2f}km · 꺾이는 곳 {len(path.corners)}개", flush=True)

    stop = threading.Event()
    if not args.dry_run:
        threading.Thread(target=heartbeat, args=(stop,), daemon=True).start()
        time.sleep(2)
    log = SignalLog(args.log, course, clock)
    put("/vehicle/gear?gear=Park")
    log.sig("gear", v="P")
    odo0 = None
    if not args.no_vehicle:
        # 0.1km 단위로 올림해 시작한다. 그대로 쓰면 반올림으로 값이 줄어 거부될 수 있다.
        odo0 = 1000.0 if args.dry_run else \
            math.ceil(call("GET", "/vehicle/state")["odometer"]["traveled_distance_km"] * 10) / 10
        put(f"/vehicle/odometer?traveled_distance_km={odo0:.1f}")
        put(f"/vehicle/ev-battery?field=battery_level&value={args.battery_kwh:.3f}")
        print(f"시작 값: 누적 {odo0:.1f}km, 배터리 {args.battery_kwh:.3f}kWh", flush=True)
        log.sig("odo", v=odo0)
        log.sig("battery", v=round(args.battery_kwh * 1000, 1))
    p0 = noisy(path.at(0))
    fix(*p0)
    log.sig("pos", lat=round(p0[0], 6), lon=round(p0[1], 6), acc=5.0)
    clock.sleep(12 if odo0 is not None else 4)  # 앱이 시작 값과 시작 위치를 읽을 시간
    print("D", put("/vehicle/gear?gear=Drive").get("actual"), flush=True)
    log.sig("gear", v="D")

    s = 0.0
    begin = last = clock.now()
    kph = 0.0
    next_report = 30.0
    stop_until = None
    sent_pos = 0
    while s < path.length:
        now = clock.now()
        dt = now - last
        last = now
        # 지난 1초 동안 그 속도로 움직였다고 보고 거리를 더한다. 실제 경과 시간을 쓰므로 호출 지연이 있어도 거리는 맞다.
        s = min(path.length, s + kph / 3.6 * dt)
        if stop_until is None and stops and s >= stops[0][0]:
            stop_until = now + stops.pop(0)[1]
        if stop_until is not None and now < stop_until:
            kph = 0.0
        else:
            stop_until = None
            kph = args.corner_kph if args.corner_kph > 0 and path.near_corner(s) else args.kph
        put(f"/vehicle/controls?speed={kph:.0f}&accelerator={0 if kph == 0 else 20}&brake={30 if kph == 0 else 0}")
        log.sig("speed", v=round(kph, 1))
        if not any(a <= s < a + length for a, length in tunnels):
            lat, lon = noisy(path.at(s))
            fix(lat, lon)
            log.sig("pos", lat=round(lat, 6), lon=round(lon, 6), acc=5.0)
            sent_pos += 1
        if odo0 is not None:
            km = s / 1000
            put(f"/vehicle/odometer?traveled_distance_km={odo0 + km:.1f}")
            put(f"/vehicle/ev-battery?field=battery_level&value={args.battery_kwh - km / args.km_per_kwh:.3f}")
            log.sig("odo", v=round(odo0 + km, 1))
            log.sig("battery", v=round((args.battery_kwh - km / args.km_per_kwh) * 1000, 1))
        if now - begin >= next_report and not args.dry_run:
            print(f"  {now - begin:4.0f}초 · {s / 1000:.2f}/{path.length / 1000:.2f}km", flush=True)
            next_report += 30
        clock.sleep(max(0.0, TICK_S - (clock.now() - now)))
    moving_s = clock.now() - begin
    put("/vehicle/controls?speed=0&accelerator=0&brake=0")
    log.sig("speed", v=0.0)
    clock.sleep(2)
    print("P", put("/vehicle/gear?gear=Park").get("actual"), flush=True)
    log.sig("gear", v="P")
    log.close()
    print(f"끝: 위치를 옮긴 거리 {s / 1000:.3f}km (그중 코스 {course_len / 1000:.3f}km), "
          f"걸린 시간 {moving_s:.0f}초, 평균 {s / 1000 / (moving_s / 3600):.0f}km/h, 위치 {sent_pos}번 보냄", flush=True)
    if odo0 is not None:
        used = s / 1000 / args.km_per_kwh
        print(f"     누적 {round(odo0 + s / 1000, 1):.1f}km, 배터리 {args.battery_kwh - used:.3f}kWh"
              f" (사용 {used:.3f}kWh)", flush=True)
        clock.sleep(12)  # 앱이 끝 값을 읽을 시간
    stop.set()


if __name__ == "__main__":
    main()
