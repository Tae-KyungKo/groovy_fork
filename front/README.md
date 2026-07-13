# Groovy Frontend

스터디 그룹 매칭 서비스의 **프론트엔드 전용** 저장소입니다. 백엔드는 이 저장소에서 다루지 않으며, 아래 명시된 API 엔드포인트를 호출하는 화면만 최소 범위로 구현합니다.

## 범위 (Scope)

이 프로젝트는 **아래 API 목록에 대응하는 기능만** 구현합니다. 목록에 없는 기능(예: 프로필 이미지 업로드, 검색 자동완성, 관리자 화면 등)은 범위 밖입니다.

### 인증 / 회원

| 기능 | Method | Endpoint | 화면 |
| --- | --- | --- | --- |
| 회원가입 | POST | `/api/auth/signup` | `/signup` |
| 로그인 | POST | `/api/auth/login` | `/login` |
| 로그아웃 | POST | `/api/auth/logout` | 헤더 로그아웃 버튼 |
| 마이페이지 조회 | GET | `/api/users/me` | `/me` |

### 스터디

| 기능 | Method | Endpoint | 화면 |
| --- | --- | --- | --- |
| 스터디 그룹 생성 | POST | `/api/studies` | `/studies/new` |
| 스터디 목록 조회 | GET | `/api/studies` | `/studies` |
| 스터디 상세 조회 | GET | `/api/studies/{studyId}` | `/studies/:studyId` |
| 스터디 수정 | PUT | `/api/studies/{studyId}` | `/studies/:studyId/edit` |
| 스터디 삭제 | DELETE | `/api/studies/{studyId}` | 상세 화면 내 삭제 버튼 |
| 스터디 참여 신청 | POST | `/api/studies/{studyId}/applications` | 상세 화면 내 신청 버튼 |
| 참여 신청 취소 | DELETE | `/api/studies/{studyId}/applications` | 상세 화면 내 신청 취소 버튼 |
| 신청 목록 조회 | GET | `/api/studies/{studyId}/applications` | `/studies/:studyId/applications` (스터디장 전용) |
| 신청 승인/거절 | PATCH | `/api/studies/{studyId}/applications/{appId}` | 신청 목록 내 승인/거절 버튼 |

### 캘린더

| 기능 | Method | Endpoint | 화면 |
| --- | --- | --- | --- |
| 스터디/개인 일정 조회 | GET | `/api/calendars` | `/calendar` |
| 개인 일정 추가 | POST | `/api/calendars` | `/calendar` 내 일정 추가 모달 |

### 대기열 / 알림

| 기능 | Method | Endpoint | 화면 |
| --- | --- | --- | --- |
| 빈자리 대기 신청 | POST | `/api/studies/{studyId}/waiting` | 상세 화면 내 대기 신청 버튼 |
| 내 대기 순번 조회 | GET | `/api/studies/{studyId}/waiting/position` | 상세 화면 내 대기 순번 표시 |
| 대기 신청 수정/삭제 | DELETE | `/api/studies/{studyId}/waiting` | 상세 화면 내 대기 취소 버튼 |
| 실시간 알림 연결 | GET | `/api/notifications/stream` | 전역 알림 아이콘 (SSE) |

### 태그

| 기능 | Method | Endpoint | 화면 |
| --- | --- | --- | --- |
| 전체 태그 목록 조회 | GET | `/api/tags` | 스터디 생성/수정 폼, 필터 |
| 태그 기반 매칭도 조회 | GET | `/api/studies/match` | `/studies` 내 매칭도 정렬/표시 |

## 기술 스택

- **React + Vite** (TypeScript)
- **react-router-dom** — 페이지 라우팅
- **fetch 기반 API 클라이언트** (`src/api/`) — 별도 상태관리 라이브러리 없이 최소 구성. 서버 상태 캐싱이 필요해지면 `@tanstack/react-query` 도입 검토
- SSE(`EventSource`)로 실시간 알림 수신

## 폴더 구조 (제안)

```
front/
├── src/
│   ├── pages/           # 위 표의 화면 단위 (Login, Signup, StudyList, StudyDetail, ...)
│   ├── components/      # 페이지 간 공용 UI (Header, Modal, Toast 등)
│   ├── api/             # 엔드포인트별 fetch 함수 (auth.ts, studies.ts, calendars.ts, waiting.ts, tags.ts, notifications.ts)
│   ├── routes.tsx        # 라우트 정의
│   └── main.tsx
├── .env.example
└── README.md
```

## 실행 방법

```bash
npm install
cp .env.example .env   # VITE_API_BASE_URL 설정
npm run dev
```

`.env`

```
VITE_API_BASE_URL=http://localhost:8080
```

백엔드는 별도 저장소/팀원이 담당하며, 이 프론트엔드는 `VITE_API_BASE_URL`이 가리키는 서버를 호출하기만 합니다. 백엔드가 준비되지 않은 동안은 각 `src/api/*` 함수를 목업 응답으로 대체해 화면 작업을 진행합니다.

## 범위 밖 (Out of scope)

- 백엔드/DB/인프라 구현 및 배포
- 위 표에 없는 신규 기능 추가
- 인증 토큰 발급 로직 자체(로그인 API 호출 후 받은 토큰 저장/전달만 처리)
- 자동화 테스트, CI 파이프라인 구축 (필요 시 추후 별도 논의)
