# 인수인계: E 역할

> **Android Studio의 Claude에게:** 이 파일은 E 담당이 맡은 일 전체를 정리한 거예요. E 담당과 작업할 때 이 내용을 기준으로 삼고, 레포의 `CLAUDE.md`, `CONTRIBUTING.md`, `README.md` 규칙을 함께 따라 주세요.
>
> 시작할 때 이렇게 말하면 돼요: **"docs/handoff/role-e.md 읽고 내 작업 도와줘"**

기준일: 2026-11-09(월). 마감: **2026-11-15(일)**.

---

## 1. 🙋 내 역할

**E. 인포 화면 UI + 일정·GitHub·Jira 관리**

- 인포(차 화면) 앱의 **화면 UI** 전부
- 팀 **일정** 관리
- **GitHub·Jira 관리**: 이슈, 보드, 브랜치 규칙, 풀 리퀘스트 확인(매일 리뷰)

### 팀 역할 (누구에게 물어볼지)

| 역할 | 담당 | 나와 관계 |
|---|---|---|
| A | 기획서, 폰 웹(결과, 친구 비교, QR), 테스트 체크리스트 | 결과 QR이 열 페이지 |
| B | 경로 프리셋, 경로 JSON 규격, DB | 코스 목록·모양 데이터 |
| C | FastAPI 서버, 가상주행 | 화면이 부르는 API |
| D | 인포 앱 코어(지도, 경로 안내, 주행 기록) | **같은 앱**, `core/` 담당 |
| E | 인포 화면 UI, 일정, GitHub·Jira | 나 |

---

## 2. 🎫 내 Jira 티켓

보드: 팀 Jira PLEOS 보드 (주소는 팀 안에서 공유)

### 인포 화면 UI (에픽 PLEOS-7)

| 티켓 | 할 일 | UI 시안 |
|---|---|---|
| PLEOS-47 | 차량 UX 가이드 확인·반영 (주행 중 화면 제한 등) | 먼저 읽기 |
| PLEOS-42 | 지역 입력 화면 | `docs/ui/page-1.png` |
| PLEOS-43 | 코스 목록·모양 미리보기 화면 | `docs/ui/page-2.png` |
| PLEOS-44 | 안내 확인 화면 (+ 시작점까지 안내) | `docs/ui/page-3.png`, `page-4.png` |
| PLEOS-45 | 주행 중 화면 | `docs/ui/page-5.png` |
| PLEOS-46 | 결과 화면 + 결과 QR | `docs/ui/page-6.png` |

`page-7`, `page-8`(폰 결과, 친구 비교)은 A 담당의 폰 웹이에요.

### 일정·GitHub·Jira 관리 (에픽 PLEOS-1)

| 티켓 | 할 일 | 상태 |
|---|---|---|
| PLEOS-13 | 전체 일정표 확정 (날짜별 누가 무엇을) | 할 일, **오늘 필요** |
| PLEOS-14 | GitHub 레포 규칙 정리 | ✅ 완료 (PR #4) |
| PLEOS-15 | Jira 보드·GitHub 연동 설정 | 할 일 |
| PLEOS-57 | 매일 PR 검토·보드 정리 (~11/15) | 할 일, 매일 |

---

## 3. 📱 화면 흐름 (시나리오: 수원 하트 코스)

1. **지역 입력** (42): "어디로 가시겠어요?" → "수원" (데모는 터치 입력 추천)
2. **코스 목록** (43): "A. 표범 B. 하트 C. 촛불", 지도 위 모양 미리보기 → "B"
3. 앱이 지역·코스를 서버에 보내고 경로·모양 데이터를 받음 (D의 `core/`가 처리)
4. **안내 확인** (44): "B 경로로 안내합니다." 확인 → 시작점까지 안내 (NaviHelper SDK)
5. **주행 중** (45): 추천 경로에 처음 들어가면 기록 시작. 이동 경로, 연비, 시간, 거리
6. **결과 + QR** (46): 지도 위 이동 경로, 연비·거리·시간, 시작/종료 시각, 결과 QR

자세한 흐름과 API: `docs/scenario-flow.md`

---

## 4. 🛠️ 기술과 코드 구조

- Kotlin, **Jetpack Compose** (Android Automotive), **MVVM** (ViewModel + StateFlow)
- 템플릿: Car(Automotive) → Empty Activity, minSdk 28, 테스트는 Pleos Connect Emulator(API 34)
- 지도: Pleos에 지도 화면 SDK가 없을 수 있어서, 데모는 경로선을 **Compose Canvas**로 그리는 방법도 가능 (확인 필요)
- 차량 UX 규정: 주행 중 화면은 조작을 줄여야 해요. PLEOS-47에서 공식 가이드 확인

### 내 폴더: `android/` 안의 `ui/`

```
app/src/main/java/.../
├── core/        # D 담당 (SDK, 서버 통신, 주행 기록). 나는 고치지 않음
└── ui/          # 나
    ├── region/      # PLEOS-42
    ├── courses/     # PLEOS-43
    ├── confirm/     # PLEOS-44
    ├── driving/     # PLEOS-45
    ├── result/      # PLEOS-46
    └── theme/
```

- `ui/`는 `core/`의 인터페이스만 써요.
- `core/`가 아직 없으면 **가짜 데이터(Fake)**로 화면부터 만들고, 나중에 바꿔 끼워요.
- 화면에 쓰는 데이터 모양은 `shared/` JSON 규격(경로 PLEOS-16, 주행 기록 PLEOS-17)을 따라요. 규격은 B·C·D가 정해요. 내가 바꾸지 않아요.

---

## 5. 🌿 Git 규칙 (요약, 전체는 `CONTRIBUTING.md`)

- `main`에 직접 push 금지, PR로만
- 브랜치: `feature/PLEOS-번호-짧은설명` (예: `feature/PLEOS-42-region-input`)
- 커밋·PR 제목 맨 앞에 티켓 번호: `PLEOS-42 지역 입력 화면 레이아웃`
- 티켓 1개 = 브랜치 1개 = PR 1개, 되도록 하루 안에
- 머지는 Squash, 머지 후 Jira 티켓 "완료"
- 화면 PR에는 **스크린샷** 첨부
- `local.properties`, 키스토어, `.env`는 절대 커밋 금지

### 리뷰어로서 (내가 매일 할 일)

- 팀의 열린 PR을 매일 확인하고 리뷰·머지 여부 결정
- `shared/`를 바꾼 PR은 B·C·D 모두 확인했는지 봐요
- 다른 사람 폴더를 건드린 PR은 그 폴더 담당자 리뷰가 있는지 봐요

---

## 6. 📋 Jira 사용 규칙 (요약, 전체는 `CLAUDE.md`)

- 상태: 할 일 → 진행 중 → 완료
- 티켓 만들 때: 동사로 끝나는 제목, 상위 에픽 + 담당자 + 역할 라벨(role-A~E) 필수, 설명은 "목표 / 완료 기준 / 참고"
- 작업 시작하면 "진행 중", 끝나면 "완료" + 한두 줄 댓글
- 삭제하지 않고 "완료" + "취소: 이유" 댓글로 닫기

### 매일 정리 (PLEOS-57, 11/15까지)

"보드 정리해줘"라고 하면 Claude가 점검해요:
- 담당자 없는 티켓, 라벨·에픽 빠진 티켓
- 기한 지났는데 완료 아닌 티켓
- 2일 넘게 "진행 중"이거나 업데이트 없는 티켓
- PR은 끝났는데 열려 있는 티켓
- 중복 티켓

고칠 내용은 먼저 보여주고 확인받은 뒤 반영. 다른 사람 티켓은 댓글로 알리기. 끝나면 오늘 완료·진행 중·막힘을 3줄 요약.

### Claude에 Jira 연결 (한 번만)

```
claude mcp add --scope user --transport http atlassian https://mcp.atlassian.com/v1/mcp
```

`claude` 실행 → `/mcp` → atlassian → 로그인 → 팀 Jira 사이트 허용.
E 담당이 사이트 관리자라서, **E 담당이 먼저 연결해야** 팀원들도 연결할 수 있어요.

---

## 7. ✅ 지금 할 일 순서

### 오늘 (월)
- [ ] Jira 연결 (위 명령) → 팀원에게 "이제 연결해 보세요" 알리기
- [ ] GitHub Settings → Branches → `main`에 "Require a pull request before merging" 켜기
- [ ] 팀 단톡에 레포 링크, Jira 보드, 설명서 공유
- [ ] PLEOS-13 일정표 확정: 11/15까지 날짜별로 정하고 각 티켓에 기한 넣기
- [ ] 담당자 없는 티켓 4개 주인 정하기 (아래 8번)
- [ ] D 담당과 Android Studio 프로젝트(`android/`) 만들었는지 확인
- [ ] PLEOS-47 차량 UX 가이드 30분 훑기
- [ ] `feature/PLEOS-42-region-input` 브랜치 만들고 지역 입력 화면 시작 (가짜 데이터)
- [ ] 저녁: 첫 "보드 정리해줘"

### 화~
- PLEOS-15 Jira·GitHub 연동
- 화면 순서대로: 43 → 44 → 45 → 46
- 매일: PR 검토 + 보드 정리 (PLEOS-57)

---

## 8. ❓ 아직 안 정해진 것

| 항목 | 티켓 | 상태 |
|---|---|---|
| 발표·데모 시나리오 작성 | PLEOS-12 | 담당자 없음 |
| 데모 영상·발표 리허설 | PLEOS-56 | 담당자 없음 |
| SDK Coming Soon 상태 확인 | PLEOS-51 | 담당자 없음 |
| 공모전 규정: 외부 서버 통신 허용 여부 문의 | PLEOS-52 | 담당자 없음 |
| 친구 식별 방식 (닉네임 + 6자리 코드 제안) | | 확인 필요 |
| 에뮬레이터에서 연비 값이 나오는지 | | 확인 필요 (D) |
| 지역·코스 입력: 음성 vs 터치 | | 데모는 터치 추천 |
| 지도 표시 방법 (외부 지도 vs Canvas) | | 확인 필요 (D와 상의) |
| Android Studio 프로젝트 생성 | | D와 확인 |

---

## 9. 🔗 링크

- 레포: https://github.com/Nana2828/Pleos_application
- Jira 보드: 팀 Jira PLEOS 보드 (주소는 팀 안에서 공유)
- Jira 설명서 (팀 공유용): https://claude.ai/artifact/EAP5pabdT676ErFte2A1fP
- 시나리오: `docs/scenario-flow.md`
- 역할: `docs/team-roles.md`
- UI 시안: `docs/ui/`
