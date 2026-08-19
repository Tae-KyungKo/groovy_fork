# 04. Kafka DLQ 추가

**날짜**: 2026-08-19
**분석 결과 원문 항목**: 종합 분석 🔴 4번(Kafka consumer에 DLQ/ErrorHandler 부재)

## 배경

`notification-service/event/NotificationEventConsumer.java`는 JSON 파싱 실패를
`catch`해서 `return`으로 조용히 스킵했다 — 메시지가 재시도도 DLT 기록도 없이 영구
유실됐다. 반면 파싱 이후 발생하는 런타임 예외(`NotificationType.valueOf()` 실패, DB
예외 등)는 캐치 없이 그대로 전파됐는데, 컨테이너 레벨 에러 핸들러가 없어 Spring Kafka
기본 정책상 무한 재시도되며 해당 파티션 처리가 멈출 위험이 있었다. producer 3개
서비스(study/calendar/content-service)는 `@KafkaListener`가 없어(발행만 함) 이 문제와
무관 — DLQ가 필요한 곳은 notification-service 하나뿐이다.

## 조치

1. **`onMessage`가 예외를 삼키지 않도록 변경**: try/catch로 스킵하던 두 지점(envelope
   파싱, payload 파싱)을 `parseEnvelope`/`parsePayload` private 메서드로 분리하고, 실패 시
   새로 만든 `EventDeserializationException`(unchecked)을 던지도록 함 — 컨테이너의
   에러 핸들러가 재시도/DLT 여부를 결정할 수 있게 예외가 실제로 올라가야 한다.
2. **`EventDeserializationException` 신설**(`event` 패키지): "재시도해도 결과가 똑같은
   실패"임을 타입으로 명시.
3. **`AppConfig`에 `kafkaErrorHandler`(`DefaultErrorHandler`) 빈 추가**:
   - `DeadLetterPublishingRecoverer`로 실패한 메시지를 `<topic>.DLT`(기본
     `notification-events.DLT`)에 원본 그대로 재발행.
   - `EventDeserializationException`/`IllegalArgumentException`(NotificationType 값
     불일치)은 재시도 없이 즉시 DLT로.
   - 그 외 예외(DB 일시 장애 등)는 2초 간격으로 3번 재시도한 뒤에도 실패하면 DLT로.
   - Spring Boot의 Kafka 오토컨피그가 `DefaultErrorHandler` 빈을 자동으로 리스너 컨테이너
     팩토리에 연결해주므로 별도 팩토리 빈은 만들지 않음.
4. **`application.yml`에 producer serializer 설정 추가**: `DeadLetterPublishingRecoverer`가
   `KafkaTemplate`으로 DLT에 발행하려면 producer 쪽 key/value serializer가 필요한데,
   notification-service엔 이 설정이 아예 없었다(consumer 설정만 있었음) — study/calendar/
   content-service와 동일하게 `StringSerializer`로 추가.

## 검증

- `./gradlew :services:notification-service:compileJava` 성공.
- `./gradlew :services:notification-service:test` — `contextLoads()`를 뺀 나머지 통과.
  `contextLoads()`는 Redis 미기동으로 인한 기존부터의 무관한 실패지만, 그 실패 지점이
  `DefaultLifecycleProcessor`(빈 생성이 전부 끝난 뒤의 lifecycle 시작 단계)라는 점에서
  `kafkaErrorHandler`를 포함한 모든 빈 정의가 문제없이 완성됐다는 간접 증거로 확인.
- Kafka 브로커가 없는 이 환경 특성상 실제 DLT 발행까지는 end-to-end로 검증하지 못함 —
  로컬 스택(`docker-compose.local.yml`)에서 poison-pill 메시지를 넣어보는 수동 검증을
  권장.

## 스코프 밖으로 남긴 것

- study/calendar/content-service는 `@KafkaListener`가 없어 이번 조치 대상에서 제외.
