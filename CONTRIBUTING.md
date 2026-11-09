# 🤝 GitHub 규칙

모든 분야가 **같은 규칙**을 써요. 분야는 폴더로만 나눠요.

## 📁 폴더와 담당

| 폴더 | 내용 | 담당 |
|---|---|---|
| `android/` | 인포 앱 (Kotlin, Compose) | D 재원(`core/`), E 나연(`ui/`) |
| `server/` | FastAPI 서버 | C 원준 |
| `simulator/` | 가상주행 스크립트 | C 원준 |
| `data/` | 경로 프리셋, DB 스키마 | B 수현 |
| `web/` | 폰 웹 (결과, 친구 비교, QR) | A 은빈 |
| `shared/` | 공유 JSON 규격 | B·C·D 함께 |
| `docs/` | 기획·시나리오·역할 문서 | A·E |

> 내 폴더 밖의 파일을 고쳐야 하면, PR에 그 폴더 담당자를 리뷰어로 넣어요.

## 🌿 브랜치

- `main`: 언제나 실행되는 상태. **직접 push 금지**, PR로만 합쳐요.
- 작업 브랜치: `feature/PLEOS-번호-짧은설명`
  - 예: `feature/PLEOS-42-region-input`
  - 버그는 `fix/PLEOS-번호-설명`, 설정·문서는 `chore/PLEOS-번호-설명`
- 브랜치 하나 = Jira 티켓 하나.

```bash
git checkout main
git pull
git checkout -b feature/PLEOS-42-region-input
```

## ✍️ 커밋

- 맨 앞에 티켓 번호: `PLEOS-42 지역 입력 화면 레이아웃`
- 작게, 자주. 한 커밋에 한 가지 변경.

## 🔀 Pull Request

1. 티켓 1개에 PR 1개, 되도록 **하루 안에** 올려요.
2. PR 제목: `PLEOS-42 지역 입력 화면`
3. 템플릿 칸을 채워요 (무엇을, 어떻게 확인했는지, 스크린샷).
4. 리뷰어
   - 기본: **E 나연** (매일 검토)
   - `shared/` JSON을 바꾸면: **B·C·D 모두** 확인
   - 다른 사람 폴더를 건드리면: 그 폴더 담당자
5. 머지는 **Squash and merge**. 머지한 뒤 Jira 티켓을 "완료"로 옮겨요.
6. 머지된 브랜치는 지워요.

## ⚠️ 올리면 안 되는 것

- `.env`, API 키, 비밀번호 → `.env.example`에 키 이름만 적어서 올려요
- Android `local.properties`, 키스토어(`*.jks`, `*.keystore`)
- `build/`, `node_modules/`, `.venv/` 같은 빌드 결과물

## 🧩 공유 JSON 규격 (`shared/`)

- 경로 JSON과 주행 기록 JSON은 **먼저 확정하고** 개발을 시작해요.
- 실제 SDK 값과 가상주행 값은 **같은 형식**을 써요.
- 규격을 바꿀 땐 PR 설명에 "무엇이 바뀌었는지"를 꼭 적어요. 다른 분야 코드가 깨질 수 있어요.
