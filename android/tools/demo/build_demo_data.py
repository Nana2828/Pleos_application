"""시연용 데이터 만들기: OSRM 안내 데이터 + 가짜 주행 신호.

1차 제출 목업 시연용이다. 앱은 이 파일들을 assets에서 읽어 재생한다(인터넷 필요 없음).

만드는 파일 (기본 출력: android/app/src/main/assets/demo/). 코스마다 세 개씩 생긴다:
  <코스 id>.json          코스 원본 복사본 (B가 만든 형식 그대로. 좌표는 {lat, lng})
  <코스 id>.route.json    접근 구간(가짜 현재 위치 → 코스 시작점)과 코스 구간의
                          촘촘한 경로 좌표와 회전 안내. 좌표 배열은 [경도, 위도] (GeoJSON)
  <코스 id>.drive.jsonl   주행 신호 drive-replay v2. 접근 + 코스 한 바퀴를 실제 시간(1초 간격)으로 담는다.
                          앱이 배속으로 재생한다. "synthetic": true

가짜 현재 위치는 모든 코스가 같다: LG가산디지털센터 (서울 금천구 가산디지털1로 189). PLEOS-62

usage:
  python -I build_demo_data.py <코스 JSON> [<코스 JSON> ...] [--out <폴더>] [--origin 위도,경도] [--seed 1]

OSRM 공개 서버(router.project-osrm.org)는 시연 준비용으로 한 번만 부른다. 앱은 실행 중에 부르지 않는다.
경로 데이터 출처: © OpenStreetMap contributors (ODbL).
"""
import argparse
import json
import math
import random
import shutil
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

OSRM = "https://router.project-osrm.org"
EARTH_R = 6_371_000.0

# 가짜 현재 위치: LG가산디지털센터 (서울 금천구 가산디지털1로 189). OSM Nominatim 좌표. PLEOS-62
DEFAULT_ORIGIN = (37.482336, 126.879651)

# 가짜 차량 값 (시연 영상은 전기차)
START_ODO_KM = 12_480.0
START_BATTERY_WH = 62_000.0
KM_PER_KWH = 6.0

GPS_NOISE_M = 2.5
ODO_EVERY_S = 5
BATTERY_EVERY_S = 5
SLOW_TYPES = {"turn", "end of road", "roundabout", "rotary", "roundabout turn", "fork", "on ramp", "off ramp"}
SLOW_KPH = 25.0
SLOW_ZONE_M = 35.0
ACCEL_KPH_PER_S = 7.0


# ---------- 지오 계산 ----------

def dist_m(a, b):
    """a, b = (경도, 위도)"""
    lon1, lat1 = map(math.radians, a)
    lon2, lat2 = map(math.radians, b)
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * EARTH_R * math.asin(math.sqrt(h))


def cumulative(line):
    out = [0.0]
    for i in range(1, len(line)):
        out.append(out[-1] + dist_m(line[i - 1], line[i]))
    return out


def point_at(line, cum, s):
    if s <= 0:
        return line[0]
    if s >= cum[-1]:
        return line[-1]
    lo, hi = 0, len(cum) - 1
    while hi - lo > 1:
        mid = (lo + hi) // 2
        if cum[mid] <= s:
            lo = mid
        else:
            hi = mid
    seg = cum[hi] - cum[lo]
    f = 0.0 if seg == 0 else (s - cum[lo]) / seg
    a, b = line[lo], line[hi]
    return (a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f)


class GpsError:
    """실제 GPS처럼 천천히 변하는 오차 (1차 자기회귀).

    매초 따로 흔들면 점이 지그재그가 되어 기록 거리가 실제보다 몇 % 늘어난다.
    """
    KEEP = 0.97

    def __init__(self, sigma_m, rnd):
        self.sigma = sigma_m
        self.rnd = rnd
        self.n = 0.0
        self.e = 0.0

    def apply(self, p):
        step = self.sigma * math.sqrt(1 - self.KEEP ** 2)
        self.n = self.KEEP * self.n + self.rnd.gauss(0, step)
        self.e = self.KEEP * self.e + self.rnd.gauss(0, step)
        lat = p[1] + math.degrees(self.n / EARTH_R)
        lon = p[0] + math.degrees(self.e / (EARTH_R * math.cos(math.radians(p[1]))))
        return (lon, lat)


# ---------- OSRM ----------

def osrm_route(points):
    """points = [(경도, 위도), ...] 순서대로 지나는 경로"""
    coords = ";".join(f"{lon:.6f},{lat:.6f}" for lon, lat in points)
    url = f"{OSRM}/route/v1/driving/{coords}?steps=true&overview=full&geometries=geojson&continue_straight=true"
    req = urllib.request.Request(url, headers={"User-Agent": "pleos-demo-data-builder"})
    with urllib.request.urlopen(req, timeout=30) as res:
        data = json.loads(res.read().decode("utf-8"))
    if data.get("code") != "Ok":
        raise SystemExit(f"OSRM 오류: {data.get('code')} {data.get('message')}")
    return data["routes"][0]


def build_section(route):
    """OSRM 경로 하나를 앱이 쓰기 좋은 모양으로 바꾼다.

    steps[].atM = 경로 좌표(geometry)를 따라 잰, 출발점에서 그 안내 지점까지의 거리.
    중간 경유지의 도착·출발 안내는 운전자에게 의미가 없어 뺀다.
    """
    line = [tuple(c) for c in route["geometry"]["coordinates"]]
    cum = cumulative(line)
    legs = route["legs"]
    steps = []
    search_from = 0
    for li, leg in enumerate(legs):
        for si, st in enumerate(leg["steps"]):
            m = st["maneuver"]
            kind = m["type"]
            first_leg, last_leg = li == 0, li == len(legs) - 1
            if kind == "depart" and not first_leg:
                continue
            if kind == "arrive" and not last_leg:
                continue
            loc = tuple(m["location"])
            # 안내 지점을 경로 좌표 위에서 찾는다 (앞으로만 찾아 8자 교차점에서 헷갈리지 않게)
            best_i, best_d = search_from, float("inf")
            for i in range(search_from, min(len(line), search_from + 400)):
                d = dist_m(line[i], loc)
                if d < best_d:
                    best_i, best_d = i, d
                if best_d < 1.0:
                    break
            search_from = best_i
            steps.append({
                "atM": round(cum[best_i], 1),
                "type": kind,
                "modifier": m.get("modifier", ""),
                "name": st.get("name", ""),
                "ref": st.get("ref", ""),
                "exit": m.get("exit"),
                "lengthM": round(st["distance"], 1),
                "durationSec": round(st["duration"], 1),
                "location": [round(loc[0], 6), round(loc[1], 6)],
            })
    return {
        "distanceM": round(cum[-1], 1),
        "osrmDistanceM": round(route["distance"], 1),
        "durationSec": round(route["duration"]),
        "geometry": [[round(lon, 6), round(lat, 6)] for lon, lat in line],
        "steps": steps,
    }


# ---------- 가짜 주행 ----------

def speed_plan(section):
    """경로 위 거리 s에서의 목표 속도(km/h)를 돌려주는 함수"""
    steps = section["steps"]
    spans = []
    for st in steps:
        if st["lengthM"] > 0 and st["durationSec"] > 0:
            kph = st["lengthM"] / st["durationSec"] * 3.6
            spans.append((st["atM"], st["atM"] + st["lengthM"], max(20.0, min(kph * 0.95, 80.0))))
    slow_at = [st["atM"] for st in steps if st["type"] in SLOW_TYPES]

    def target(s):
        kph = 40.0
        for a, b, v in spans:
            if a <= s < b:
                kph = v
                break
        for p in slow_at:
            if abs(s - p) < SLOW_ZONE_M:
                return min(kph, SLOW_KPH)
        return kph
    return target


def pick_stops(section, rnd, count):
    """신호 대기: 회전 안내 지점 몇 곳 앞에서 20~45초 선다"""
    cand = [st["atM"] for st in section["steps"] if st["type"] in ("turn", "end of road") and st["atM"] > 300]
    cand = cand[: -1] if len(cand) > 1 else cand
    chosen = sorted(rnd.sample(cand, min(count, len(cand))))
    return [(max(0.0, at - 8.0), rnd.randint(20, 45)) for at in chosen]


class Writer:
    def __init__(self, path, header):
        self.f = open(path, "w", encoding="utf-8", newline="\n")
        self.write(header)

    def write(self, obj):
        self.f.write(json.dumps(obj, ensure_ascii=False) + "\n")

    def close(self):
        self.f.close()


def drive(section_list, out_path, header, rnd):
    """접근 + 코스를 이어 한 번의 주행으로 만든다. t는 실제 시간(ms)."""
    line = []
    for sec in section_list:
        g = [tuple(c) for c in sec["geometry"]]
        if line and dist_m(line[-1], g[0]) < 1.0:
            g = g[1:]
        line.extend(g)
    cum = cumulative(line)
    total = cum[-1]

    # 구간별 속도 계획과 정차를 하나의 거리축으로 합친다
    offsets, plans, stops = [], [], []
    base = 0.0
    for i, sec in enumerate(section_list):
        offsets.append(base)
        plans.append(speed_plan(sec))
        stops += [(base + a, d) for a, d in pick_stops(sec, rnd, 2 if i == 0 else 9)]
        base += sec["distanceM"]

    def target(s):
        for i in range(len(offsets) - 1, -1, -1):
            if s >= offsets[i]:
                return plans[i](s - offsets[i])
        return plans[0](s)

    w = Writer(out_path, header)
    gps = GpsError(GPS_NOISE_M, rnd)
    t = 0
    odo_km = START_ODO_KM
    batt = START_BATTERY_WH
    p0 = line[0]
    w.write({"t": t, "k": "pos", "lat": round(p0[1], 6), "lon": round(p0[0], 6), "acc": 5.0})
    w.write({"t": t, "k": "odo", "v": round(odo_km, 1)})
    w.write({"t": t, "k": "battery", "v": round(batt, 1)})
    w.write({"t": t, "k": "speed", "v": 0.0})
    t += 3000
    w.write({"t": t, "k": "gear", "v": "D"})

    s = 0.0
    kph = 0.0
    stop_i = 0
    stop_left = 0
    sec_count = 0
    while s < total - 0.5:
        t += 1000
        sec_count += 1
        if stop_left > 0:
            stop_left -= 1
            kph = 0.0
        else:
            want = target(s)
            # 정차 지점 앞에서는 멈추기
            if stop_i < len(stops) and stops[stop_i][0] - s < 25:
                want = 0.0 if stops[stop_i][0] - s < 3 else min(want, 15.0)
            # 도착 직전 감속
            if total - s < 60:
                want = min(want, max(8.0, (total - s) / 60 * 30))
            if want > kph:
                kph = min(want, kph + ACCEL_KPH_PER_S)
            else:
                kph = max(want, kph - ACCEL_KPH_PER_S * 1.5)
            if stop_i < len(stops) and kph == 0.0 and stops[stop_i][0] - s < 3:
                stop_left = stops[stop_i][1]
                stop_i += 1
            step = kph / 3.6
            s = min(total, s + step)
            odo_km += step / 1000
            batt -= step / 1000 / KM_PER_KWH * 1000
        p = gps.apply(point_at(line, cum, s))
        w.write({"t": t, "k": "speed", "v": round(kph, 1)})
        w.write({"t": t, "k": "pos", "lat": round(p[1], 6), "lon": round(p[0], 6), "acc": 5.0})
        if sec_count % ODO_EVERY_S == 0:
            w.write({"t": t, "k": "odo", "v": round(odo_km, 1)})
        if sec_count % BATTERY_EVERY_S == 0:
            w.write({"t": t, "k": "battery", "v": round(batt, 1)})

    t += 1000
    w.write({"t": t, "k": "speed", "v": 0.0})
    t += 2000
    w.write({"t": t, "k": "gear", "v": "P"})
    w.write({"t": t, "k": "odo", "v": round(odo_km, 1)})
    w.write({"t": t, "k": "battery", "v": round(batt, 1)})
    w.close()
    return {"totalM": total, "durationSec": t // 1000, "stops": len(stops),
            "usedKwh": (START_BATTERY_WH - batt) / 1000, "odoKm": odo_km - START_ODO_KM}


# ---------- main ----------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("courses", nargs="+", help="코스 JSON 파일 (여러 개 가능)")
    ap.add_argument("--out", default=str(Path(__file__).resolve().parents[2] / "app/src/main/assets/demo"))
    ap.add_argument("--origin", help="가짜 현재 위치 '위도,경도'")
    ap.add_argument("--seed", type=int, default=1)
    args = ap.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    origin = tuple(map(float, args.origin.split(","))) if args.origin else DEFAULT_ORIGIN
    for course_file in args.courses:
        build_course(Path(course_file), out, origin, random.Random(args.seed))


def build_course(course_path, out, origin, rnd):
    course = json.loads(course_path.read_text(encoding="utf-8"))
    cid = course["id"]
    start = (course["start"]["lng"], course["start"]["lat"])

    # 코스: 시작점 → 각 구간의 경유지 → 구간 도착점 (B가 코스를 만들 때 쓴 순서 그대로)
    course_pts = [start]
    for leg in course["legs"]:
        for p in leg["waypoints"] + [leg["destination"]]:
            course_pts.append((p["lng"], p["lat"]))

    approach = build_section(osrm_route([(origin[1], origin[0]), start]))
    course_sec = build_section(osrm_route(course_pts))

    fetched = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
    route_doc = {
        "format": "demo-route",
        "version": 1,
        "synthetic": True,
        "courseId": cid,
        "source": {"router": "OSRM", "url": OSRM, "fetchedAt": fetched,
                   "attribution": "© OpenStreetMap contributors (ODbL)"},
        "origin": {"lat": origin[0], "lon": origin[1], "name": "현재 위치(시연)"},
        "approach": approach,
        "course": course_sec,
    }
    (out / f"{cid}.route.json").write_text(json.dumps(route_doc, ensure_ascii=False, indent=1), encoding="utf-8")
    if course_path.resolve() != (out / f"{cid}.json").resolve():
        shutil.copyfile(course_path, out / f"{cid}.json")

    header = {"format": "drive-replay", "version": 2, "name": f"{course['name']} 시연 (합성)",
              "synthetic": True, "course_id": cid, "energy_type": "ev"}
    stat = drive([approach, course_sec], out / f"{cid}.drive.jsonl", header, rnd)

    print(f"[{cid}]")
    print(f"접근 구간: {approach['distanceM'] / 1000:.2f}km, OSRM {approach['durationSec'] / 60:.0f}분, 안내 {len(approach['steps'])}개")
    print(f"코스 구간: {course_sec['distanceM'] / 1000:.2f}km (파일 값 {course['distanceKm']}km), "
          f"OSRM {course_sec['durationSec'] / 60:.0f}분 (파일 값 {course['durationMin']}분), 안내 {len(course_sec['steps'])}개")
    print(f"가짜 주행: {stat['totalM'] / 1000:.2f}km, {stat['durationSec'] / 60:.1f}분, 정차 {stat['stops']}번, "
          f"누적거리 +{stat['odoKm']:.1f}km, 배터리 -{stat['usedKwh']:.2f}kWh")
    print(f"출력: {out}")


if __name__ == "__main__":
    sys.exit(main())
