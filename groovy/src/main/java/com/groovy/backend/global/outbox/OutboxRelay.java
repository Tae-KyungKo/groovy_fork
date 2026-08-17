package com.groovy.backend.global.outbox;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환 Phase 9: Outbox Publisher(Relay). outbox_events 테이블을 폴링하며 아직 발행하지
 * 않은 이벤트를 Kafka로 실제 발행한다. 계획서가 언급한 CDC(Debezium 등) 대신 폴링 방식을
 * 택했다 — 이 규모에서는 폴링만으로 충분하고, CDC는 별도 인프라(Debezium Connect)가 추가로
 * 필요해 지금 단계에서는 과하다.
 *
 * 발행이 실패하면(브로커 다운 등) published를 true로 바꾸지 않고 그대로 둔다 — 다음 폴링에서
 * 그대로 재시도된다. 이게 "브로커가 잠깐 죽어도 이벤트가 유실되지 않는다"는 보장의 핵심이다.
 * 순서를 지키기 위해, 한 배치 안에서 발행이 실패하면 그 이후 이벤트는 이번 폴링에서 건너뛴다
 * (다음 폴링에서 실패한 이벤트부터 다시 순서대로 시도).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

	static final String TOPIC = "notification-events";
	private static final long SEND_ACK_TIMEOUT_SECONDS = 3;

	private final OutboxEventRepository outboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;

	@Scheduled(fixedDelay = 1000)
	public void relay() {
		List<OutboxEvent> batch = outboxEventRepository.findTop50ByPublishedFalseOrderByIdAsc();

		for (OutboxEvent event : batch) {
			try {
				// get()으로 브로커의 ack를 기다린다 — 안 기다리고 바로 published=true로 표시하면
				// 실제로는 브로커에 안 들어갔는데 발행됐다고 착각하는 유실 케이스가 생길 수 있다.
				kafkaTemplate.send(TOPIC, event.getEventId(), event.getPayload())
					.get(SEND_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
				event.markPublished();
				outboxEventRepository.save(event);
				log.info("Outbox 이벤트 발행 성공: eventId={}, eventType={}", event.getEventId(), event.getEventType());
			} catch (Exception e) {
				log.warn("Outbox 이벤트 발행 실패, 다음 폴링에서 재시도: eventId={}, eventType={}",
					event.getEventId(), event.getEventType(), e);
				return;
			}
		}
	}
}
