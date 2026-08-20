# Groovy MSA 전환 — Phase 2: 서비스 경계 및 Contract 정의

> 상위 계획: [`Groovy_MSA_전환계획.md`](Groovy_MSA_전환계획.md) Phase 2
> 선행 문서: [`Groovy_MSA_Phase0_의존성분석.md`](Groovy_MSA_Phase0_의존성분석.md),
>            [`Groovy_MSA_도메인경계_재검토.md`](../Groovy_MSA_도메인경계_재검토.md),
>            [`Groovy_MSA_Phase1_ModularMonolith.md`](Groovy_MSA_Phase1_ModularMonolith.md)
> 목표: 어떤 서비스로 나눌지, 서비스끼리 뭘 주고받을지를 코드보다 먼저 문서로 확정한다.
> 이 Phase는 **문서 작업만** 한다 — 코드 변경 없음(Internal API 실제 구현은 Phase 8, Event 브로커 전환은
> Phase 9).

## 1. 서비스 경계 확정

| 서비스 | 담당 도메인 | 근거 |
|---|---|---|
| identity-service | User, 인증/인가(JWT) | Phase 0에서 확인된 최대 결합 허브. 독자적 Aggregate. |
| study-service | Study, Application, Waitlist, **StudyTag** | 정원 제어가 하나의 트랜잭션/락에 걸쳐 있어 물리적으로 분리 불가(도메인경계 재검토 §Study 참고). StudyTag는 Study가 소유하는 데이터라 함께 배치. |
| content-service | Memoir, MemoirComment, MemoirLike | 단일 Aggregate. |
| calendar-service | Calendar | 개인 일정이라는 독자적 존재 이유. Phase 1에서 이미 Study 엔티티 참조를 끊어둠. |
| notification-service | Notification | 나가는 의존성 0, 이벤트 전용 leaf. Phase 6에서 최우선 추출 대상. |

### Tag 배치 최종 결정 (도메인경계 재검토에서 이관된 안건)

- **StudyTag → study-service.** `StudyService.getMatchedStudies()`가 `StudyTag`와 SQL 레벨로 강하게
  조인되어 있어(태그 매칭 정렬·페이지네이션을 DB에서 수행) 분리 시 성능/복잡도 손실이 크다.
- **UserTag(선호 태그) → identity-service.** `/api/tags/me`는 유저 프로필의 일부이므로 User 소유로
  옮긴다.
- **Tag 마스터 목록(카테고리+이름)** 은 관리자가 가끔 추가하는 저빈도 변경 참조 데이터(Reference
  Data)다. study-service가 원본을 소유하고, identity-service는 읽기 전용 복제본을 갖는다. 동기화는
  Phase 9 이벤트 브로커 도입 시 `TagCreated`/`TagUpdated` 이벤트로 전파하거나, 그 전까지는 두
  서비스의 Flyway 시딩 데이터를 동일하게 유지하는 방식으로 임시 대응한다.
- **영향**: `TagController`(`GET /api/tags`, `GET/PUT /api/tags/me`)는 study-service와
  identity-service로 쪼개져야 한다. 실제 코드 분리는 Phase 3(Gradle Multi-Project) 이후에 진행한다.

### UserController 조합 책임 결정 (Phase 0/1에서 이관된 안건)

`GET /api/users/me/studies`, `GET /api/users/me/applications`는 User 도메인 URL이지만 실제로는
Study 도메인 데이터를 반환한다. API Gateway는 단순 라우팅(Strangler Fig)만 맡기고 응답 조합
(BFF) 기능은 두지 않기로 했으므로(Phase 5 Spring Cloud Gateway는 라우팅 전용으로 사용), **이
두 엔드포인트의 소유권을 study-service로 옮긴다.**

```
GET /api/users/me/studies       → GET /api/studies/mine            (study-service)
GET /api/users/me/applications  → GET /api/studies/mine/applications (study-service)
```

프론트엔드 호출 경로 변경이 필요하지만, Gateway 라우팅 규칙(`/api/studies/**` → study-service)
하나로 커버되어 별도 조합 로직이 필요 없어진다. 실제 엔드포인트 이전은 Phase 3 이후 코드 작업.

## 2. Internal API 명세 (동기 통신 — Phase 8에서 구현)

Phase 1에서 도메인 간 호출을 이미 "Service 공개 API 경유"로 정리해뒀기 때문에, 그 메서드
시그니처가 사실상 Internal API 설계 초안이다. 지금은 인프로세스 메서드 호출이고, Phase 8에서
동일한 의미의 HTTP 엔드포인트로 그대로 옮긴다.

### identity-service가 제공

| Internal API | 현재(Phase 1) 구현 | 호출하는 서비스 |
|---|---|---|
| `GET /internal/users/by-email/{email}` | `UserService.findByEmail(String)` | study, content, calendar, tag, notification |
| `GET /internal/users/{userId}` | `UserService.findById(Long)` | notification (알림 수신자 조회) |

### study-service가 제공

| Internal API | 현재(Phase 1) 구현 | 호출하는 서비스 |
|---|---|---|
| `GET /internal/studies/{studyId}` | `StudyService.getStudyEntity(Long)` | content, calendar |
| `GET /internal/studies?leaderId={userId}` | `StudyService.getStudiesLedBy(Long)` | content, calendar |
| `GET /internal/studies/{studyId}/membership/{userId}` | `ApplicationService.isApprovedMember(Long, Long)` | content, calendar |
| `GET /internal/studies/{studyId}/members` | `ApplicationService.getApprovedMemberUserIds(Long)` | calendar (일정 변경 알림 대상 조회) |
| `GET /internal/users/{userId}/studies?status=APPROVED` | `ApplicationService.getApprovedStudies(Long)` | content, calendar |

> 계획서 원안의 `GET /internal/studies/{studyId}/membership/{userId}` 예시가 Phase 1에서 실제로
> 구현한 `ApplicationService.isApprovedMember(studyId, userId)`와 정확히 일치한다 — Phase 0/1의
> 결합도 분석이 계획서의 사전 설계와 맞아떨어졌다는 뜻이므로 별도 재설계 없이 그대로 채택.

### 호출 방향 요약

```
content-service    ──→ identity-service (유저 조회)
content-service    ──→ study-service (스터디 조회, 멤버십 확인, 내 스터디 목록)
calendar-service    ──→ identity-service (유저 조회)
calendar-service    ──→ study-service (스터디 조회, 멤버십 확인, 승인 멤버 목록, 내 스터디 목록)
study-service       ──→ identity-service (방장/신청자 조회)
notification-service ──→ identity-service (알림 수신자 조회)
tag(study 내부)      ──→ identity-service (선호 태그 소유자 조회)
```

identity-service는 아무도 호출하지 않는(들어오기만 하는) 서비스가 아니라, 반대로 **모든 서비스가
호출하는 허브**다 — Phase 0에서 확인한 결합도 그래프와 동일한 모양이 서비스 경계에도 그대로
남는다. Phase 11(Resilience)에서 identity-service 장애가 전체로 번지지 않도록 Circuit
Breaker/Fallback을 가장 먼저 적용해야 할 대상으로 지금 표시해둔다.

## 3. Event Contract (비동기 통신 — Phase 9에서 브로커로 전환)

### 3-1. 이미 구현되어 있는 이벤트 (Spring `ApplicationEvent`, Notification 전용 소비)

Phase 0에서 확인한 7종. 지금은 프로세스 내부 이벤트지만 Phase 9에서 Kafka/RabbitMQ 메시지로
그대로 옮길 수 있도록 이름과 필드를 그대로 표준 이벤트명으로 채택한다.

| 이벤트 | 발행 | 현재 소비자 | 스키마(현재 record 필드) |
|---|---|---|---|
| `STUDY_APPLICATION_SUBMITTED` | study-service | notification-service | recipientUserId, applicantName, studyId, studyTitle |
| `STUDY_APPLICATION_APPROVED` / `STUDY_APPLICATION_REJECTED` | study-service | notification-service | recipientUserId, approved, studyId, studyTitle |
| `STUDY_SEAT_AVAILABLE` | study-service | notification-service | recipientUserIds[], studyId, studyTitle |
| `STUDY_LEVEL_UP` | study-service | notification-service | recipientUserIds[], studyId, studyTitle, newLevel |
| `STUDY_SCHEDULE_CHANGED` | calendar-service | notification-service | recipientUserIds[], studyId, studyTitle, scheduleTitle, changeType(CREATED/UPDATED/DELETED) |
| `MEMOIR_COMMENT_ADDED` | content-service | notification-service | recipientUserId, commenterName, memoirId, memoirTitle |
| `MEMOIR_LIKE_ADDED` | content-service | notification-service | recipientUserId, likerName, memoirId, memoirTitle |

### 3-2. 계획서가 제안했으나 아직 구현되지 않은 이벤트 — Phase 2에서 초안만 확정

이 이벤트들은 지금은 소비자가 없다(발행 코드 자체가 없음). Notification 외의 미래 소비자를
염두에 두고 스키마만 먼저 못박아 둔다 — 실제 발행 코드 작성은 그 이벤트가 처음 필요해지는
시점(예: Phase 9, 또는 그 이전에 다른 서비스가 필요로 할 때)에 한다.

| 이벤트 | 발행(예정) | 잠재 소비자 | 필요한 이유 |
|---|---|---|---|
| `USER_DELETED` | identity-service | study, content, calendar, notification | 지금은 회원 탈퇴 기능 자체가 없다. 생기는 순간 4개 서비스에 흩어진 `user_id` FK를 정리해야 하므로, 기능 구현과 이벤트 설계를 반드시 같이 해야 한다. |
| `STUDY_CREATED` | study-service | (미정 — 향후 검색/추천 서비스) | 지금 당장 소비자는 없지만 Phase 2 계획서가 예시로 명시. 발행 자체는 비용이 낮아 Phase 9에서 다른 이벤트와 함께 얹는다. |
| `STUDY_MEMBER_LEFT` | study-service | content, calendar | `ApplicationService.leave()`가 지금은 탈퇴를 알림 이벤트 없이 조용히 처리한다(대기열 오픈만 알림). 탈퇴 시 content-service(그 사람이 쓴 회고록의 스터디 접근권)나 calendar-service(그 사람의 스터디 일정 접근권)가 반응해야 할 필요가 생기면 그때 추가한다. |

`STUDY_APPLICATION_APPROVED`가 사실상 계획서의 `StudyMemberJoined`, `MEMOIR_COMMENT_ADDED`가
`MemoirCommentCreated`, `STUDY_SEAT_AVAILABLE`이 `StudySeatAvailable`과 같은 개념이라 계획서의
이벤트 목록 중 이 3개는 별도 이벤트를 새로 만들지 않고 기존 이벤트로 대체했다. `MemoirCreated`는
지금은 소비자가 없어 3-2 표에 넣지 않았다 — 필요해지는 시점에 추가.

### 3-3. 공통 이벤트 봉투(Envelope) 스키마

```json
{
  "eventId": "uuid",
  "eventType": "STUDY_APPLICATION_APPROVED",
  "occurredAt": "2026-08-17T10:00:00+09:00",
  "schemaVersion": 1,
  "payload": {
    "studyId": 123,
    "recipientUserId": 456
  }
}
```

- `schemaVersion`은 계획서 Phase 2가 요구한 "버전 필드 포함" 원칙 반영. payload 필드는 전부
  optional로 설계해(계획서 원칙) 컨슈머가 필요한 필드만 읽고 무시할 수 있게 한다.
- 지금 코드의 Java `record` 이벤트들(`ApplicationSubmittedEvent` 등)은 필드가 전부 non-null
  필수값으로 설계돼 있다 — Phase 9에서 실제 브로커 메시지로 옮길 때 이 스키마 원칙에 맞춰
  `payload`를 감싸는 봉투 구조로 한 번 더 감싸야 한다(지금 미리 바꾸지 않는 이유: 아직 프로세스
  내부 이벤트라 강타입 record가 오히려 더 안전하고, 봉투 구조는 실제로 네트워크를 건널 때
  의미가 생긴다).

## 4. 완료 기준 체크

- [x] 서비스 경계 확정 (5개 서비스 + Tag 소유권 분할 + Tag 마스터 데이터 동기화 방식)
- [x] Internal API 명세 문서화 (§2 — 누가 누구를 호출하는지 표로 정리, 전부 Phase 1의 실제 공개
      메서드에서 도출)
- [x] Event 목록 및 스키마 초안 확정 (§3 — 기존 7종 + 신규 3종 후보 + 공통 봉투 스키마)

## 5. 다음 단계로 넘길 것

- **Tag 컨트롤러 분리**: `/api/tags`(마스터 조회)는 study-service, `/api/tags/me`(선호 태그)는
  identity-service로 이전 — Phase 3(Gradle Multi-Project) 이후 실제 모듈 분리 시 처리.
- **UserController 두 엔드포인트 이전**: `/api/users/me/studies`, `/me/applications` →
  `/api/studies/mine`, `/api/studies/mine/applications`로 이동. 프론트엔드 API 클라이언트
  수정이 함께 필요.
- **USER_DELETED 설계는 회원 탈퇴 기능 구현과 묶어서**: 지금 이 기능 자체가 없으므로 이벤트만
  먼저 만들지 않는다.
