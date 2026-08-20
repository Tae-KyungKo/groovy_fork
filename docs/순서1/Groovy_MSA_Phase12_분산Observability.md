# Groovy MSA 전환 — Phase 12: 분산 Observability(분산 트레이싱)

> 상위 계획: [`Groovy_MSA_전환계획.md`](Groovy_MSA_전환계획.md) Phase 12
> 선행 문서: [`Groovy_MSA_Phase11_Resilience.md`](Groovy_MSA_Phase11_Resilience.md)
> 목표: 하나의 요청이 여러 서비스(동기 HTTP + 비동기 Kafka 경계 포함)를 거칠 때 동일한
> traceId로 Tempo에서 추적하고, 로그에도 traceId를 남겨 Loki에서 검색 가능하게 한다.

## 1. Boot 4.1 아티팩트 분리 — 이번에도 재확인 필요했다

Flyway(Phase 6)/Kafka(Phase 9) 때 겪은 패턴이 트레이싱에도 그대로 있었다. Maven Central의
실제 jar를 열어(`unzip -l`, `AutoConfiguration.imports` 확인) 검증한 결과:

- `spring-boot-starter-actuator`에는 트레이싱 오토컨피그가 포함되지 않는다.
- `spring-boot-micrometer-tracing` — Micrometer Tracing 오토컨피그(신규 모듈, 별도 추가 필요).
- `spring-boot-opentelemetry` — OpenTelemetry SDK "틀"(Resource, OpenTelemetrySdk 빈 조립)만
  오토컨피그. **OTLP 로그 익스포트(`OtlpLoggingAutoConfiguration`)는 있지만, OTLP 트레이스
  익스포트 오토컨피그는 아예 없다** — 이번 Phase에서 새로 발견한 사실이다.

즉 `SdkTracerProvider`(OTLP 익스포터 포함)와 Micrometer `Tracer`/`Propagator` 빈은 Boot가
만들어주지 않는다 — `jjwt`/`resilience4j` 때처럼 실제 jar의 클래스를 `javap`로 열어
생성자/빌더 시그니처를 확인한 뒤 직접 조립했다(`TracingConfig`, §3).

api-gateway는 Boot 4.0.7에 고정돼 있는데(Phase 5 참고), 이 두 모듈이 4.0.7에도 존재하는지
Maven Central에서 별도로 확인한 뒤 추가했다.

## 2. 추가 의존성 (legacy-monolith / notification-service / api-gateway 공통)

```gradle
implementation 'org.springframework.boot:spring-boot-micrometer-tracing'
implementation 'org.springframework.boot:spring-boot-opentelemetry'
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
```

## 3. `TracingConfig` — 3개 서비스에 동일하게 배치(공유 라이브러리 아님)

`backend/libs/observability`는 순수 `java-library`로 Boot 의존성 관리(BOM)가 없고, 이
모듈을 쓰는 두 서비스(notification-service=Boot 4.1.0, api-gateway=Boot 4.0.7)가 서로
다른 Boot 버전을 쓴다. 여기에 트레이싱 의존성을 얹으면 서비스별 BOM이 관리하는 버전과
충돌할 위험이 있어, `TracingConfig` 클래스는 legacy/notification-service/api-gateway
3곳에 각자 패키지로 복제했다(이미 api-gateway가 Boot 버전을 독립적으로 고정한 것과 같은
"서비스별 독립 배포 트레인" 원칙 — 계획서가 경고하는 "공용 모듈에 비즈니스/과다 의존성을
넣지 말 것"과도 부합).

```
groovy/.../global/config/TracingConfig.java
backend/services/notification-service/.../notification/config/TracingConfig.java
backend/services/api-gateway/.../gateway/config/TracingConfig.java
```

핵심 조립(모든 API를 실제 jar에 `javap -p`로 확인 후 작성):

```java
@Bean SdkTracerProvider  // OtlpHttpSpanExporter + BatchSpanProcessor + Sampler.traceIdRatioBased
@Bean ContextPropagators // W3CTraceContextPropagator + W3CBaggagePropagator
// 위 두 빈을 Boot의 OpenTelemetrySdkAutoConfiguration이 모아 OpenTelemetrySdk 빈을 만들어준다.
@Bean Tracer              // OtelTracer(openTelemetrySdk.getTracer(...), OtelCurrentTraceContext, eventPublisher)
@Bean Propagator          // OtelPropagator(openTelemetrySdk.getPropagators(), ...)
```

생성자에서 `ContextStorage.addWrapper(new EventPublishingContextWrapper(...))`를 한 번
등록해 `Slf4JEventListener`/`Slf4JBaggageEventListener`가 컨텍스트 전환마다 SLF4J MDC에
`traceId`/`spanId`를 채우게 했다 — `libs/observability`의 `LogFields.TRACE_ID`가 이미
`"traceId"`라는 같은 키로 미리 정의돼 있어서 별도 매핑 없이 그대로 맞아떨어졌다.

## 4. Kafka 경계를 넘는 트레이스 전파

Spring for Kafka의 관측(Observation) 계측은 기본 비활성화라 명시적으로 켜야 한다
(`KafkaProperties$Template`/`$Listener`의 `observationEnabled` 필드를 `javap`로 확인):

```yaml
# legacy(발행 쪽)
spring.kafka.template.observation-enabled: true
# notification-service(소비 쪽)
spring.kafka.listener.observation-enabled: true
```

이게 켜지면 `OutboxRelay`가 `KafkaTemplate.send(...)`할 때 현재 트레이스 컨텍스트를
Kafka 레코드 헤더(`traceparent`)에 실어보내고, `NotificationEventConsumer`의
`@KafkaListener`가 그 헤더를 읽어 같은 트레이스의 자식 스팬으로 이어붙인다 — Outbox+Kafka로
갈아탄 Phase 9 이후에도(동기 HTTP가 아니라 비동기 메시징인데도) 트레이스 연속성이 끊기지
않는다.

## 5. `docker-compose.msa.yml` / `monitoring-msa/` 추가

기존 `monitoring/`(Loki 설정 `loki/config.yaml`, Alloy 설정 `alloy/config.alloy`)은 스택에
무관하게 재사용 가능하다 — Alloy가 Docker 소켓으로 "모든" 컨테이너를 자동 탐지해 Loki로
보내는 구조라 이 검증 스택에 그대로 읽기 전용으로 마운트했다. 원본 `docker-compose.yml`이
이 파일들을 쓰고 있어 **건드리지 않았다**.

Grafana 프로비저닝(Tempo 데이터소스 추가)과 Tempo 설정만 `monitoring-msa/`에 새로 뒀다 —
Tempo 설정은 Grafana 공식 예제
(`grafana/tempo` repo, `example/docker-compose/single-binary/tempo.yaml`)를 그대로 가져와
Prometheus remote-write가 필요한 `metrics_generator` 섹션만 뺐다(이 검증 스택엔
Prometheus가 없고, Phase 12 완료 기준은 트레이싱+로그이지 메트릭이 아니다).

추가된 서비스: `tempo`(OTLP gRPC 4317/HTTP 4318, 쿼리 3200→18200), `loki`(재사용, →18310),
`alloy`(재사용, Docker 소켓 마운트), `grafana`(Tempo+Loki 데이터소스, →18300). legacy/
notification-service/api-gateway 3곳에 `OTLP_TRACING_ENDPOINT: http://tempo:4318/v1/traces`
환경변수를 추가했다.

## 6. 실제 검증 (Docker Compose, 전체 사이클 실측)

### 6-1. 동기 HTTP 경계: api-gateway → legacy-monolith

`GET http://localhost:18080/api/health`(게이트웨이 경유)를 호출한 뒤 Tempo에서 트레이스를
조회하면, 하나의 traceId 아래 7개 스팬이 부모-자식으로 이어진다:

```
api-gateway      http get /**              (root)
api-gateway        └─ http get             (게이트웨이가 legacy로 보낸 클라이언트 호출)
groovy-backend        └─ http get /api/health   (legacy가 받은 서버 스팬)
groovy-backend            ├─ security filterchain before
groovy-backend            ├─ authorize request
groovy-backend            ├─ secured request
groovy-backend            └─ security filterchain after
```

Spring Security 필터체인까지 자동으로 스팬이 쪼개져서(Boot의 기본 Observation 계측),
게이트웨이 진입부터 시큐리티 인가까지 전체 요청 경로가 한 트레이스 안에 다 보인다.

### 6-2. 비동기 Kafka 경계: legacy(Outbox) → Kafka → notification-service

회원가입 → 로그인 → 스터디 생성 → 다른 사용자로 참여 신청(`POST /api/studies/{id}/applications`,
전부 게이트웨이 경유)까지 실제 업무 흐름을 실행해 `notificationOutboxPublisher.applicationSubmitted(...)`를
발생시켰다. Outbox 폴링 주기 이후 로그:

```
[legacy-monolith]        Outbox 이벤트 발행 성공: eventId=b2bd3f9c-..., eventType=APPLICATION_RECEIVED
                          traceId=10229c6c8a715c67f44572e592f9fd0a
[notification-service]   알림 이벤트 처리 완료: eventId=b2bd3f9c-..., eventType=APPLICATION_RECEIVED
                          traceId=10229c6c8a715c67f44572e592f9fd0a   ← 동일 traceId
```

같은 traceId로 Tempo를 조회하면:

```
groovy-backend        task outboxRelay.relay      (Outbox 폴링 스케줄 스팬)
groovy-backend          └─ notification-events send
notification-service          └─ notification-events process
notification-service                └─ publish
```

**HTTP 동기 호출이 Phase 9에서 이미 Kafka로 대체된 뒤에도, 트레이스는 Kafka 헤더를 통해
서비스 경계를 넘어 끊기지 않고 이어진다**는 걸 실측으로 확인했다.

### 6-3. Loki 검색 (LogQL)

```
curl -G http://localhost:18310/loki/api/v1/query_range \
  --data-urlencode 'query={job="docker-container-logs"} |= "10229c6c8a715c67f44572e592f9fd0a"'
```

→ `groovy-msa-legacy-monolith`, `groovy-msa-notification-service` 두 컨테이너의 로그
스트림에서 모두 매칭 — 하나의 traceId로 그 요청이 어느 서비스들을 거쳤는지 로그만으로도
전체 흐름을 검색/추적할 수 있음을 확인했다.

## 7. 완료 기준 체크

- [x] 하나의 요청이 여러 서비스를 거칠 때 동일 traceId로 Tempo에서 추적 가능 — §6-1(동기
      HTTP: api-gateway→legacy-monolith), §6-2(비동기 Kafka: legacy→notification-service)
      둘 다 실측
- [x] 로그에 traceId가 포함되어 Loki에서 특정 요청의 전체 흐름 검색 가능 — §6-3 LogQL 실측

## 8. 다음 단계로 넘길 것

- **트레이스-로그 상호 링크(Grafana `tracesToLogsV2`/Loki `derivedFields`)는 넣지 않음**:
  완료 기준이 "Tempo에서 추적 가능"과 "Loki에서 검색 가능"을 각각 요구할 뿐, Grafana UI
  안에서 클릭 한 번으로 오가는 연결까지는 요구하지 않는다. 정확한 필드 스키마를 추측하지
  않는다는 이 세션의 원칙상, 검증되지 않은 설정을 넣기보다는 범위에서 뺐다 — 다음에
  UI 상관관계가 필요해지면 그때 실제로 켜보고 검증하며 추가한다.
- **메트릭(Prometheus)은 이 검증 스택에 포함하지 않음**: Phase 12 완료 기준이 트레이싱+로그라
  범위를 거기에 맞췄다. legacy는 이미 자체 Prometheus 익스포트(`/actuator/prometheus`)를
  갖고 있으니, 여러 서비스의 메트릭을 한 곳에서 보고 싶어지면 그때 기존 `monitoring/prometheus`
  설정을 참고해 이 스택에도 추가하면 된다.
- **Phase 13(테스트 전략 확장)**: 계획서의 마지막 Phase. Contract Test 도입 여부는 사용자
  지시가 있을 때 진행한다.
