# Groovy MSA 전환 — Phase 9: 비동기 메시지 브로커 + Transactional Outbox

> 상위 계획: [`Groovy_MSA_전환계획.md`](Groovy_MSA_전환계획.md) Phase 9
> 선행 문서: [`Groovy_MSA_Phase8_SynchronousCommunication.md`](Groovy_MSA_Phase8_SynchronousCommunication.md)
> 목표: Phase 6~8에서 동기 HTTP로 임시 연결해뒀던 legacy→notification-service 알림 흐름을
> 정식 메시지 브로커(Kafka) + Transactional Outbox로 교체한다.
> 브로커는 **Kafka**로 진행(사용자 지정). 계획서의 선택 기준("단순 작업 큐면 RabbitMQ")만
> 보면 RabbitMQ도 후보였지만, 계획서 예시 다이어그램 자체가 이미 Kafka
> (`study-service → StudyApplicationApproved → Kafka → notification-service, (미래)
> recommendation-service`)를 쓰고 있어 향후 다중 컨슈머 확장과도 맞다.

## 1. Before / After

**Before(Phase 6~8)**: Study/Memoir/Calendar가 Spring `ApplicationEvent`를 발행 →
`NotificationEventBridge`가 `AFTER_COMMIT` 이후(별도 트랜잭션) `NotificationClient`로
`POST /internal/notifications` 동기 HTTP 호출.

**After(Phase 9)**: Study/Memoir/Calendar가 `NotificationOutboxPublisher`를 직접 호출 →
**자기 자신의 트랜잭션 안에서** `outbox_events` 테이블에 기록 → 별도 프로세스인
`OutboxRelay`(`@Scheduled`, 1초 간격 폴링)가 아직 발행 안 된 행을 Kafka(`notification-events`
토픽)로 발행 → notification-service의 `NotificationEventConsumer`(`@KafkaListener`)가 소비,
`processed_events`(Inbox)로 중복을 걸러내며 알림을 생성.

이 전환으로 **HTTP 브릿지 관련 코드를 전부 삭제**했다: `NotificationClient`,
`NotificationCreateRequest`, `NotificationEventBridge`(legacy), `InternalNotificationController`,
`InternalNotificationCreateRequest`(notification-service). `domain.notification.event`의
Spring Event record 7개도 삭제했다 — 이제 아무도 인프로세스로 구독하지 않는다.

## 2. Outbox가 "같은 트랜잭션"이어야 하는 이유를 실제로 지킨 방법

계획서 예시 코드가 강조한 지점을 그대로 따랐다:

```java
@Transactional
public void approve() {
    application.approve();
    outboxRepository.save(new OutboxEvent("StudyApplicationApproved", payload));
    // DB 저장 + Outbox 기록이 같은 트랜잭션
}
```

기존 `AFTER_COMMIT` 이벤트 리스너 패턴을 그대로 썼다면 이 원자성이 깨진다 — 커밋 이후에
발행되는 이벤트는 정의상 원 트랜잭션과 분리된 별도 트랜잭션이기 때문이다. 그래서
`NotificationOutboxPublisher`(→`OutboxEventWriter`)를 **Study/Application/Memoir/
MemoirComment/Calendar 서비스가 자기 `@Transactional` 메서드 안에서 직접 호출**하도록
바꿨다(5개 서비스, 7개 호출 지점 전부 수정). `OutboxEventWriter`에는 의도적으로
`@Transactional`을 걸지 않았다 — 걸면 호출자의 트랜잭션과 분리돼버려 원자성이 깨진다.

## 3. Kafka 인프라 — Boot 4.1에서 실제로 겪은 문제

- **KRaft 단일 노드**: `apache/kafka:latest` 공식 이미지, Zookeeper 없이 `process.roles=
  broker,controller`로 컨트롤러+브로커를 한 컨테이너에서 함께 띄운다.
- **`org.springframework.kafka:spring-kafka`만 넣으면 `KafkaTemplate` 빈이 안 생긴다** —
  Phase 5(Spring Cloud Gateway)와 Phase 6(Flyway) 때 이미 겪었던 "Boot 4.1이 오토컨피그를
  세분화된 모듈로 쪼갠다" 패턴을 이번에도 그대로 만났다. Maven Central에서
  `org.springframework.boot:spring-boot-kafka`(오토컨피그)와 `spring-boot-starter-kafka`가
  실제로 존재함을 확인하고 교체해 해결했다(`spring-boot-starter-kafka` → `spring-boot-kafka` +
  `spring-kafka` 전이 의존). **추측이 아니라 Maven Central 아티팩트 존재 여부를 curl로
  직접 확인한 뒤 고쳤다** — 이 프로젝트 전체에서 반복해온 방식 그대로.
- **프로듀서 `max.block.ms` 기본값(60초) 문제**: Kafka 다운 상태를 재현하는 테스트 중,
  `OutboxRelay`가 `KafkaTemplate.send(...).get(3, SECONDS)`로 3초 타임아웃을 걸어뒀는데도
  브로커가 죽으면 실패 로그가 훨씬 늦게(최대 60초 뒤에) 나타나는 걸 발견했다. 원인은
  `KafkaProducer.send()` 자체가 `Future`를 반환하기 전에 클러스터 메타데이터를 못 가져오면
  `max.block.ms`(기본 60초) 동안 그 안에서 막혀버려서, `.get(3, SECONDS)`가 적용될 기회조차
  없었던 것 — Phase 8에서 배운 "타임아웃 기본값 방치 금지"가 애플리케이션 레벨 RestClient뿐
  아니라 Kafka 프로듀서 설정에도 그대로 적용되는 사례였다. `max.block.ms=2000`,
  `request.timeout.ms=2000`, `delivery.timeout.ms=3000`을 명시해서 고쳤다.

## 4. 실제 검증 (Docker Compose, mock 없이 실측)

기존에 떠 있는 실서비스는 건드리지 않고 `docker-compose.msa.yml` 스택 안에서만 진행했다.

### 4-1. 정상 흐름
신청 → `outbox_events`에 즉시 기록(같은 트랜잭션) → 1초 뒤 Relay가 Kafka로 발행(`published=1`)
→ notification-service가 소비 → API로 알림 조회 성공. 전 과정을 실제 HTTP 호출로 확인.

### 4-2. Kafka 장애 시 이벤트 유실 없음 (완료 기준 2)
```
1) Kafka 컨테이너를 강제로 stop
2) Kafka가 죽은 상태에서 새 참여 신청 실행 → outbox_events에 published=0으로 기록됨을 확인
   (Producer 로그: "Connection to node 1 ... could not be established" 반복)
3) Kafka 컨테이너 재기동 + legacy-monolith 재기동(Relay 재시작)
4) 몇 초 뒤 재확인 → 그 이벤트가 자동으로 published=1로 바뀌고,
   notification-service에 정상적으로 알림이 생성됨을 API로 확인
```
DB(outbox_events)가 진실의 원천이라, 브로커가 죽어 있던 동안에도 이벤트 자체는 전혀
유실되지 않고 브로커가 돌아오자마자 자동으로 따라잡았다.

### 4-3. 중복 전달돼도 알림 중복 생성 안 됨 (완료 기준 3)
`kafka-console-producer.sh`로 **같은 eventId**를 가진 메시지를 의도적으로 2번 발행:
```
1번째 발행 → notification-service 로그: "알림 이벤트 처리 완료: eventId=dup-test-0001"
2번째 발행 → notification-service 로그: "이미 처리한 이벤트, 중복 무시: eventId=dup-test-0001"
```
`notification_db.notifications`에서 해당 이벤트로 생긴 행 개수를 직접 세어 **정확히 1건**만
있음을 확인했다(`processed_events`의 eventId 기본키가 두 번째 처리를 막았다).

## 5. 완료 기준 체크

- [x] Outbox 테이블 + Relay 프로세스 동작
- [x] 강제로 Kafka 컨테이너를 잠깐 내렸다 올려도 이벤트 유실 없음 확인 (재시도 검증) — §4-2 실측
- [x] 동일 이벤트를 의도적으로 2번 보내도 Notification이 중복 생성 안 됨 (Idempotency 검증) — §4-3 실측

## 6. 다음 단계로 넘길 것

- **토픽 자동 생성(`KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`)은 학습/검증 편의용**. 실제 운영이면
  파티션 수/복제 계수/보존 정책을 명시적으로 관리하는 토픽 생성 스크립트가 필요하다.
  파티션이 1개뿐이라 지금은 이벤트 발행 순서가 자연히 보존되지만, 향후 파티션을 늘리면
  같은 studyId/recipientId 이벤트가 같은 파티션으로 가도록 파티션 키 전략을 다시 설계해야
  한다(지금은 eventId를 키로 써서 사실상 무작위 분산 — 파티션 1개라 지금은 문제 없음).
- **Retry/Dead-letter는 아직 기본값**: `NotificationEventConsumer`가 예외를 던지면 Spring
  Kafka의 기본 에러 핸들러가 처리한다. 명시적인 재시도 횟수·백오프·DLQ 설정은 계획서
  Phase 11(Resilience)의 몫으로 남겨둔다.
- **다음 서비스 추출 시 이 Outbox+Kafka 패턴을 재사용**: Study/Content/Calendar가 실제로
  분리될 때, `NotificationOutboxPublisher`와 같은 구조(도메인 서비스 트랜잭션 안에서 직접
  Outbox 기록)를 그대로 템플릿으로 쓰면 된다.
