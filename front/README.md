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

## Task Spec: CalendarPage 실제 달력 UI

`src/pages/CalendarPage.tsx` 현재는 폼 + 목록(list)뿐. 월간 그리드 형태 실제 달력으로 개편.

### 1. 뷰 구조 (월간 그리드만)

- 7열(일~토) x 6행 고정 그리드. 해당 월 1일이 속한 주부터 마지막 날이 속한 주까지 채움.
- 이전 달/다음 달 날짜로 채워지는 칸은 흐리게(다른 달 표시) 처리.
- 상단 헤더: "YYYY년 M월" + 이전달(`<`)/다음달(`>`)/오늘 이동 버튼.
- 상태: 현재 보고 있는 연/월 (`currentMonth`).

### 2. 일정 표시

- `events`를 `date` 기준 그룹핑해서 해당 날짜 칸 안에 렌더링.
- 한 칸에 여러 일정 가능(세로 나열, 넘치면 "+N개" 축약 고려).
- PERSONAL vs STUDY는 색상/뱃지로 구분 (좌측 바 색상 또는 배경색 다르게, STUDY는 `studyTitle` 표시).
- 오늘 날짜 칸은 강조 표시(테두리/배경).

### 3. 일정 추가 (칸 클릭 → 모달)

- 날짜 칸 클릭 시 모달 오픈, 클릭한 날짜를 `date` 필드 기본값으로 세팅.
- 모달 안에 기존 폼(title, date) 재사용. 제출 시 `addPersonalEvent` 호출 후 목록 갱신 + 모달 닫기.
- 수정/삭제는 이번 스펙 범위 아님 (API에 update/delete 없음, 추가만 구현).

### 4. 데이터

- 기존 `listCalendarEvents` / `addPersonalEvent` 그대로 사용, 백엔드 변경 없음.
- API가 전체 목록만 반환하므로 월 이동 시 클라이언트에서 해당 월로 필터링.

### 5. 접근성/반응형

- 날짜 칸은 버튼 또는 `role="button"` + 키보드 접근 가능하게.
- 좁은 화면에서 그리드 가로 스크롤 또는 축소 레이아웃 고려.

### 미해결/확인 필요

- 모달 컴포넌트 분리(`components/`) 여부.
- 하루 표시 가능 일정 개수 상한 및 "+N개" 문구 처리.

## 범위 밖 (Out of scope)

- 백엔드/DB/인프라 구현 및 배포
- 위 표에 없는 신규 기능 추가
- 인증 토큰 발급 로직 자체(로그인 API 호출 후 받은 토큰 저장/전달만 처리)
- 자동화 테스트, CI 파이프라인 구축 (필요 시 추후 별도 논의)
