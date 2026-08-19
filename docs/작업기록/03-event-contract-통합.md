# 03. event-contract 실사용 전환

**날짜**: 2026-08-19
**분석 결과 원문 항목**: 종합 분석 🔴 3번(event-contract 모듈이 완전히 죽은 코드)

## 배경

`backend/libs/event-contract`에 서비스 간 이벤트 공용 봉투 `EventEnvelope<T>`가 있었지만,
5개 서비스 `build.gradle` 전부 의존성은 걸려 있었음에도 실제 소스코드 어디에서도
`com.groovy.backend.eventcontract` 패키지를 import하지 않고 있었다(grep 0건). 대신
study/calendar/content/notification-service 4곳이 각자 로컬 `OutboxEnvelope`을 복붙했는데,
`schemaVersion` 필드가 아예 빠져 있고 `payload`가 제네릭 대신 `Object`/`JsonNode`라 타입
안정성이 없었다. `notification-service`의 로컬 `NotificationPayload` 주석은 이미 삭제된
`legacy-monolith`를 여전히 참조하고 있었다.

## 왜 이 패턴 자체는 유지하는가

같은 언어(Java)로 된 모노레포에서 서비스 간 이벤트 계약을 공유 코드 라이브러리로 강제하는
것은 업계에서 통용되는 정상적인 방법이다(다른 언어/다른 팀이 서비스를 소유할 때는 대신
스키마 레지스트리+Avro/Protobuf나 Pact 같은 Consumer-Driven Contract Test를 쓴다). 문제는
패턴 선택이 아니라 "만들어놓고 실제로 연결하지 않은 것"이었다.

## 조치

1. `event-contract`에 실제 운영 중인 계약(`NotificationPayload`: recipientUserId/type/title/
   message/targetId)을 새로 추가. 기존에 있던 구조화 Payload record들(`StudyApplicationSubmittedPayload`
   등, 제목/본문을 소비자가 렌더링하는 설계)은 실제 구현과 모양이 달라 그대로 못 씀 — 손대지
   않고 남겨둠(별도 정리 필요성만 기록).
2. study/calendar/content/notification-service 4곳의 로컬 `OutboxEnvelope`, 3곳(study/
   calendar/content)의 로컬 `NotificationPayload`, notification-service의 로컬
   `NotificationPayload` 삭제 → `event-contract`의 공유 타입으로 교체.
3. **부수 발견/수정**: `EventEnvelope.occurredAt`이 `Instant`인데, 각 서비스가 Boot 4
   오토컨피그를 우회해 수동으로 만드는 구버전 Jackson `ObjectMapper`에는 `JavaTimeModule`이
   등록돼 있지 않아 직렬화가 실패할 상황이었다. `jackson-datatype-jsr310` 의존성을 4개
   서비스에 추가하고 `SchedulingConfig`(study/calendar/content)·`AppConfig`(notification)의
   `ObjectMapper` 빈에 `JavaTimeModule`을 등록.
4. notification-service의 계약 테스트(`NotificationEventConsumerContractTest`)와 fixture
   (`application-received-event.json`)를 새 스키마에 맞게 갱신 — `schemaVersion` 필드 추가,
   `occurredAt`에 `Z` 오프셋 추가(기존 fixture는 이 부분이 원래도 틀려 있었는데 필드 타입이
   느슨한 `String`이라 안 걸렸던 것).

## 스코프 밖으로 남긴 것

- `OutboxEvent`/`OutboxEventRepository`/`OutboxRelay`/`OutboxEventWriter` 자체도 3개
  서비스에서 100% 동일 코드로 중복돼 있다. 이건 "이벤트 계약(DTO)"이 아니라 "공통 인프라
  코드" 범주라 [06(공유 라이브러리 논의)](./README.md)에서 다루기로 하고 손대지 않음.
- 실제 발행되는 `eventType` 문자열(`"APPLICATION_RECEIVED"` 등)이 `EventTypes.java`의 상수
  (`STUDY_APPLICATION_SUBMITTED` 등)와 이름이 다르다 — `EventTypes`도 사실상 안 쓰이고
  있다는 뜻이지만, 이벤트 이름 자체를 바꾸는 건 동작 변경 소지가 있어 보고만 하고 손대지 않음.

## 검증

- `./gradlew :libs:event-contract:compileJava :services:{study,calendar,content,notification}-service:compileJava` 전부 성공.
- `./gradlew :services:{study,calendar,content,notification}-service:test` — study/calendar/
  content-service 전부 통과. notification-service는 계약 테스트 포함 3개 통과, 나머지 1개
  (`NotificationServiceApplicationTests.contextLoads()`)는 이 개발 환경에 Redis가 떠 있지
  않아 나는 사전부터 있던 무관한 실패(Lettuce `ConnectException`).
- `grep -rln "eventcontract"`로 8개 파일에서 실제로 쓰이고 있음을 확인.
