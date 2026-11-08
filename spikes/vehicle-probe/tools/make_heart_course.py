"""가상주행 시험용 하트 모양 코스를 규격 ① 코스 JSON(course/1)으로 만든다.

실제 도로를 따르지 않는다. 에뮬레이터는 위치만 옮기므로 도로가 없어도 기록·궤적 시험에는 충분하다.
하트 곡선은 이전 시험 앱(route-draw)의 '샘플 하트'와 같은 식이다.

usage:
  python -I make_heart_course.py <출력 경로> [중심 위도=37.3950] [중심 경도=127.1110] [크기 m=600] [모양=heart|figure8]

figure8은 스스로 한 번 교차하는 8자 코스다. 코스 판정이 교차점에서 헷갈리는지 시험한다.
"""
import json
import math
import sys

EARTH_R = 6_371_000.0


def dist_m(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * EARTH_R * math.asin(math.sqrt(h))


def heart(lat0, lon0, radius_m, n=120):
    m_lat = 111_320.0
    m_lon = m_lat * math.cos(math.radians(lat0))
    scale = radius_m / 16.0
    out = []
    for i in range(n + 1):
        t = 2 * math.pi * i / n
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        out.append((round(lat0 + y * scale / m_lat, 6), round(lon0 + x * scale / m_lon, 6)))
    return out


def figure8(lat0, lon0, radius_m, n=160):
    """제르노 렘니스케이트(8자). 가운데에서 한 번 교차한다. 시작점은 오른쪽 끝."""
    m_lat = 111_320.0
    m_lon = m_lat * math.cos(math.radians(lat0))
    out = []
    for i in range(n + 1):
        t = 2 * math.pi * i / n
        x = radius_m * math.cos(t)
        y = radius_m * math.sin(t) * math.cos(t)
        out.append((round(lat0 + y / m_lat, 6), round(lon0 + x / m_lon, 6)))
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    lat0 = float(sys.argv[2]) if len(sys.argv) > 2 else 37.3950
    lon0 = float(sys.argv[3]) if len(sys.argv) > 3 else 127.1110
    size = float(sys.argv[4]) if len(sys.argv) > 4 else 600.0
    shape = sys.argv[5] if len(sys.argv) > 5 else "heart"
    pts = figure8(lat0, lon0, size) if shape == "figure8" else heart(lat0, lon0, size)
    length = sum(dist_m(a, b) for a, b in zip(pts, pts[1:]))
    third = len(pts) // 3

    def place(p, name):
        return {"lat": p[0], "lon": p[1], "name": name}

    course = {
        "schema": "course/1",
        "course_id": f"test-{shape}",
        "region": "시험",
        "label": "A",
        "name": f"시험용 {'8자' if shape == 'figure8' else '하트'} 코스 (도로를 따르지 않는 가상주행 시험용)",
        # GeoJSON은 [경도, 위도] 순서다.
        "shape": {"type": "LineString", "coordinates": [[p[1], p[0]] for p in pts]},
        "start": place(pts[0], "출발"),
        "waypoints": [place(pts[third], "경유 1"), place(pts[2 * third], "경유 2")],
        "destination": place(pts[-1], "도착"),
        "distance_m": round(length),
        "est_duration_sec": round(length / (40 / 3.6)),
        "corridor_m": 60,
    }
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        json.dump(course, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"{sys.argv[1]}: 점 {len(pts)}개, 길이 {length / 1000:.2f}km")


if __name__ == "__main__":
    main()
