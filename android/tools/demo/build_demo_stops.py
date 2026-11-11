"""시연용 들를 곳 데이터 만들기 (PLEOS-63).

코스 주변 장소(B가 만든 poi-list JSON)마다 다음을 미리 계산해 `<코스 id>.stops.json`에 넣는다.

- 이탈 지점: 코스 위 점 중 "코스 → 장소 → 같은 코스 점" 왕복 도로 거리가 가장 짧은 곳.
  장소에서 직선으로 가장 가까운 점이 도로로도 가깝다는 보장이 없어서(강·고속도로 건너편)
  직선으로 가까운 후보 여러 개를 OSRM table로 실제 도로 거리를 재서 고른다.
  후보 점에는 코스 진행 방향을 bearings로 걸어서, 코스 방향으로 달리다 빠지고 코스 방향으로 합류하게 한다.
- 왕복 경로: 이탈 지점 → 장소(가는 길), 장소 → 이탈 지점(돌아오는 길). 회전 안내 포함(앱의 GuidanceTracker가 읽는 형식).
- 가짜 주행: 가는 길·돌아오는 길을 1초 간격 [초, 경도, 위도, km/h]로. 앱이 배속으로 재생한다.

앱은 이탈 지점에서 코스 기록을 멈추고, 돌아오는 길이 끝나면(= 이탈 지점) 기록을 이어 간다.

usage:
  python -I build_demo_stops.py <장소 JSON> <코스 id> [--out <assets/demo 폴더>]
  예: python -I build_demo_stops.py input/hwaseong-pois.json gyeonggi-heart-hwaseong-national-60min

먼저 build_demo_data.py로 <코스 id>.route.json을 만들어 둬야 한다(코스 좌표를 거기서 읽는다).
OSRM 공개 서버는 준비할 때 한 번만 부른다. 앱은 실행 중에 부르지 않는다.
경로 데이터 출처: © OpenStreetMap contributors (ODbL), OSRM.
"""
import argparse
import json
import math
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

# -I(격리 모드)에서는 스크립트 폴더가 import 경로에 없어서 직접 넣는다.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_demo_data import (  # noqa: E402
    ACCEL_KPH_PER_S, OSRM, build_section, cumulative, dist_m, osrm_route, point_at, speed_plan,
)

# 💡 제안 값
SAMPLE_STEP_M = 50.0  # 이탈 후보를 코스 위 이 간격으로 뽑는다
CANDIDATES = 90  # 도로 거리로 잴 후보 수 (OSRM 공개 서버 table 한도 100점)
REFINE_M = 1000.0  # 2차로 다시 잴 범위(1차 최선 점 앞뒤)
NEAR_M = 4000.0  # 장소에서 직선으로 이 거리(또는 가장 가까운 점 거리의 3배) 안의 코스 점만 후보로 본다
EDGE_SKIP_M = 1500.0  # 코스 시작·끝 이 거리 안에서는 빠지지 않는다(진입·완주 판정과 겹치지 않게)
BEARING_RANGE = 45  # 코스 방향 ± 이 각도의 도로에만 붙인다
LEAVE_KPH = 30.0  # 코스에서 빠질 때·합류할 때 속도


def bearing(a, b):
    """a→b 방위각(도). a, b = (경도, 위도)"""
    lon1, lat1, lon2, lat2 = map(math.radians, (*a, *b))
    y = math.sin(lon2 - lon1) * math.cos(lat2)
    x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(lon2 - lon1)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def course_samples(line, cum):
    """코스 위 SAMPLE_STEP_M 간격 점 (거리, 점, 진행 방향). 시작·끝 근처는 뺀다."""
    out = []
    s = EDGE_SKIP_M
    while s < cum[-1] - EDGE_SKIP_M:
        p = point_at(line, cum, s)
        ahead = point_at(line, cum, min(cum[-1], s + 20))
        out.append((s, p, round(bearing(p, ahead))))
        s += SAMPLE_STEP_M
    return out


def osrm_table(points, bearings):
    coords = ";".join(f"{lon:.6f},{lat:.6f}" for lon, lat in points)
    b = ";".join("" if x is None else f"{x},{BEARING_RANGE}" for x in bearings)
    url = f"{OSRM}/table/v1/driving/{coords}?annotations=distance,duration&bearings={b}"
    req = urllib.request.Request(url, headers={"User-Agent": "pleos-demo-data-builder"})
    with urllib.request.urlopen(req, timeout=30) as res:
        data = json.loads(res.read().decode("utf-8"))
    if data.get("code") != "Ok":
        raise SystemExit(f"OSRM table 오류: {data.get('code')} {data.get('message')}")
    return data


def pick_exit(poi, samples):
    """왕복 도로 거리가 가장 짧은 코스 점. 직선으로 가까운 코스 점들을 OSRM으로 잰다.

    가장 가까운 몇 점만 보면 그 점들이 모두 출구 없는 큰길 위일 때 먼 길로 돌아간다.
    그래서 반경 안의 점을 코스 순서대로 고르게 [CANDIDATES]개 뽑아 재고(1차),
    가장 좋은 점 앞뒤 [REFINE_M]를 [SAMPLE_STEP_M] 간격으로 다시 잰다(2차).
    """
    straight = min(dist_m(c[1], poi) for c in samples)
    radius = max(NEAR_M, straight * 3)
    near = [c for c in samples if dist_m(c[1], poi) <= radius]
    if len(near) > CANDIDATES:
        step = len(near) / CANDIDATES
        near = [near[int(i * step)] for i in range(CANDIDATES)]
    best = best_of(poi, near)
    if best is None:
        raise SystemExit("OSRM이 이탈 후보를 하나도 잇지 못했습니다")
    around = [c for c in samples if abs(c[0] - best[1][0]) <= REFINE_M][:CANDIDATES]
    refined = best_of(poi, around)
    if refined is not None and refined[0] < best[0]:
        best = refined
    return best[1], best[0], straight


def best_of(poi, candidates):
    """후보 중 왕복 (거리, 시간)이 가장 작은 것. ((거리, 시간), 후보) 또는 None"""
    table = osrm_table([c[1] for c in candidates] + [poi], [c[2] for c in candidates] + [None])
    dist, dur = table["distances"], table["durations"]
    k = len(candidates)
    best = None
    for i, c in enumerate(candidates):
        go, back = dist[i][k], dist[k][i]
        if go is None or back is None:
            continue
        cost = (go + back, dur[i][k] + dur[k][i])
        if best is None or cost < best[0]:
            best = (cost, c)
    return best


def detour_drive(section, start_kph, end_kph):
    """구간 하나를 1초 간격으로 달린다. [[초, 경도, 위도, km/h], ...]"""
    line = [tuple(c) for c in section["geometry"]]
    cum = cumulative(line)
    total = cum[-1]
    target = speed_plan(section)
    s, kph, t = 0.0, start_kph, 0
    p = line[0]
    out = [[0, round(p[0], 6), round(p[1], 6), round(kph, 1)]]
    while s < total - 0.5:
        t += 1
        want = target(s)
        left = total - s
        if left < 80:
            want = min(want, max(end_kph, 8.0, left / 80 * 30))
        if want > kph:
            kph = min(want, kph + ACCEL_KPH_PER_S)
        else:
            kph = max(want, kph - ACCEL_KPH_PER_S * 1.5)
        s = min(total, s + kph / 3.6)
        p = point_at(line, cum, s)
        out.append([t, round(p[0], 6), round(p[1], 6), round(kph, 1)])
    out[-1][3] = round(end_kph, 1)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pois", help="장소 목록 JSON (type: poi-list)")
    ap.add_argument("course_id", help="코스 id (assets/demo/<id>.route.json이 있어야 함)")
    ap.add_argument("--out", default=str(Path(__file__).resolve().parents[2] / "app/src/main/assets/demo"))
    args = ap.parse_args()

    out = Path(args.out)
    poi_doc = json.loads(Path(args.pois).read_text(encoding="utf-8"))
    route_doc = json.loads((out / f"{args.course_id}.route.json").read_text(encoding="utf-8"))
    line = [tuple(c) for c in route_doc["course"]["geometry"]]
    cum = cumulative(line)
    samples = course_samples(line, cum)

    stops = []
    for poi in poi_doc["pois"]:
        loc = (poi["longitude"], poi["latitude"])
        (at_m, exit_pt, exit_bearing), (round_m, round_s), straight = pick_exit(loc, samples)
        outbound = build_section(osrm_route([exit_pt, loc], bearings=[(exit_bearing, BEARING_RANGE), None]))
        inbound = build_section(osrm_route([loc, exit_pt], bearings=[None, (exit_bearing, BEARING_RANGE)]))
        stops.append({
            "id": poi["id"],
            "name": poi["name"],
            "address": poi.get("address", ""),
            "location": [round(loc[0], 6), round(loc[1], 6)],
            "exit": {"atM": round(at_m, 1), "location": [round(exit_pt[0], 6), round(exit_pt[1], 6)],
                     "bearing": exit_bearing},
            "extraDistanceM": round(outbound["distanceM"] + inbound["distanceM"], 1),
            "extraDurationSec": outbound["durationSec"] + inbound["durationSec"],
            "outbound": outbound,
            "inbound": inbound,
            "outboundDrive": detour_drive(outbound, LEAVE_KPH, 0.0),
            "inboundDrive": detour_drive(inbound, 0.0, LEAVE_KPH),
            "source": poi.get("source", ""),
        })
        print(f"{poi['name']}: 코스 {at_m / 1000:.2f}km 지점에서 빠짐 (장소까지 직선 최소 {straight / 1000:.2f}km), "
              f"왕복 {round_m / 1000:.2f}km · {round_s / 60:.0f}분 (table), "
              f"가는 길 {outbound['distanceM'] / 1000:.2f}km, 돌아오는 길 {inbound['distanceM'] / 1000:.2f}km")

    stops.sort(key=lambda s: s["exit"]["atM"])
    fetched = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
    doc = {
        "format": "demo-stops",
        "version": 1,
        "synthetic": True,
        "courseId": args.course_id,
        "source": {"pois": poi_doc.get("id", ""), "poisGeneratedAt": poi_doc.get("generatedAt", ""),
                   "router": "OSRM", "url": OSRM, "fetchedAt": fetched,
                   "attribution": "© OpenStreetMap contributors (ODbL)"},
        "stops": stops,
    }
    path = out / f"{args.course_id}.stops.json"
    path.write_text(json.dumps(doc, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    skipped = poi_doc.get("unresolved", [])
    if skipped:
        print("좌표가 없어 뺀 장소: " + ", ".join(s["name"] for s in skipped))
    print(f"출력: {path} ({path.stat().st_size / 1024:.0f}KB)")


if __name__ == "__main__":
    sys.exit(main())
