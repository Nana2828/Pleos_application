'use strict';
// 이 파일은 화면 전환과 데이터 표시를 담당합니다.
// MOCK 데이터를 API 결과로 교체하면 서버와 연결할 수 있습니다.
const $ = (selector) => document.querySelector(selector);
const mock = window.DRIVE_RUN_MOCK;

const objectOrEmpty = value => value && typeof value === 'object' && !Array.isArray(value) ? value : {};
const listOrEmpty = value => Array.isArray(value) ? Array.from(value) : [];
const displayText = (value, fallback = '—') => typeof value === 'string' && value.trim() ? value.trim() : fallback;
function nonNegativeNumber(value) {
  if (typeof value !== 'number' && !(typeof value === 'string' && value.trim())) return null;
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? number : null;
}
function measurementText(value) {
  const number = nonNegativeNumber(value);
  return number === null ? '—' : number.toFixed(1);
}
function durationText(total) {
  const number = nonNegativeNumber(total);
  if (number === null) return '—';
  const seconds = Math.floor(number);
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
function shortDate(iso) {
  if (typeof iso !== 'string' || !iso.trim()) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '-';
  return `${date.getFullYear()}.${String(date.getMonth()+1).padStart(2,'0')}.${String(date.getDate()).padStart(2,'0')}`;
}
function renderTrip(trip) {
  trip = objectOrEmpty(trip);
  const planned = trip.routeType === 'planned';
  $('#course-name').textContent = displayText(trip.courseName);
  $('#result-title').textContent = planned ? 'PLANNED ROUTE' : 'DRIVE RECORD';
  $('#drive-date').textContent = planned
    ? '계획 코스'
    : `${shortDate(trip.startedAt)} · ${displayText(trip.region)}`;
  $('#trip-id').textContent = `기록 ID · ${displayText(trip.tripId)}`;
  $('#route-map').setAttribute('aria-label', `${displayText(trip.courseName, '코스')} 지도`);
  const routeLabels = {demo: '데모 경로', planned: '계획 경로', recorded: '이동 경로'};
  $('#result-view .course-chip').textContent = typeof trip.routeType === 'string' && Object.hasOwn(routeLabels, trip.routeType)
    ? routeLabels[trip.routeType] : '경로 유형 미확인';
  $('#distance-kind').textContent = planned ? '계획 거리' : '주행 거리';
  $('#duration-label').textContent = planned ? '예상시간' : '주행 시간';
  $('#distance').textContent = measurementText(planned ? trip.plannedDistanceKm : trip.distanceKm);
  const minutes = nonNegativeNumber(trip.estimatedDurationMin);
  $('#duration').textContent = planned ? minutes === null ? '—' : `${minutes}분` : durationText(trip.durationSec);
  $('#fuel').textContent = measurementText(trip.avgFuelEconomy);
  $('#fuel-unit').textContent = trip.efficiencyUnit === 'kwh' ? 'km/kWh' : trip.efficiencyUnit === 'l' || trip.isDemo ? 'km/L' : '—';
  const time = iso => typeof iso === 'string' && Number.isFinite(Date.parse(iso)) ? new Date(iso).toLocaleString('ko-KR', {hour12: false}) : '—';
  $('#trip-times').textContent = planned ? '' : '시작 ' + time(trip.startedAt) + ' · 종료 ' + time(trip.endedAt);
  $('#trip-times').hidden = planned;
  $('.demo-badge').textContent = trip.pathSource === 'vehicle_qr' ? 'VEHICLE QR' : 'DEMO / 01';
  $('.demo-badge').setAttribute('aria-label', trip.pathSource === 'vehicle_qr' ? '차량 QR 주행 기록' : '샘플 데이터');
  $('#region').textContent = displayText(trip.region);
  window.driveRouteMap.updateTrip(trip);
}

// 서버 연결 시에는 이 함수의 데이터 조회 부분만 실제 fetch 호출로 교체합니다.
async function getTripById(tripId) {
  const trips = objectOrEmpty(window.DRIVE_RUN_TRIPS);
  if (window.DRIVE_RUN_COURSES?.hasId(tripId)) return window.DRIVE_RUN_COURSES.getById(tripId);
  if (!Object.hasOwn(trips, tripId)) {
    throw new Error('해당 주행 기록을 찾을 수 없습니다. QR코드 또는 링크를 확인해 주세요.');
  }
  return trips[tripId];
}

// 향후 API 응답이 늦게 와도 이전 조회가 최신 화면을 덮어쓰지 않도록 합니다.
let tripRequestVersion = 0;

function setTripState(state, detail = '') {
  const view = $('#result-view');
  view.dataset.tripState = state;
  view.setAttribute('aria-busy', String(state === 'loading'));
  for (const child of view.children) {
    child.hidden = child.id === 'trip-message' ? state === 'ready' : state !== 'ready';
  }
  $('#trip-message-title').textContent = state === 'error' ? '주행 기록을 확인해 주세요' : '주행 기록 조회 중';
  $('#trip-message-detail').textContent = detail;
  $('#trip-sample-link').hidden = state !== 'error';
}

async function loadTripFromUrl() {
  const requestVersion = ++tripRequestVersion;
  setTripState('loading', '주행 ID를 확인하고 있습니다.');
  try {
    const params = new URLSearchParams(location.search);
    const ids = params.getAll('tripId');
    const payloads = params.getAll('d');
    if (payloads.length > 1 || ids.length > 1) throw new Error('QR 주소에 중복된 데이터가 있습니다.');
    if (params.has('d')) {
      const trip = window.DRIVE_RUN_QR.decode(payloads[0], ids.length ? ids[0] : null);
      renderTrip(trip);
      setTripState('ready');
      window.driveRouteMap.init();
      return;
    }
    // ID가 없는 기존 주소는 첫 번째 샘플로 열고, 빈 값이나 중복 ID는 거부합니다.
    const tripId = ids.length === 0 ? 'abc123' : ids[0];
    if (ids.length > 1 || !/^[A-Za-z0-9_-]{1,64}$/.test(tripId)) {
      throw new Error('주행 ID 형식이 올바르지 않습니다. QR코드 또는 링크를 다시 확인해 주세요.');
    }
    const trip = await getTripById(tripId);
    if (requestVersion !== tripRequestVersion) return;
    if (!trip || Array.isArray(trip) || trip.tripId !== tripId) throw new Error('요청한 ID와 주행 기록이 일치하지 않습니다.');
    renderTrip(trip);
    setTripState('ready');
    window.driveRouteMap.init();
  } catch (error) {
    if (requestVersion !== tripRequestVersion) return;
    setTripState('error', displayText(error?.message, '주행 기록을 불러오지 못했습니다. 다시 시도해 주세요.'));
  }
}
// 서버가 전달한 순서를 그대로 순위로 표시합니다. 데이터를 다시 정렬하지 않습니다.
let rankingData = null;
let selectedCategory = 0;
function isMyEntry(entry) {
  entry = objectOrEmpty(entry);
  // 명시적인 true/false를 우선합니다. 닉네임으로 false를 뒤집지 않습니다.
  if (typeof entry.isMe === 'boolean') return entry.isMe;
  const currentUserId = rankingData?.currentUserId;
  return typeof currentUserId === 'string' && currentUserId.trim() !== ''
    && typeof entry.userId === 'string' && entry.userId === currentUserId;
}
const categoryLabel = title => displayText(title, '').replace(/^[^\p{L}\p{N}]+/u, '');

// 종합 시상대만 상위 3명을 표시합니다. 점수나 순위는 새로 계산하지 않습니다.
function renderMiniPodium(podium) {
  podium = listOrEmpty(podium);
  $('#overall-podium').replaceChildren(...[2, 1, 3].map(rank => {
    const person = podium.find(entry => entry && nonNegativeNumber(entry.rank) === rank);
    const isMe = isMyEntry(person);
    const column = document.createElement('li');
    column.className = `mini-person podium-rank-${rank}${isMe ? ' is-me' : ''}`;
    const avatar = document.createElement('span');
    avatar.className = 'podium-avatar';
    avatar.textContent = displayText(person?.avatar);
    avatar.setAttribute('aria-hidden', 'true');
    const name = document.createElement('span');
    name.className = 'podium-name';
    name.textContent = displayText(person?.nickname, person ? '이름 없음' : '집계 전');
    name.title = name.textContent;
    const step = document.createElement('span');
    step.className = 'podium-step';
    step.textContent = `${rank}위`;
    column.append(avatar, name, step);
    return column;
  }));
}

function renderRanking(ranking) {
  ranking = objectOrEmpty(ranking);
  rankingData = ranking;
  const rank = nonNegativeNumber(ranking.myRank);
  $('#my-rank').textContent = Number.isInteger(rank) && rank > 0 ? `${rank}위` : '—';
  renderMiniPodium(ranking.podium ?? []);
  const month = /^(\d{4})-(\d{2})$/.exec(ranking.month ?? '');
  $('#ranking-month').textContent = month ? `${month[1]}년 ${Number(month[2])}월 · 고정 데모 랭킹` : '고정 데모 랭킹';
  const categories = listOrEmpty(ranking.categories);
  selectedCategory = Math.min(selectedCategory, Math.max(0, categories.length - 1));
  $('#ranking-tabs').replaceChildren(...categories.map((category, index) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.id = `ranking-tab-${index}`;
    button.className = 'ranking-tab';
    button.textContent = categoryLabel(category?.title) || `부문 ${index + 1}`;
    button.setAttribute('role', 'tab');
    button.setAttribute('aria-controls', 'friend-ranking-panel');
    button.addEventListener('click', () => selectCategory(index));
    button.addEventListener('keydown', event => {
      const keys = ['ArrowLeft', 'ArrowRight', 'Home', 'End'];
      if (!keys.includes(event.key)) return;
      event.preventDefault();
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? categories.length - 1
        : (index + (event.key === 'ArrowRight' ? 1 : -1) + categories.length) % categories.length;
      selectCategory(next);
      document.getElementById(`ranking-tab-${next}`).focus();
    });
    return button;
  }));
  selectCategory(selectedCategory);
}

function selectCategory(index) {
  selectedCategory = index;
  const category = listOrEmpty(rankingData?.categories)[index];
  const entries = listOrEmpty(category?.entries).map(objectOrEmpty);
  const label = categoryLabel(category?.title) || '친구 랭킹';
  document.querySelectorAll('.ranking-tab').forEach((tab, i) => {
    const selected = i === index;
    tab.classList.toggle('selected', selected);
    tab.setAttribute('aria-selected', String(selected));
    tab.tabIndex = selected ? 0 : -1;
  });
  const panel = $('#friend-ranking-panel');
  if (category) panel.setAttribute('aria-labelledby', `ranking-tab-${index}`);
  else panel.removeAttribute('aria-labelledby');
  $('#category-title').textContent = `${label} · ${entries.length}명`;
  const myIndex = entries.findIndex(isMyEntry);
  $('#category-self').textContent = myIndex >= 0
    ? `내 순위 ${myIndex + 1}위 · ${displayText(entries[myIndex].value)}` : '이 부문에 내 기록이 없습니다.';
  $('#find-my-rank').disabled = myIndex < 0;
  $('#ranking-scroll').setAttribute('aria-label', `${label} 전체 친구 순위`);
  // 이 목록은 제한 없이 전체 참여자를 표시합니다. 상위 3명 제한은 시상대에만 적용합니다.
  $('#ranking-list').replaceChildren(...entries.map((entry, position) => {
    const row = document.createElement('li');
    row.className = `rank-row${isMyEntry(entry) ? ' me' : ''}`;
    row.tabIndex = -1;
    const number = document.createElement('span');
    number.className = 'rank-number'; number.textContent = `${position + 1}위`;
    const name = document.createElement('span');
    name.className = 'friend-name'; name.textContent = displayText(entry.name, '이름 없음');
    const value = document.createElement('strong');
    value.className = 'friend-value'; value.textContent = displayText(entry.value);
    row.append(number, name, value);
    return row;
  }));
  $('#ranking-empty').hidden = entries.length > 0;
  $('#ranking-scroll').scrollTop = 0;
}

// 전체 페이지 대신 순위 목록만 움직이고 내 행에 키보드 포커스를 줍니다.
function findMyRank() {
  const row = $('#ranking-list .me');
  if (!row) return;
  const scroller = $('#ranking-scroll');
  const top = row.getBoundingClientRect().top - scroller.getBoundingClientRect().top
    + scroller.scrollTop - (scroller.clientHeight - row.offsetHeight) / 2;
  row.focus({preventScroll: true});
  scroller.scrollTo({top: Math.max(0, top), behavior: 'instant'});
}
function showPage(page) {
  for (const name of ['result','ranking']) {
    const view = $(`#${name}-view`); const visible = page === name;
    view.hidden = !visible; view.classList.toggle('active',visible);
  }
  document.querySelectorAll('.nav-item').forEach(btn => {
    const selected = btn.dataset.page === page;
    btn.classList.toggle('selected',selected);
    btn.setAttribute('aria-current', selected ? 'page' : 'false');
  });
  if (location.hash !== `#${page}`) history.replaceState(null,'',`#${page}`);
  window.scrollTo({top:0,behavior:'instant'});
  if (page === 'result') requestAnimationFrame(() => window.driveRouteMap.resize());
}
$('#find-my-rank').addEventListener('click', findMyRank);

document.querySelectorAll('.nav-item').forEach(btn => btn.addEventListener('click',()=>showPage(btn.dataset.page)));
window.addEventListener('hashchange',()=>showPage(location.hash === '#ranking' ? 'ranking' : 'result'));
renderRanking(mock.ranking);
showPage(location.hash === '#ranking' ? 'ranking' : 'result');
loadTripFromUrl();

