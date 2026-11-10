'use strict';
// 지도 코드만 분리했습니다. 기록/랭킹 계산이나 버튼 동작은 app.js가 담당합니다.
window.driveRouteMap = (() => {
  const VERSION = '5.6.2'; // CDN 버전을 고정해 예고 없는 변경을 방지합니다.
  const STYLE = 'https://tiles.openfreemap.org/styles/liberty';
  let map = null;
  let coordinates = [];
  let waypoints = [];
  let styleReady = false;
  let initializing = false;
  let hasMapError = false;
  const pendingAssets = {};
  let loadingTimer;
  let resizeObserver;
  let mapGeneration = 0;
  const status = () => document.querySelector('#map-status');
  const retry = () => document.querySelector('#retry-map');

  function message(text, failed = false) {
    status().textContent = text;
    status().classList.toggle('map-error', failed);
    retry().hidden = !failed;
  }

  function failMap(text) {
    hasMapError = true;
    clearTimeout(loadingTimer);
    message(text, true);
  }

  // CDN의 JS와 CSS도 실패할 수 있으므로 시간 제한과 재시도를 제공합니다.
  function loadAsset(type) {
    if (type === 'js' && window.maplibregl) return Promise.resolve();
    if (type === 'css' && document.querySelector('#maplibre-css')?.sheet) return Promise.resolve();
    if (pendingAssets[type]) return pendingAssets[type];
    pendingAssets[type] = new Promise((resolve, reject) => {
      const element = document.createElement(type === 'js' ? 'script' : 'link');
      if (type === 'js') {
        element.src = `https://unpkg.com/maplibre-gl@${VERSION}/dist/maplibre-gl.js`;
        element.async = true;
      } else {
        element.id = 'maplibre-css';
        element.rel = 'stylesheet';
        element.href = `https://unpkg.com/maplibre-gl@${VERSION}/dist/maplibre-gl.css`;
      }
      const timer = setTimeout(() => finish(new Error('지도 라이브러리 응답 지연')), 15000);
      function finish(error) {
        clearTimeout(timer);
        element.onload = element.onerror = null;
        if (error) { element.remove(); reject(error); } else resolve();
      }
      element.onload = () => finish();
      element.onerror = () => finish(new Error('지도 라이브러리 연결 실패'));
      document.head.append(element);
    }).finally(() => { delete pendingAssets[type]; });
    return pendingAssets[type];
  }

  function validPath(path) {
    return Array.isArray(path) && path.length >= 2 && Array.from(path).every(point =>
      Array.isArray(point) && point.length >= 2 &&
      Number.isFinite(point[0]) && Number.isFinite(point[1]) &&
      Math.abs(point[0]) <= 180 && Math.abs(point[1]) <= 85.051129);
  }

  function fitRoute() {
    if (!map || !coordinates.length || !document.querySelector('#route-map').clientWidth) return;
    const bounds = [...coordinates, ...waypoints.map(point => point.coordinates)]
      .reduce((box, point) => box.extend(point), new maplibregl.LngLatBounds());
    // 위쪽 확대/축소 버튼과 아래쪽 출처 표시를 피해 경로 전체를 넣습니다.
    map.fitBounds(bounds, {padding: {top: 55, right: 48, bottom: 40, left: 24}, maxZoom: 16, duration: 0});
  }

  function drawRoute() {
    if (!map || !styleReady) return;
    const colors = getComputedStyle(document.documentElement);
    const routeColor = colors.getPropertyValue('--primary').trim() || '#C8FF00';
    const casingColor = colors.getPropertyValue('--map-route-casing').trim() || '#0B0B0B';
    const data = {
      type: 'FeatureCollection',
      features: coordinates.length ? [{
        type: 'Feature', properties: {},
        geometry: {type: 'LineString', coordinates}
      }] : []
    };
    const source = map.getSource('drive-route');
    if (source) source.setData(data);
    else {
      // 경로 표현만 변경합니다. 라임과 어두운 외곽선으로 밝은 지도에서도 구분합니다.
      map.addSource('drive-route', {type: 'geojson', data});
      map.addLayer({
        id: 'drive-route-casing', type: 'line', source: 'drive-route',
        layout: {'line-join': 'round', 'line-cap': 'round'},
        paint: {'line-color': casingColor, 'line-width': 9, 'line-opacity': 0.95}
      });
      map.addLayer({
        id: 'drive-route-line', type: 'line', source: 'drive-route',
        layout: {'line-join': 'round', 'line-cap': 'round'},
        paint: {'line-color': routeColor, 'line-width': 5, 'line-opacity': 1}
      });
    }
    // 경유지는 경로와 별도 GeoJSON으로 만들어 이름과 순서도 유지합니다.
    const waypointData = {
      type: 'FeatureCollection',
      features: waypoints.map((point, index) => ({
        type: 'Feature',
        properties: {name: point.name, order: point.order ?? index + 1},
        geometry: {type: 'Point', coordinates: point.coordinates}
      }))
    };
    const waypointSource = map.getSource('drive-waypoints');
    if (waypointSource) waypointSource.setData(waypointData);
    else if (waypoints.length) {
      map.addSource('drive-waypoints', {type: 'geojson', data: waypointData});
      map.addLayer({
        id: 'drive-waypoint-points', type: 'circle', source: 'drive-waypoints',
        paint: {'circle-radius': 4, 'circle-color': routeColor, 'circle-stroke-width': 2, 'circle-stroke-color': casingColor}
      });
    }
    fitRoute();
  }

  // FastAPI 응답도 renderTrip(trip)을 호출하면 이 함수로 전달됩니다.
  function updateTrip(trip) {
    trip = trip && typeof trip === 'object' ? trip : {};
    coordinates = validPath(trip.path) ? trip.path.map(point => point.slice(0, 2)) : [];
    waypoints = Array.isArray(trip.waypoints) ? trip.waypoints.filter(point => point
      && Array.isArray(point.coordinates) && point.coordinates.length === 2
      && Number.isFinite(point.coordinates[0]) && Number.isFinite(point.coordinates[1])
      && Math.abs(point.coordinates[0]) <= 180 && Math.abs(point.coordinates[1]) <= 85.051129) : [];
    const notices = {
      demo: '데모 경로 · 요약 수치는 별도 샘플',
      planned: waypoints.length ? `계획 경로 · ${waypoints.length}개 경유지 · 실주행 아님`
        : trip.isDemo === false ? '계획 경로 · 실제 이동 기록 아님' : '계획 경로(데모) · 실측 아님',
      recorded: trip.isDemo === false ? '이동 경로 · 서버 제공 좌표' : '이동 경로(데모) · 실측 아님'
    };
    document.querySelector('#route-notice').textContent = typeof trip.routeType === 'string' && Object.hasOwn(notices, trip.routeType)
      ? notices[trip.routeType] : '경로 유형 미확인 · 실측 여부 미확인';
    drawRoute();
    if (map && styleReady && !hasMapError && !coordinates.length) message('표시할 GPS 경로가 없습니다. 좌표 데이터를 확인해 주세요.');
  }

  function resize() {
    if (!map || document.querySelector('#result-view').hidden) return;
    map.resize();
    fitRoute();
  }

  async function init() {
    if (initializing) return;
    initializing = true;
    const generation = ++mapGeneration;
    retry().disabled = true;
    clearTimeout(loadingTimer);
    if (map) { map.remove(); map = null; }
    styleReady = false;
    hasMapError = false;
    message('지도를 불러오는 중입니다.');
    try {
      await Promise.all([loadAsset('js'), loadAsset('css')]);
      map = new maplibregl.Map({
        container: 'route-map', style: STYLE,
        center: [127.022, 37.294], zoom: 13,
        attributionControl: false, // 카드 아래에 출처를 항상 펼쳐 표시합니다.
        dragRotate: false, pitchWithRotate: false
      });
      const instance = map;
      map.touchZoomRotate.disableRotation();
      map.addControl(new maplibregl.NavigationControl({showCompass: false}), 'top-right');
      map.on('style.load', () => {
        if (generation !== mapGeneration) return;
        styleReady = true;
        drawRoute();
      });
      map.on('error', () => {
        if (generation !== mapGeneration) return;
        failMap('지도의 일부 또는 전체를 불러오지 못했습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.');
      });
      map.on('idle', () => {
        // 실패한 타일도 로딩 종료로 처리될 수 있으므로 오류는 재시도 전까지 유지합니다.
        if (generation !== mapGeneration || hasMapError || !navigator.onLine || !instance.areTilesLoaded()) return;
        clearTimeout(loadingTimer);
        message(coordinates.length ? '드래그 이동 · 두 손가락/± 확대·축소' : '표시할 GPS 경로가 없습니다. 좌표 데이터를 확인해 주세요.');
      });
      map.getCanvas().addEventListener('webglcontextlost', () => {
        if (generation !== mapGeneration) return;
        failMap('지도 그래픽 연결이 끊어졌습니다. 지도를 다시 불러와 주세요.');
      });
      loadingTimer = setTimeout(() => {
        if (generation === mapGeneration) failMap('지도 응답이 지연되고 있습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.');
      }, 20000);
      if (!resizeObserver) {
        resizeObserver = new ResizeObserver(resize);
        resizeObserver.observe(document.querySelector('#route-map'));
      }
    } catch (error) {
      failMap('지도를 불러오지 못했습니다. 인터넷 연결과 브라우저의 WebGL 지원을 확인한 뒤 다시 시도해 주세요.');
    } finally {
      initializing = false;
      retry().disabled = false;
    }
  }

  window.addEventListener('offline', () => failMap('인터넷 연결이 끊어졌습니다. 연결 후 지도를 다시 불러와 주세요.'));
  window.addEventListener('online', () => init());
  document.querySelector('#retry-map').addEventListener('click', init);
  return {init, updateTrip, resize};
})();
