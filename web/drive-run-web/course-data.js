'use strict';
// 원본 코스 JSON과 화면용 데이터를 분리합니다. 서버에서도 normalize()를 재사용할 수 있습니다.
window.DRIVE_RUN_COURSES = (() => {
  const sources = Object.freeze({
    'bucheon-cat-91min': {
      file: 'data/courses/bucheon-cat-91min.json',
      region: '부천·인천'
    }
  });
  const scriptUrl = document.currentScript.src;

  function validLatLng(lat, lng) {
    return Number.isFinite(lat) && Number.isFinite(lng)
      && Math.abs(lat) <= 85.051129 && Math.abs(lng) <= 180;
  }

  function normalize(course, region) {
    if (!course || typeof course.id !== 'string' || !course.id
      || typeof course.name !== 'string' || !course.name.trim()
      || !Number.isFinite(course.distanceKm) || course.distanceKm <= 0
      || !Number.isFinite(course.durationMin) || course.durationMin <= 0
      || !Array.isArray(course.points) || course.points.length < 1
      || !Array.isArray(course.routePreview) || course.routePreview.length < 2) {
      throw new Error('계획 코스의 이름·거리·시간·좌표 데이터를 확인해 주세요.');
    }
    const path = Array.from(course.routePreview, point => {
      if (!Array.isArray(point) || point.length !== 2 || !validLatLng(point[0], point[1])) {
        throw new Error('계획 경로는 [위도, 경도] 순서의 유효한 좌표여야 합니다.');
      }
      return [point[1], point[0]];
    });
    const waypoints = Array.from(course.points, (point, index) => {
      if (!point || !validLatLng(point.lat, point.lng)) {
        throw new Error('경유 지점의 위도·경도를 확인해 주세요.');
      }
      return {name: typeof point.name === 'string' ? point.name : '경유 지점', order: index + 1, coordinates: [point.lng, point.lat]};
    });
    return {
      tripId: course.id,
      courseName: course.name,
      region: region || course.area,
      area: course.area,
      shape: course.shape,
      direction: course.direction,
      isDemo: true,
      routeType: 'planned',
      pathSource: 'team_route_preview',
      summarySource: 'course_plan',
      roadValidated: false,
      plannedDistanceKm: course.distanceKm,
      estimatedDurationMin: course.durationMin,
      path,
      waypoints
      // 연비·실제 거리·주행 시간·시작/종료 시각·소유자는 원본에 없으므로 만들지 않습니다.
    };
  }

  function hasId(id) {
    return Object.hasOwn(sources, id);
  }

  async function getById(id) {
    if (!hasId(id)) throw new Error('등록되지 않은 계획 코스입니다.');
    const source = sources[id];
    const response = await fetch(new URL(source.file, scriptUrl));
    if (!response.ok) throw new Error('계획 코스 파일을 불러오지 못했습니다. 주소와 서버 연결을 확인해 주세요.');
    const course = await response.json();
    if (course?.id !== id) throw new Error('요청한 ID와 계획 코스가 일치하지 않습니다.');
    return normalize(course, source.region);
  }

  return {hasId, getById, normalize};
})();
