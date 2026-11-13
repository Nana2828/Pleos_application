# 🖥️ server/ — FastAPI 서버

담당: C 담당

- 기술: Python 3, FastAPI, Pydantic, Uvicorn, SQLAlchemy
- API (시나리오 문서 6번): `GET /courses?region=`, `GET /courses/{course_id}`
- 데이터 처리 원칙: 서버는 지역명·코스 이름 같은 텍스트만 받고 코스 JSON만 응답해요. 현재 위치, 주행 기록, 음성 원문은 받지 않고, 주행 기록 저장·랭킹 API(`POST /drive`, `GET /drive/{id}`, `GET /ranking`)는 만들지 않아요.
- 비밀 값은 `.env`에 두고, 키 이름만 `.env.example`에 올려요.
