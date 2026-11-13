// 서버 연동 전까지 사용하는 예시 데이터입니다. 실제 측정값이 아닙니다.
window.DRIVE_RUN_MOCK = {
  trip: {
    tripId: "abc123",
    courseName: "수원 하트 코스",
    region: "수원시",
    startedAt: "2026-11-05T10:12:00",
    endedAt: "2026-11-05T11:36:10",
    distanceKm: 42.3,
    durationSec: 5050,
    avgFuelEconomy: 12.8,
    // 수원 화성 일대의 임의 좌표입니다. 실제 GPS 측정이나 도로 안내 경로가 아닙니다.
    // GeoJSON 규칙: [경도, 위도] 순서. 서버도 같은 배열 형식으로 전달하면 됩니다.
    isDemo: true,
    userId: 'demo-user-me',
    routeType: 'demo', // demo / planned(계획) / recorded(이동 기록)
    pathSource: 'synthetic',
    summarySource: 'independent_demo', // 아래 좌표로 계산한 요약이 아닙니다.
    roadValidated: false,
    path: [
      [127.0166, 37.2887], [127.0148, 37.2905], [127.0135, 37.2928],
      [127.0127, 37.2952], [127.0144, 37.2972], [127.0177, 37.2990],
      [127.0210, 37.3005], [127.0250, 37.3001], [127.0287, 37.2985],
      [127.0312, 37.2963], [127.0324, 37.2935], [127.0306, 37.2910],
      [127.0273, 37.2892], [127.0235, 37.2878], [127.0198, 37.2873],
      [127.0166, 37.2887]
    ]
  },
  ranking: {
    isDemo: true,
    currentUserId: 'demo-user-me', // 로그인 사용자가 아니라 고정 데모 사용자입니다.
    month: "2026-11", myRank: 3,
    podium: [
      { rank: 1, userId: 'demo-user-king', isMe: false, nickname: "드라이브왕", avatar: "👑" },
      { rank: 2, userId: 'demo-user-cat', isMe: false, nickname: "달리는고양이", avatar: "🐱" },
      { rank: 3, userId: 'demo-user-me', isMe: true, nickname: "나", avatar: "🚙" }
    ],
    categories: [
      { title: "⛽ 평균 연비", entries: [{userId:'demo-user-king',isMe:false,name:"드라이브왕",value:"14.2 km/L"},{userId:'demo-user-me',isMe:true,name:"나",value:"12.8 km/L"},{userId:'demo-user-cat',isMe:false,name:"달리는고양이",value:"12.1 km/L"}] },
      { title: "◷ 누적 주행시간", entries: [{userId:'demo-user-cat',isMe:false,name:"달리는고양이",value:"18시간 40분"},{userId:'demo-user-king',isMe:false,name:"드라이브왕",value:"16시간 20분"},{userId:'demo-user-me',isMe:true,name:"나",value:"11시간 22분"}] },
      { title: "↗ 누적 주행거리", entries: [{userId:'demo-user-king',isMe:false,name:"드라이브왕",value:"412 km"},{userId:'demo-user-cat',isMe:false,name:"달리는고양이",value:"358 km"},{userId:'demo-user-me',isMe:true,name:"나",value:"301 km"}] }
    ]
  }
};

// QR에 넣은 URL의 tripId로 찾는 데모 기록 3개입니다.
// 첫 번째 기록과 친구 랭킹은 기존 샘플을 그대로 유지합니다.
window.DRIVE_RUN_TRIPS = {
  abc123: window.DRIVE_RUN_MOCK.trip,
  def456: {
    tripId: 'def456', courseName: '수원 장안문 데모 코스', region: '수원시',
    startedAt: '2026-11-06T09:00:00', endedAt: '2026-11-06T09:35:20',
    distanceKm: 18.6, durationSec: 2120, avgFuelEconomy: 14.1, isDemo: true,
    userId: 'demo-user-king', routeType: 'demo', pathSource: 'synthetic',
    summarySource: 'independent_demo', roadValidated: false,
    path: [
      [127.0090, 37.2950], [127.0112, 37.2968], [127.0144, 37.2972],
      [127.0177, 37.2990], [127.0210, 37.3005], [127.0240, 37.3012],
      [127.0270, 37.3002], [127.0292, 37.2987]
    ]
  },
  ghi789: {
    tripId: 'ghi789', courseName: '수원 팔달문 데모 코스', region: '수원시',
    startedAt: '2026-11-07T16:10:00', endedAt: '2026-11-07T17:02:05',
    distanceKm: 27.4, durationSec: 3125, avgFuelEconomy: 11.9, isDemo: true,
    userId: 'demo-user-cat', routeType: 'demo', pathSource: 'synthetic',
    summarySource: 'independent_demo', roadValidated: false,
    path: [
      [127.0150, 37.2830], [127.0180, 37.2838], [127.0215, 37.2844],
      [127.0250, 37.2855], [127.0280, 37.2875], [127.0306, 37.2910],
      [127.0324, 37.2935], [127.0310, 37.2950]
    ]
  }
};
