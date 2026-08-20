# Groovy MSA 전환 계획 (Docker Compose 환경)

> 범위: Docker Compose 기반 MSA 전환 (K8s 도입은 별도 팀원 담당)
> 전제: DDD, MSA 개념, DB per Service, 동기/비동기 통신, Eventual Consistency, Saga, Outbox/Inbox/Idempotency 학습 완료

---

## 0. 원칙 재확인

전환 전체를 관통하는 원칙 3가지를 먼저 박아둡니다. 이후 모든 Phase에서 이 원칙이 흔들리면 "Distributed Monolith"가 됩니다.

1. **한 번에 다 바꾸지 않는다 (Strangler Fig)**: 레거시 모놀리스를 곁에 두고, 트래픽을 조금씩 새 서비스로 옮긴다.
2. **모듈 경계 → 서비스 경계 순서로 간다**: 코드 레벨에서 경계가 안 지켜지면, 서비스로 쪼개도 결국 강결합된다.
3. **DB 분리는 마지막이 아니라 중간 지점**: 서비스는 나눴는데 DB는 공유 중인 과도기 상태를 짧게, 명시적으로 거친다.

---

## Phase 0. 의존성 전수 조사 (착수 전 필수)

**목표**: 코드를 건드리기 전에 현재 결합도를 눈에 보이게 만든다.

### 할 일
- 각 도메인(Study, User, Memoir, Calendar, Notification, Tag)별로 아래 표를 작성

```
Domain: StudyApplicationService
├─ 사용 Entity: Application, Study, Waitlist
├─ 사용 Repository: StudyRepository, ApplicationRepository, UserRepository(cross), WaitlistRepository
├─ 다른 Domain Service 호출: NotificationService.create(...)
├─ Transaction 범위: approve() 안에서 Study+Application+Notification 전부
├─ Redis 사용: 없음 / 있음
├─ 제공 API: POST /api/studies/{id}/applications/{appId}
```

- 이 표를 모두 모아서 **서비스 간 coupling graph**를 그림으로 그려본다 (누가 누구를 직접 참조하는지)

### 완료 기준
- [x] 6개 도메인의 의존성 표 작성 완료
- [x] "cross-domain repository 직접 접근" 목록이 눈에 보이는 문서로 정리됨 (예: Study → UserRepository, Memoir → StudyRepository 등)

> 산출물: [`Groovy_MSA_Phase0_의존성분석.md`](Groovy_MSA_Phase0_의존성분석.md)

---

## Phase 1. Modular Monolith로 전환 (코드 레벨 경계 확립)

**목표**: 서비스로 쪼개기 전에, 패키지 레벨에서 먼저 "남의 Repository/Entity를 직접 못 건드리게" 만든다. **이 단계를 건너뛰고 바로 서비스를 분리하면 십중팔구 Distributed Monolith가 됩니다.**

### 할 일
- 패키지 구조를 도메인별로 재편

```
domain/
 ├─ study/       (api, application, domain, infrastructure)
 ├─ user/
 ├─ memoir/
 ├─ calendar/
 ├─ notification/
 └─ tag/
```

- 금지 규칙 명시 (코드 리뷰 체크리스트화)

```
Study → UserRepository 직접 접근 금지
Study → NotificationRepository 접근 금지
Memoir → StudyRepository 접근 금지
Calendar → Study Entity 참조 금지
```

- 모듈 간 통신이 필요하면 반드시 **공개 API(인터페이스) 또는 Application Event**를 거치도록 리팩터링
- **Spring Modulith** 도입 검토: 모듈 경계를 어노테이션으로 강제하고, 위반 시 테스트가 실패하게 만들 수 있음 (`ApplicationModules.of(...).verify()`)

### 완료 기준
- [x] cross-domain repository/entity 직접 참조가 0건 (ArchUnit 규칙 통과)
- [x] 도메인 간 통신이 전부 Application Event 또는 명시적 인터페이스를 거침

> 산출물: [`Groovy_MSA_Phase1_ModularMonolith.md`](Groovy_MSA_Phase1_ModularMonolith.md)

> **왜 이 단계가 먼저인가**: 지금까지 배운 "DB per Service"는 물리적 DB 분리보다 먼저 "코드가 남의 데이터에 직접 접근하지 않는다"는 습관을 들이는 게 우선입니다. 코드에서부터 안 되면 서비스로 쪼개도 API 너머로 여전히 강결합됩니다.

---

## Phase 2. 서비스 경계 및 Contract 정의

**목표**: 어떤 서비스로 나눌지, 서비스끼리 뭘 주고받을지를 코드보다 먼저 문서로 확정한다.

### 서비스 경계 (제안)

| 서비스 | 담당 도메인 |
|---|---|
| identity-service | User, 인증/인가 |
| study-service | Study, Application, Waitlist |
| content-service | Memoir, Comment, Like |
| calendar-service | Calendar |
| notification-service | Notification |

Tag는 규모가 작으므로 초기에는 study-service 내부에 붙여두고, 필요해지면 나중에 분리해도 무방합니다.

> **재검토 (Phase 1 착수 전)**: `StudyTag`는 Study 소유, `UserTag`(선호 태그)는 User 소유로 실제로는
> 소유권이 갈린다. "Tag를 study-service에 흡수"하면 identity-service가 선호 태그 조회를 위해
> study-service를 호출해야 하는 역전이 생기므로, 최종 배치는 이 Phase에서 확정하지 않고 Tag 마스터
> 데이터(참조 데이터)를 어떻게 공유할지와 함께 결정한다. 상세: [`Groovy_MSA_도메인경계_재검토.md`](../Groovy_MSA_도메인경계_재검토.md)

### HTTP Contract (동기 통신, Phase 7에서 사용)

```
GET /internal/users/{userId}
GET /internal/studies/{studyId}/membership/{userId}
```

### Event Contract (비동기 통신, Phase 9에서 사용)

```
StudyCreated
StudyApplicationApproved
StudyMemberJoined
StudyMemberLeft
StudySeatAvailable
MemoirCreated
MemoirCommentCreated
UserDeleted
```

```json
{
  "eventId": "uuid",
  "eventType": "STUDY_APPLICATION_APPROVED",
  "occurredAt": "2026-08-17T10:00:00Z",
  "studyId": 123,
  "userId": 456
}
```

### 완료 기준
- [x] 서비스 경계 확정 및 팀 합의
- [x] Internal API 명세 문서화 (누가 누구를 호출하는지)
- [x] Event 목록 및 스키마 초안 확정 (3단계에서 배운 Event Schema 원칙 적용: 버전 필드 포함, optional 필드 위주 설계)

> 산출물: [`Groovy_MSA_Phase2_서비스경계와Contract.md`](Groovy_MSA_Phase2_서비스경계와Contract.md)

---

## Phase 3. Gradle Multi-Project 구조 전환

**목표**: 모노레포는 유지하되, 서비스별로 독립 빌드/실행이 가능한 구조로 전환.

```
Groovy/
├─ backend/
│  ├─ services/
│  │  ├─ api-gateway/
│  │  ├─ identity-service/
│  │  ├─ study-service/
│  │  ├─ content-service/
│  │  ├─ calendar-service/
│  │  └─ notification-service/
│  ├─ libs/
│  │  ├─ event-contract/     ← Phase 2에서 정의한 Event 스키마 (DTO만)
│  │  └─ observability/      ← 공통 로깅/메트릭 설정
│  ├─ settings.gradle
│  └─ build.gradle
├─ front/
├─ docker-compose.yml
└─ monitoring/
```

각 서비스는 독립적인 `@SpringBootApplication`, `application.yml`, `build.gradle`, `Dockerfile`, Flyway migration, 테스트를 가집니다. IntelliJ는 루트 하나로 열면 됩니다.

### `common` 모듈 경고

가장 흔한 실수: `common` 모듈에 Entity/Repository/DTO를 다 몰아넣고 모든 서비스가 `implementation project(":common")` 하는 것. 이러면 사실상 다시 하나로 묶입니다.

| 공유해도 되는 것 | 공유하면 위험한 것 |
|---|---|
| observability, logging 설정 | JPA Entity |
| event envelope(공통 이벤트 봉투 형식) | Repository |
| security utility | Business Service |
| test fixtures | Domain Model, 서비스별 DTO 전체 |

### 완료 기준
- [x] `settings.gradle`에 서비스별 모듈 등록 완료
- [x] 각 서비스가 독립적으로 `./gradlew :services:notification-service:bootRun` 가능
- [x] `libs/event-contract`에 순수 DTO만 존재 (비즈니스 로직 없음)

> 산출물: [`Groovy_MSA_Phase3_GradleMultiProject.md`](Groovy_MSA_Phase3_GradleMultiProject.md)

---

## Phase 4. Docker Compose 뼈대 구축

**목표**: 서비스를 실제로 코드에서 분리하기 전에, Compose 환경에서 여러 컨테이너가 통신할 수 있는 기반부터 만든다.

```yaml
# docker-compose.yml (뼈대 예시)
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: groovy_db   # Phase 6까지는 단일 DB 유지
    networks: [groovy-net]

  redis:
    image: redis:7
    networks: [groovy-net]

  api-gateway:
    build: ./backend/services/api-gateway
    ports: ["8080:8080"]
    networks: [groovy-net]

  legacy-monolith:
    build: ./groovy   # 기존 모놀리스, 당분간 유지
    networks: [groovy-net]

  notification-service:
    build: ./backend/services/notification-service
    networks: [groovy-net]

networks:
  groovy-net:
    driver: bridge
```

Compose 내부에서는 서비스명(`notification-service`, `mysql` 등)이 곧 DNS 호스트명이 되므로, 서비스 간 통신 시 `http://notification-service:8081` 형태로 접근합니다. 이게 Compose 환경의 "서비스 디스커버리"입니다 (K8s의 Service Discovery보다 훨씬 단순함).

### 완료 기준
- [x] `docker-compose up` 시 레거시 모놀리스 + 신규 빈 서비스 컨테이너가 동시에 뜸
- [x] 컨테이너 간 이름 기반 통신 확인 (`docker exec`로 curl 테스트)

> 산출물: [`Groovy_MSA_Phase4_DockerComposeSkeleton.md`](Groovy_MSA_Phase4_DockerComposeSkeleton.md)

---

## Phase 5. API Gateway 우선 도입 (Strangler Fig 패턴)

**목표**: 클라이언트가 레거시/신규 서비스 구분 없이 하나의 진입점만 보게 한다. 이후 서비스를 하나씩 추출해도 프론트는 코드 변경이 필요 없다.

### 단계별 전환

```
1단계: Frontend → Gateway → Legacy Monolith (전부)

2단계: Frontend → Gateway ─┬─ /notifications/** → notification-service
                            └─ 나머지 → Legacy Monolith

3단계: Frontend → Gateway ─┬─ /auth/**          → identity-service
                            ├─ /notifications/** → notification-service
                            ├─ /memoirs/**       → content-service
                            └─ 나머지            → Legacy Monolith

최종: Legacy Monolith 완전 제거
```

Spring Cloud Gateway 사용 권장. JWT 검증(2단계에서 배운 Gateway 역할)을 여기서부터 처리하기 시작합니다.

### 완료 기준
- [x] Gateway가 떠 있고, 모든 요청이 일단 레거시 모놀리스로 라우팅됨 (기능 변화 없음, 구조만 추가)
- [ ] nginx는 Gateway 앞단의 SSL 종료/정적 파일 서빙 역할로 재정의 — 보류(실제 배포 전환 시점에 처리, 근거는 산출물 문서 §5 참고)

> 산출물: [`Groovy_MSA_Phase5_ApiGateway.md`](Groovy_MSA_Phase5_ApiGateway.md)

---

## Phase 6. 첫 번째 서비스 추출: Notification Service

**목표**: 가장 이벤트 기반 성격이 강하고 리스크가 낮은 도메인부터 실전 검증한다.

Notification은 이미 SSE + Redis Pub/Sub 구조라 이벤트 기반 전환에 가장 적합합니다.

### Before
```java
studyService.approve();
notificationService.create(...);   // 직접 호출, 강결합
```

### After
```
Study(Legacy) → StudyApplicationApproved 이벤트 발행
                        ↓
                 Message Broker (Phase 9에서 정식 도입)
                        ↓
              Notification Service → DB 저장 → SSE 전송
```

Phase 9(메시지 브로커)가 아직 없다면, 초기에는 **동기 REST 호출**로 먼저 분리하고(Phase 7), 이후 Phase 9에서 비동기로 교체하는 2단계 접근도 가능합니다. 처음부터 완벽한 비동기 구조를 노리기보다, "일단 프로세스를 분리한다"를 먼저 검증하는 게 리스크가 낮습니다.

### 완료 기준
- [x] Notification 관련 테이블이 notification-service 코드베이스로 이동
- [x] Legacy Monolith에서 Notification 관련 코드/테이블 참조 제거
- [x] Gateway에서 `/notifications/**`가 신규 서비스로 라우팅됨
- [x] 기존 기능(SSE 알림) 동작 회귀 없음 확인

> 산출물: [`Groovy_MSA_Phase6_NotificationExtraction.md`](Groovy_MSA_Phase6_NotificationExtraction.md)

---

## Phase 7. Database per Service (논리적 분리부터)

**목표**: 처음부터 MySQL 인스턴스를 5개 띄우지 않는다. 하나의 MySQL 컨테이너 안에서 **스키마 소유권**부터 분리한다.

```
MySQL (컨테이너 1개)
 ├─ identity_db
 ├─ study_db
 ├─ content_db
 ├─ calendar_db
 └─ notification_db
```

### 원칙
- `study-service`는 오직 `study_db`에만 접근 가능 (계정/권한 레벨로 강제)
- 기존 FK(`memoirs.study_id → studies.id`)는 서비스 경계를 넘는 순간 **DB FK가 아니라 "Service-level reference"**로 취급 (그냥 BIGINT 컬럼, DB 레벨 제약 없음)
- 여기서부터 정합성 문제(Eventual Consistency)가 실제로 발생하기 시작함 — 4단계에서 배운 개념이 실전에 등장하는 지점

### 완료 기준
- [x] 서비스별 DB 계정 생성, 권한이 자기 스키마로 제한됨 (notification-service 기준)
- [x] 코드에서 cross-schema JOIN이 물리적으로 실행 불가능함 확인 (권한 에러 발생 테스트)
- [x] FK 제약 제거 및 애플리케이션 레벨 검증으로 대체

> 산출물: [`Groovy_MSA_Phase7_DatabasePerService.md`](Groovy_MSA_Phase7_DatabasePerService.md)
> 범위: 실제로 추출된 notification-service만 물리 분리. User/Study/Memoir/Calendar는 각 도메인의
> 서비스 추출 시점에 함께 진행(근거는 산출물 문서 §1).

---

## Phase 8. 서비스 간 동기 통신 구축

**목표**: "즉시 응답이 필요한" 조회성 통신부터 REST로 구축.

예: content-service가 "이 사용자가 이 Study에 속해 있는가"를 즉시 확인해야 하는 경우

```
content-service --HTTP GET--> study-service (/internal/studies/{id}/membership/{userId})
```

Spring 최신 권장 스택:
- 동기: `RestClient`
- 비동기/논블로킹: `WebClient`
- 선언형: HTTP Service Client

OpenFeign은 여전히 동작하지만 Spring 공식 문서가 feature-complete로 규정하고 신규 프로젝트에는 HTTP Service Client 이동을 권장하므로, 신규로는 굳이 Feign을 넣지 않는 걸 권장합니다.

### 완료 기준
- [x] Phase 2에서 정의한 Internal API 1~2개가 실제로 서비스 간 호출됨
- [x] Timeout 설정 존재 (기본값 방치 금지 — Phase 11 Resilience의 최소 전제조건)

> 산출물: [`Groovy_MSA_Phase8_SynchronousCommunication.md`](Groovy_MSA_Phase8_SynchronousCommunication.md)

---

## Phase 9. 비동기 메시지 브로커 + Transactional Outbox

**목표**: "지금 당장 응답 불필요한" 이벤트 흐름을 정식 메시지 브로커로 전환.

### 브로커 선택
Redis Pub/Sub은 SSE 인스턴스 간 브로드캐스트용으로는 유지하되, **비즈니스 이벤트의 durability(유실 방지)까지 Redis Pub/Sub에 맡기지 않습니다.** Kafka 또는 RabbitMQ 중 선택 (3단계에서 정리한 기준: 대용량 스트리밍/재처리 필요하면 Kafka, 단순 작업 큐면 RabbitMQ).

```
study-service → StudyApplicationApproved → Kafka ─┬→ notification-service
                                                     └→ (미래) recommendation-service
```

### 필수로 함께 도입해야 하는 것 (6단계 학습 내용 그대로 적용)

```java
@Transactional
public void approve() {
    application.approve();
    outboxRepository.save(new OutboxEvent("StudyApplicationApproved", payload));
    // DB 저장 + Outbox 기록이 같은 트랜잭션
}
```

- **Transactional Outbox**: DB 커밋과 이벤트 발행의 원자성 보장
- **Outbox Publisher(Relay)**: Outbox 테이블을 폴링하거나 CDC(Debezium 등)로 감시해서 실제 Kafka 발행
- **At-least-once 전제 인정**: Kafka의 Exactly-Once는 Kafka 내부(read-process-write)에만 적용되고, DB+Kafka를 아우르는 진짜 Exactly-once가 아님을 인지
- **Inbox + Idempotency**: 수신 측(notification-service)에서 event_id 기반 중복 제거

### 완료 기준
- [x] Outbox 테이블 + Relay 프로세스 동작
- [x] 강제로 Kafka 컨테이너를 잠깐 내렸다 올려도 이벤트 유실 없음 확인 (재시도 검증)
- [x] 동일 이벤트를 의도적으로 2번 보내도 Notification이 중복 생성 안 됨 (Idempotency 검증)

> 산출물: [`Groovy_MSA_Phase9_MessageBrokerOutbox.md`](Groovy_MSA_Phase9_MessageBrokerOutbox.md)
> 브로커: Kafka(사용자 지정)

---

## Phase 10. 인증 구조 재설계

**목표**: 모든 서비스가 같은 HMAC secret을 공유하는 방식에서 벗어난다.

```
identity-service (Private Key로 JWT 서명)
        ↓
     Client
        ↓
     Gateway
        ↓
 각 Resource Server (Public Key/JWKS로 검증만 수행)
```

공부/적용 순서: JWT 구조 → Issuer/Audience/Scope → JWKS 기반 검증 → Key Rotation. 서비스 간 호출(Phase 8의 내부 API)에는 Service-to-Service 인증(내부 전용 토큰 또는 mTLS)도 검토합니다.

### 완료 기준
- [x] Gateway 또는 각 서비스가 HMAC 공유 secret이 아닌 JWKS 기반으로 검증
- [x] 서비스 간 내부 API 호출에 별도 인증 메커니즘 존재 (누구나 호출 가능한 상태 아님)

> 산출물: [`Groovy_MSA_Phase10_AuthRedesign.md`](Groovy_MSA_Phase10_AuthRedesign.md)

---

## Phase 11. Resilience 적용

**목표**: 네트워크가 낀 순간부터 당연시했던 것들이 더 이상 당연하지 않다는 걸 코드로 반영.

```
Timeout → Retry(Exponential Backoff + Jitter) → Circuit Breaker → Bulkhead → Fallback
```

Spring Cloud CircuitBreaker(Resilience4j 기반) 적용. 특히 주의할 점: Timeout 없이 Retry만 걸면 Retry Storm(서버 부하 증가 → 더 많은 Timeout → 더 많은 Retry)이 발생할 수 있으므로, Retry는 반드시 Timeout + Backoff와 세트로 적용합니다.

### 완료 기준
- [x] Phase 8에서 만든 동기 호출에 Timeout, Retry, Circuit Breaker 적용
- [x] 의도적으로 study-service를 죽인 상태에서 content-service가 전체 장애로 번지지 않고 Fallback 동작 확인

> 산출물: [`Groovy_MSA_Phase11_Resilience.md`](Groovy_MSA_Phase11_Resilience.md)
> 범위: 대상 서비스는 study/content가 아니라 legacy-monolith/notification-service로 치환(근거는 산출물 문서 §1)

---

## Phase 12. 분산 Observability

**목표**: 기존 Prometheus/Grafana/Loki/Alloy 스택은 이미 좋은 기반. 여기에 **Distributed Tracing**을 추가.

```
Gateway(span A) → Study Service(span B) → Identity Service(span C) → DB(span D)
= 전부 하나의 traceId로 연결
```

OpenTelemetry의 Context Propagation으로 traceId/spanId/parentSpanId를 서비스 경계를 넘어 이어지게 구성합니다.

### 완료 기준
- [x] 하나의 요청이 여러 서비스를 거칠 때 동일 traceId로 Grafana(Tempo 등)에서 추적 가능
- [x] 로그에 traceId가 포함되어 Loki에서 특정 요청의 전체 흐름 검색 가능

> 산출물: [`Groovy_MSA_Phase12_분산Observability.md`](Groovy_MSA_Phase12_분산Observability.md)

---

## Phase 13. 테스트 전략 확장

서비스가 늘어나면 테스트 종류도 늘어납니다.

```
Unit Test → Component Test → Integration Test → Contract Test
→ Message Contract Test → E2E Test → Smoke Test
```

특히 Phase 8(동기 통신)에서 만든 서비스 간 의존은 **Contract Test**로 보호해야 합니다. Spring Cloud Contract로 producer/consumer 계약 기반 테스트/stub 생성을 검토합니다.

### 완료 기준
- [x] 서비스 간 API 1개 이상에 대해 Contract Test 존재
- [x] Event 스키마 변경 시 Consumer 쪽 테스트가 깨지는 걸 CI에서 감지 가능

> 산출물: [`Groovy_MSA_Phase13_테스트전략확장.md`](Groovy_MSA_Phase13_테스트전략확장.md)
> 범위: Spring Cloud Contract 대신 자체 구현 Consumer-Driven Contract Test(근거는 산출물
> 문서 §1)

---

## 전체 로드맵 요약

| Phase | 산출물 | 학습 개념 연결 |
|---|---|---|
| 0 | 의존성 조사표 | - |
| 1 | Modular Monolith | DDD Bounded Context |
| 2 | Contract 정의 | Event Schema |
| 3 | Gradle Multi-Project | - |
| 4 | Compose 뼈대 | - |
| 5 | API Gateway | 동기 통신/API Gateway |
| 6 | Notification 추출 | - |
| 7 | DB per Service | DB per Service, Eventual Consistency |
| 8 | 동기 통신 | REST |
| 9 | 비동기 + Outbox/Inbox | Kafka/RabbitMQ, Domain Event, Outbox/Inbox, Idempotency, At-least-once |
| 10 | 인증 재설계 | - |
| 11 | Resilience | (Saga 실패 처리와 연결) |
| 12 | Observability | - |
| 13 | 테스트 전략 | - |

**우선순위 제안**: Phase 0~6까지는 순서대로 반드시 진행 (기반 작업). Phase 7~9(DB 분리, 통신, 메시징)가 지금까지 학습한 핵심 개념이 실전 코드로 옮겨지는 구간이라 가장 신경 써야 합니다. Phase 10~13은 서비스가 2~3개 추출된 이후 점진적으로 병행해도 무방합니다.
