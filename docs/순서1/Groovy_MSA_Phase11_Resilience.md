# Groovy MSA 전환 — Phase 11: Resilience 적용

> 상위 계획: [`Groovy_MSA_전환계획.md`](Groovy_MSA_전환계획.md) Phase 11
> 선행 문서: [`Groovy_MSA_Phase10_AuthRedesign.md`](Groovy_MSA_Phase10_AuthRedesign.md)
> 목표: 네트워크가 낀 동기 호출에 Timeout → Retry(지수 백오프+지터) → Circuit Breaker → Fallback을
> 적용한다.

## 1. 대상 — 지금 실제로 남아있는 유일한 동기 서비스 간 호출

계획서 예시는 "study-service를 죽인 상태에서 content-service가 전체 장애로 안 번지는지"지만,
Phase 9에서 legacy→notification-service의 유일한 동기 HTTP 호출(`/internal/notifications`)이
Kafka로 대체됐다. 그 결과 **지금 이 프로젝트에 남은 유일한 서비스 간 동기 호출은
notification-service의 `JwksKeyLocator`(Phase 10에서 만든, legacy-monolith의 JWKS를
가져오는 호출)뿐**이다. 이 호출에 Resilience 4요소를 전부 적용했다 — 계획서 완료 기준이
요구하는 "동기 호출에 적용"이라는 조건과 "장애가 안 번지는지 확인"이라는 조건 둘 다,
study/content 대신 legacy-monolith/notification-service로 자연스럽게 치환된다.

## 2. 라이브러리 선택 — Spring Cloud CircuitBreaker 대신 resilience4j 코어 직접 사용

계획서는 "Spring Cloud CircuitBreaker(Resilience4j 기반)"를 권장하지만, Spring Cloud
자체가 Boot 4.0.7까지만 검증돼 있다는 걸 Phase 5(API Gateway)에서 이미 겪었다. Spring Cloud
CircuitBreaker 스타터를 또 끌어오면 같은 버전 충돌을 다시 만날 게 뻔해서, **resilience4j
코어 라이브러리(`resilience4j-circuitbreaker`, `resilience4j-retry`)를 Spring Boot
오토컨피그 없이 직접 코드로 조립**했다. 이 라이브러리들은 순수 Java라 Boot 버전과 전혀
무관하다 — 이번 Phase에서는 "Boot 4.1 아티팩트 분리" 계열 문제를 겪지 않았다.

jjwt 때와 동일하게, `CircuitBreaker.of/decorateSupplier`, `Retry.of/decorateSupplier`,
`IntervalFunction.ofExponentialRandomBackoff`(지수 백오프+지터), `EventPublisher.onStateTransition
/onRetry` 등의 API는 실제 jar를 javap로 확인한 뒤 사용했다.

## 3. 적용 내용 (`JwksKeyLocator`)

```
Timeout        → JdkClientHttpRequestFactory에 connect 2s / read 3s 명시 (Phase 8와 동일 패턴)
Retry          → 최대 3회, IntervalFunction.ofExponentialRandomBackoff(200ms, x2) — 지수 백오프+지터
CircuitBreaker → 슬라이딩 윈도우 5, 최소 호출 3, 실패율 50% 이상이면 10초간 OPEN
Fallback       → 위가 전부 실패해도 예외를 던지지 않고 "기존 캐시(마지막으로 성공했던 JWKS)"를
                 그대로 유지한 채 조용히 리턴 — 이미 캐시에 있는 kid는 legacy-monolith가
                 죽어 있어도 계속 검증 가능하다.
```

CircuitBreaker는 Retry의 바깥쪽에 둔다(`CircuitBreaker.decorateSupplier(cb,
Retry.decorateSupplier(retry, fetch))`) — 그래야 CB가 재시도 한 번 한 번이 아니라 "재시도를
다 해본 뒤의 최종 결과" 하나만 실패/성공으로 기록한다. 상태 전이와 재시도 시도를 각각 로그로
남겨 실제 동작을 관측할 수 있게 했다.

## 4. 실제 검증 (Docker Compose, 전체 사이클 실측)

검증을 쉽게 재현하려면 캐시가 자주 만료돼야 해서, 이 검증 스택에서만 JWKS 캐시 TTL을
10초로 낮췄다(운영 기본값은 5분). 정상 흐름으로 캐시를 한 번 데운 뒤 `legacy-monolith`를
`docker stop`으로 강제 종료하고 반복 요청을 보냈다.

```
요청 1(직후, 캐시 만료 후 첫 요청): 9.3초 소요, 200 OK
  → 로그: connect timed out ×2(재시도), 실패해도 기존 캐시(1개 키) 유지
  → CircuitBreaker: CLOSED → OPEN 전이
요청 2~5(그 이후): 15~20ms, 200 OK
  → CircuitBreaker가 OPEN이라 네트워크 시도조차 하지 않고 즉시 거부(fail fast)
  → 그래도 기존 캐시로 계속 인증 성공 — 클라이언트 입장에서는 장애가 전혀 안 보인다.
```

**핵심 확인**: legacy-monolith가 완전히 죽어 있는 동안에도 notification-service의
`GET /api/notifications`는 단 한 번도 실패하지 않았다(전부 200) — Fallback이 실제로
장애 전파를 막았다.

**복구 확인**: CircuitBreaker의 `waitDurationInOpenState`(10초)가 지난 뒤 legacy-monolith를
재기동하고 새 요청을 보내자:
```
OPEN → HALF_OPEN → CLOSED 전이 로그 확인
JWKS 갱신 완료 로그 재등장(새 kid — legacy가 재기동되며 키가 새로 생성됨, Phase 10에서 문서화한
단순화의 자연스러운 결과)
```
완전히 정상 상태로 자동 복구됨을 확인했다.

## 5. 완료 기준 체크

- [x] Phase 8에서 만든 동기 호출에 Timeout, Retry, Circuit Breaker 적용 — 대상이
      legacy→notification-service HTTP(Phase 9로 대체됨)에서 notification-service→
      legacy-monolith JWKS(Phase 10에서 신설)로 바뀌었지만, "지금 남은 유일한 동기 호출"이라는
      본질은 동일하게 적용
- [x] 의도적으로 죽인 상태에서 전체 장애로 번지지 않고 Fallback 동작 확인 — §4 실측
      (study-service/content-service 대신 legacy-monolith/notification-service로 치환)

## 6. 다음 단계로 넘길 것

- **Bulkhead는 적용하지 않음**: 계획서 다이어그램의 마지막 요소(Bulkhead)는 스레드 풀/커넥션
  풀 격리가 목적인데, 지금 이 호출 하나만으로는 격리할 다른 자원이 마땅치 않아 범위에서 뺐다.
  여러 개의 동기 호출이 생기는 시점(다음 서비스 추출)에 같이 검토한다.
- **다음 서비스 추출 시 이 패턴을 재사용**: `JwksKeyLocator`에 쓴
  Timeout+Retry+CircuitBreaker+Fallback 조합(resilience4j 코어 직접 조립)이 앞으로 생길
  모든 동기 서비스 간 호출(Phase 8에서 예고된 `GET /internal/studies/...` 류)에 그대로
  적용 가능한 템플릿이다.
