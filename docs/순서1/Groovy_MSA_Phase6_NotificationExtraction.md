# Groovy MSA 전환 — Phase 6: 첫 번째 서비스 추출 (Notification)

> 상위 계획: [`Groovy_MSA_전환계획.md`](Groovy_MSA_전환계획.md) Phase 6
> 선행 문서: [`Groovy_MSA_Phase5_ApiGateway.md`](Groovy_MSA_Phase5_ApiGateway.md)
> 목표: 가장 리스크가 낮은 도메인(Notification)을 실제로 별도 프로세스로 옮겨 "서비스 분리"를
> 실전 검증한다. 지금까지는 전부 골격/구조 작업이었고, 이번이 처음으로 실제 도메인 코드와
> DB 접근이 두 개의 독립 배포 단위로 갈라지는 단계다.

## 1. 핵심 설계 결정

### 1-1. Notification.recipient(User 엔티티) → recipientId(Long)

legacy에서 `Notification`은 `@ManyToOne User recipient`로 User를 직접 참조했다. notification-service는
User 도메인(identity-service, 아직 미추출)에 접근할 수 없으므로 이 연관관계를 유지할 수 없다.
`recipientId(Long)`로 바꿨고, DB 컬럼(`recipient_id`)과 FK 제약은 그대로라 스키마 변경은
없었다 — Phase 1에서 Calendar.study → Calendar.studyId에 썼던 것과 동일한 패턴.

부수 효과: 알림 생성 시 더 이상 "수신자가 실제로 존재하는 유저인지" User 테이블을 조회해서
확인하지 않는다(recipientId만 있으면 충분). 이 안전장치는 사실 방어적인 것이었지 실제로
쓰인 적은 없다(recipientId는 항상 legacy 내부의 이미 검증된 데이터에서 오기 때문) — 없어도
안전하다고 판단했다.

### 1-2. JWT에 userId(uid) 클레임 추가

가장 까다로웠던 지점. notification-service의 API(`GET /api/notifications` 등)는 "로그인한
사용자가 누구인지" 알아야 하는데, 기존 JWT는 email만 담고 있었고 email→userId 변환은
User 테이블(identity-service, 아직 없음)이 있어야 가능했다.

**해결**: JWT 자체에 `uid`(userId) 클레임을 추가했다(`TokenProvider.createToken`,
`UserService.login()`). 모든 서비스가 아직 같은 HMAC 시크릿(`JWT_SECRET_KEY`)을 공유하는
과도기이므로, notification-service는 identity-service를 호출하지 않고도 자체적으로 토큰을
검증해 `uid` 클레임을 바로 꺼내 쓸 수 있다. `@AuthenticationPrincipal`의 principal 타입도
`String email`이 아니라 `Long userId`로 바뀌었다 — 이 서비스에는 애초에 email로 조회할
User 테이블이 없으므로 이쪽이 더 자연스럽다.

> 계획서 Phase 10(인증 구조 재설계)이 지적한 "모든 서비스가 같은 HMAC secret을 공유하는
> 방식"이 정확히 지금 이 상태다. JWT self-contained claim 방식은 Phase 10에서 JWKS 기반으로
> 갈아탈 때도 유지되는 설계라(클레임 자체는 계속 필요), 지금 미리 해두는 게 헛수고가 아니다.

### 1-3. 인프로세스 이벤트 → 동기 HTTP 브릿지 (Phase 9 전 임시 단계)

계획서가 명시한 대로("Phase 9가 아직 없다면, 초기에는 동기 REST 호출로 먼저 분리") 접근했다.

- **legacy에 남긴 것**: `domain/notification/event/*` 7개 이벤트 record. Study/Memoir/Calendar가
  여전히 같은 JVM 안에서 `ApplicationEventPublisher`로 발행하는 건 그대로.
- **legacy에서 바꾼 것**: `NotificationEventListener`(인프로세스로 `NotificationService.
  createAndPublish(...)` 직접 호출) → `NotificationEventBridge`(`NotificationClient`로
  `POST http://notification-service:8085/internal/notifications` HTTP 호출). 텍스트 포맷팅
  (한국어 제목/본문 조합) 로직은 그대로 legacy에 남아있다 — notification-service는 Study/Memoir
  의미를 몰라도 되고, 그냥 "이 사람에게 이 제목/본문을 저장+push해라"만 받는다.
  `@Async("notificationTaskExecutor") @TransactionalEventListener(AFTER_COMMIT)` 패턴은
  그대로 유지해서, 원 트랜잭션 커밋 후에만·API 응답을 안 막고 알림을 보낸다는 성질을 지켰다.
- **notification-service에 새로 만든 것**: `POST /internal/notifications` — Internal API
  (Groovy_MSA_Phase2_서비스경계와Contract.md에서 설계한 대로). 인증 없음(permitAll) —
  API Gateway가 `/internal/**`을 외부로 라우팅하지 않는 것이 지금 단계의 유일한 접근 통제다.
  서비스 간 인증(mTLS/내부 토큰)은 계획서 Phase 10의 몫으로 남겨뒀다.

### 1-4. DB는 아직 공유 (계획대로)

notification-service는 legacy와 같은 `groovy_db`에 접속한다. 다만 **자체 Flyway 마이그레이션은
두지 않았다** — `notifications` 테이블은 legacy의 V6/V8 이력이 이미 만들어둔 것을 그대로 쓰고,
`ddl-auto: validate`로 매핑만 확인한다. 같은 스키마에 두 개의 독립된 `flyway_schema_history`가
부딪히는 걸 피하기 위한 선택이다. 완전한 스키마 소유권 이전(전용 DB로 물리 분리)은 Phase 7의 몫.

## 2. legacy-monolith에서 제거한 것

완료 기준 "Legacy Monolith에서 Notification 관련 코드/테이블 참조 제거"를 문자 그대로 실행했다.

- `Notification`, `NotificationType`, `NotificationRepository`, `NotificationResponse`,
  `NotificationService`, `NotificationRedisSubscriber`, `NotificationCleanupScheduler`,
  `NotificationPushMessage`, `NotificationController`, `NotificationEventListener` — 전부 삭제.
- `RedisConfig` 삭제, `spring-boot-starter-data-redis` 의존성 제거, `application.yml`의
  `spring.data.redis.*` 제거 — **Redis는 알림 도메인 전용이었어서 legacy에 더 이상 필요 없다.**
- `AsyncConfig`에서 `@EnableScheduling`(더 이상 `@Scheduled` 없음)과 수동 Jackson2
  `ObjectMapper` 빈(Redis 직렬화 전용이었음) 제거. `@EnableAsync` + `notificationTaskExecutor`는
  `NotificationEventBridge`가 계속 쓰므로 유지.
- `SecurityConfig`의 `/api/notifications/subscribe` permitAll 패턴 제거(그 컨트롤러 자체가 없음).

**중요한 부수 효과**: legacy 소스가 이렇게 바뀌었기 때문에, 이 브랜치의 `groovy/`를 빌드하는
어떤 compose 스택이든(`docker-compose.msa.yml`뿐 아니라 기존 루트 `docker-compose.yml`/
`docker-compose.prod.yml`도 포함) 더 이상 알림 기능을 자체적으로 제공하지 못한다.
`docker-compose.msa.yml`은 이미 `api-gateway` + `notification-service`를 함께 띄우므로 문제가
없지만, **기존 루트 compose 파일들은 아직 api-gateway/notification-service가 없어서 재빌드하면
알림 기능이 빠진 채로 뜬다.** 지금 당장은 그 스택들을 건드리지 않았지만(이미 실행 중인 운영/개발
환경에 영향 주지 않기 위해), 실제 전환 시점에는 그 compose 파일들도 `docker-compose.msa.yml`
체계로 교체하거나 통합해야 한다 — 이번 Phase의 의도된 한계로 기록해둔다.

## 3. 실제 검증 (Docker Compose, 실제 요청으로 end-to-end)

목(mock) 없이 API Gateway를 통해 실제 시나리오를 전부 실행했다.

```
1. 회원가입/로그인 → JWT payload에 uid 클레임 포함 확인: {"sub":"leader@test.com","uid":1,"role":"USER",...}
2. GET /api/notifications (JWT로) → notification-service가 200 반환, 빈 목록
3. Authorization 헤더 없이 호출 → 401 (notification-service 자체 JWT 필터 동작 확인)
4. 신청자가 스터디 참여 신청
   → legacy: ApplicationSubmittedEvent 발행 → NotificationEventBridge → HTTP POST
   → notification-service: /internal/notifications 수신 → DB 저장 → Redis publish
5. 방장이 GET /api/notifications → 신청 알림 도착 확인:
   {"type":"APPLICATION_RECEIVED","title":"새 참여 신청이 도착했어요",
    "message":"신청자님이 \"MSA 스터디\"에 참여 신청했어요.","targetId":"1"}
6. POST /{id}/read, POST /read-all → 정상 동작
7. POST /subscribe-ticket → 티켓 발급 → GET /subscribe?ticket=... → SSE "connected" 이벤트 수신
8. 잘못된 티켓으로 /subscribe 호출 → 400(IllegalArgumentException, GlobalExceptionHandler 처리) 확인
9. 회귀 확인: GET /api/studies(알림과 무관한 경로)는 여전히 legacy-monolith로 정상 라우팅
```

### 실제로 겪은 버그와 수정

1단계 검증에서 "잘못된 티켓으로 구독"이 400이 아니라 **401**을 반환하는 문제를 발견했다.
원인: `GlobalExceptionHandler`(`@RestControllerAdvice`)를 `com.groovy.backend.exception`
패키지에 두었는데, notification-service의 메인 클래스는 `com.groovy.backend.notification.
NotificationServiceApplication`이라 컴포넌트 스캔 루트가 `com.groovy.backend.notification`
이하로 한정된다 — `com.groovy.backend.exception`은 그 밖이라 빈으로 전혀 등록되지 않았고,
예외가 처리되지 않은 채 서블릿 컨테이너까지 올라가 `/error`로 포워딩됐다가 그 경로마저
인증이 필요해 엉뚱하게 401이 난 것이었다. (legacy는 메인 클래스가 `com.groovy.backend` 루트라
같은 구조에서도 문제가 없었다.) `com.groovy.backend.notification.exception`으로 옮겨서
해결하고 재검증했다 — 실제로 이런 종류의 "패키지 루트 불일치" 버그가 서비스 추출 과정에서
나온다는 걸 보여주는 사례라 그대로 기록해둔다.

## 4. 완료 기준 체크

- [x] Notification 관련 테이블(엔티티/리포지토리/서비스 코드)이 notification-service
      코드베이스로 이동 (물리적 DB 테이블 자체의 이전은 Phase 7)
- [x] Legacy Monolith에서 Notification 관련 코드/테이블 참조 제거 (Redis 의존성까지 함께 제거)
- [x] Gateway에서 `/api/notifications/**`가 신규 서비스로 라우팅됨
- [x] 기존 기능(SSE 알림) 동작 회귀 없음 확인 — 신청→알림 생성→조회→읽음 처리→SSE 구독까지
      전 과정을 실제 API 호출로 검증

## 5. 다음 단계로 넘길 것

- **기존 루트 docker-compose.yml/prod.yml 전환**: §2의 "중요한 부수 효과" 참고. 실제 배포
  전환 시점에 `docker-compose.msa.yml` 체계로 교체해야 한다.
- **Internal API 인증 없음**: `/internal/**`이 지금은 네트워크 경계(Gateway가 안 열어줌)에만
  의존한다. Phase 10에서 서비스 간 인증을 붙일 때 같이 정리한다.
- **DB 완전 분리는 Phase 7에서**: notification-service가 여전히 `groovy_db`를 쓴다.
- **다음 추출 후보**: 계획서 로드맵상 identity-service가 다음이지만, Phase 0/1에서 확인했듯
  User는 5개 도메인이 전부 참조하는 최대 결합 허브라 Notification보다 범위가 훨씬 크다.
  Phase 7(DB per Service)을 먼저 진행하며 나머지 도메인들의 분리 준비도를 같이 점검하는 것도
  검토할 만하다.
