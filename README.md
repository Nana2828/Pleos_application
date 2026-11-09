# Pleos_application

Pleos 공모전 팀 프로젝트. 차 인포테인먼트 앱으로 코스를 골라 달리고, 결과를 폰에서 QR로 확인하고 친구와 비교해요.

## 🏗️ 전체 구조

```
인포 앱 (차, android/)  ⇄  FastAPI 서버 (server/)  ⇄  DB (data/)
                                  ⇅
                     폰 웹 (web/, QR로 접속)

가상주행 (simulator/) → 실제 SDK와 같은 JSON → 서버
공유 JSON 규격 (shared/) ← 모두가 이 형식을 따름
```

## 📁 폴더

| 폴더 | 내용 | 기술 | 담당 |
|---|---|---|---|
| `android/` | 인포 앱 | Kotlin, Jetpack Compose, MVVM, Retrofit, Pleos SDK | D 재원, E 나연 |
| `server/` | API 서버 | Python, FastAPI, Pydantic, SQLAlchemy | C 원준 |
| `simulator/` | 가상주행 | Python | C 원준 |
| `data/` | 경로 프리셋, DB | GeoJSON, SQLite | B 수현 |
| `web/` | 폰 웹 | HTML, CSS, JavaScript | A 은빈 |
| `shared/` | 공유 JSON 규격 | JSON Schema | B·C·D |
| `docs/` | 기획, 시나리오, 역할 | Markdown | A·E |

## 🔗 링크

- Jira 보드: https://choenayeon.atlassian.net/jira/software/projects/PLEOS/boards
- 규칙: [CONTRIBUTING.md](CONTRIBUTING.md)
- 역할: [docs/team-roles.md](docs/team-roles.md)
- 시나리오: [docs/scenario-flow.md](docs/scenario-flow.md)
