# Groovy MSA 전환 — Phase 13: 테스트 전략 확장 (Contract Test)

> 상위 계획: [`Groovy_MSA_전환계획.md`](Groovy_MSA_전환계획.md) Phase 13
> 선행 문서: [`Groovy_MSA_Phase12_분산Observability.md`](Groovy_MSA_Phase12_분산Observability.md)
> 목표: 서비스 간 API(동기 HTTP)와 이벤트(비동기 Kafka) 계약이 한쪽에서 깨졌을 때 CI에서
> 바로 감지되게 한다.

## 1. Spring Cloud Contract 대신 가벼운 자체 Consumer-Driven Contract Test를 택한 이유

계획서는 "Spring Cloud Contract로 producer/consumer 계약 기반 테스트/stub 생성을 검토"하라고
제안한다. 하지만 Spring Cloud Contract Verifier는 `spring-cloud-contract-dependencies` BOM에
묶여 있고, Phase 5에서 이미 Spring Cloud 자체가 **Boot 4.0.7까지만** 검증돼 있다는 걸
실제로 겪었다(그래서 api-gateway만 예외적으로 4.0.7에 고정, [`Groovy_MSA_Phase5_ApiGateway.md`](Groovy_MSA_Phase5_ApiGateway.md)
참고). 계약 테스트 대상인 legacy-monolith와 notification-service는 둘 다 Boot 4.1.0이라
Spring Cloud Contract를 끌어오면 같은 버전 충돌을 또 만날 위험이 크다 — resilience4j를
Spring Cloud CircuitBreaker 대신 코어 라이브러리로 직접 조립했던 Phase 11과 같은 판단이다.

대신 **고정된 JSON 픽스처를 "계약" 삼아 양쪽이 각자 검증하는 소비자 주도 계약 테스트**
방식을 새 프레임워크 의존성 없이 구현했다 — 이미 두 서비스가 갖고 있는 Jackson/jjwt만
사용한다.

## 2. Contract Test #1 — 서비스 간 API (JWKS, 동기 HTTP)

Phase 8/11에서 이미 확인했듯, 지금 남은 유일한 서비스 간 동기 호출은
`notification-service → legacy-monolith`의 `GET /.well-known/jwks.json`이다.

- **Provider 쪽** (`groovy/.../global/auth/jwt/JwksControllerContractTest`): `JwksController`의
  실제 응답을 **컨슈머가 실제로 쓰는 라이브러리(jjwt의 `Jwks.setParser()`)로 직접 파싱**해
  "진짜로 소비 가능한지" 검증한다. Phase 10에서 실제로 겪은 버그(`JwkSet`을 그대로 반환하면
  Jackson이 `{"keys":{}}`로 잘못 직렬화하던 문제)의 회귀 방지도 겸한다.
- **Consumer 쪽** (`notification-service/.../auth/JwksKeyLocatorContractTest`): JDK 내장
  `com.sun.net.httpserver.HttpServer`로 legacy의 `JwksController`와 정확히 같은 응답 모양
  (`{"keys":[...]}`, jjwt `RsaPublicJwk`를 `LinkedHashMap`으로 감싼 형태)을 흉내 낸 스텁을
  띄우고, **실제 `JwksKeyLocator`가 RS256으로 서명된 진짜 JWT를 검증까지 해낼 수 있는지**
  확인한다. 처음엔 `Jwts.header().keyId(...).build()`로 헤더를 손으로 만들어
  `locate()`를 직접 호출했는데, 이게 `DefaultHeader`(=`ProtectedHeader`가 아님)를 반환해서
  실패했다(§4 참고) — legacy의 `TokenProvider`와 똑같이 `signWith(...)`로 실제 서명하고
  `Jwts.parser().keyLocator(locator).build().parseSignedClaims(...)`로 검증하는 방식으로
  바꿔 실제 운영 코드 경로를 그대로 재현했다.

## 3. Contract Test #2 — 이벤트 스키마 (Kafka, 비동기 메시지)

legacy(Producer)의 `OutboxEnvelope`+`NotificationPayload`와 notification-service(Consumer)의
동일한 이름의 record 2벌은 서로 다른 Gradle 빌드에 각자 정의돼 있다 — legacy는 별도 빌드라
`libs/event-contract`(backend/ 하위 서비스만 쓸 수 있음)를 공유할 수 없다(TracingConfig를
서비스마다 복제해야 했던 것과 같은 제약). 두 record의 코드 주석에도 원래
"필드가 정확히 일치해야 한다(서비스 간 계약)"이라고만 적혀 있었을 뿐, 이를 실제로
강제하는 테스트는 없었다.

고정된 픽스처 `contracts/application-received-event.json`을 두 프로젝트의 test resources에
동일하게 두고(파일 자체가 계약 문서):

- **Producer 쪽** (`groovy/.../global/outbox/OutboxEventContractTest`): (1) 픽스처를 legacy의
  `OutboxEnvelope`/`NotificationPayload`로 역직렬화할 수 있는지, (2) legacy가 실제로
  직렬화하는 JSON의 필드 구성이 픽스처와 정확히 같은지(`Set<String>` 필드명 비교) 검증한다.
- **Consumer 쪽** (`notification-service/.../event/NotificationEventConsumerContractTest`):
  `NotificationEventConsumer.onMessage(String)`가 실제로 하는 2단계 역직렬화(문자열 →
  `OutboxEnvelope`, `payload` → `NotificationPayload`)를 그대로 재현해 픽스처를 처리하고,
  필드 값까지 검증한다.

## 4. 설계가 실제로 드리프트를 잡아내는지 검증

문서만으로 "계약이 지켜진다"고 주장하지 않고, 실제로 필드명을 하나 바꿔서 깨지는지
확인했다: legacy의 `NotificationPayload.title`을 `heading`으로 임시 변경한 뒤
`OutboxEventContractTest`만 돌리자—

```
error: cannot find symbol
    assertThat(payload.title()).isNotBlank();
                      ^
  symbol:   method title()
```

**컴파일 단계에서 즉시 빌드가 깨졌다** — 런타임 검증보다도 더 빠르게 잡힌다. 확인 후
`NotificationPayload.java`를 원상 복구했다.

## 5. 실제 검증 (Docker Compose, 전체 사이클 실측)

Kafka는 브로커 자체의 리스너/SASL 설정을 건드릴 위험이 있어(호스트 노출용 별도
EXTERNAL 리스너가 필요해지는데, Phase 9/10에서 어렵게 맞춰둔 설정을 다시 흔들고 싶지
않았다) 호스트에 포트를 열지 않았다. 대신 `groovy-msa-net`에 붙는 1회성 컨테이너
(`eclipse-temurin:21-jdk`)에서 `./gradlew test`를 실행해, 컨테이너 이름(`msa-mysql`,
`kafka`, `legacy-monolith`)으로 실제 인프라에 접속해 전체 스위트를 돌렸다(MySQL만
호스트에서도 접근 가능하도록 `docker-compose.msa.yml`의 `msa-mysql`에 `18306:3306`
포트를 추가했다 — Phase 4에서 뺐던 DB 호스트 노출을 Phase 13에서 테스트 목적으로 다시 열었다).

```
legacy-monolith 전체 테스트:        8개 통과, 실패 0, 에러 0
  (GroovyApplicationTests, ConcurrencyTest, ModuleBoundaryTest,
   CalendarEntityBoundaryTest, JwksControllerContractTest,
   OutboxEventContractTest ×2)
notification-service 전체 테스트:   3개 통과, 실패 0, 에러 0
  (NotificationServiceApplicationTests, JwksKeyLocatorContractTest,
   NotificationEventConsumerContractTest)
```

## 6. 완료 기준 체크

- [x] 서비스 간 API 1개 이상에 대해 Contract Test 존재 — §2 (JWKS, Provider+Consumer 양쪽)
- [x] Event 스키마 변경 시 Consumer 쪽 테스트가 깨지는 걸 CI에서 감지 가능 — §3(Consumer
      쪽 테스트 구조), §4(실제로 필드명을 바꿔 컴파일 실패까지 확인한 실측)

## 7. 다음 단계로 넘길 것

- **Unit/Component/Integration/E2E/Smoke Test 전 종류를 새로 갖추지는 않음**: 계획서가
  나열한 테스트 피라미드 전체가 아니라, "서비스 경계가 실제로 존재하는 지점"(동기 API 1개,
  비동기 이벤트 1개)에 한해 Contract Test를 추가하는 데 집중했다 — 이미 ArchUnit(Phase 1),
  전체 사이클 실측(Phase 8~12)으로 다른 계층은 어느 정도 커버되고 있다.
- **Spring Cloud Contract 재검토 시점**: identity-service/study-service 등 나머지 서비스가
  실제로 도메인 코드를 갖게 되고 서비스 간 동기 호출이 늘어나면(Phase 8이 예고한
  `GET /internal/studies/...` 류), 그때는 Boot 버전을 다시 점검하고(Spring Cloud 릴리스
  트레인이 4.1.x를 검증 완료했는지) Spring Cloud Contract의 stub-runner 도입을 다시 검토할
  가치가 있다 — 지금은 API가 1개뿐이라 자체 구현 비용이 훨씬 낮았을 뿐이다.
- **Kafka 호스트 포트는 열지 않음**: 이유는 §5. 앞으로 호스트에서 직접 Kafka에 붙어야 할
  필요가 생기면(예: 로컬 CLI 프로듀서로 디버깅), 그때 EXTERNAL 리스너를 별도로 추가하는
  작업을 새로 검토한다.
