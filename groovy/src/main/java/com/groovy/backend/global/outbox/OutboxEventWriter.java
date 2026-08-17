package com.groovy.backend.global.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * MSA 전환 Phase 9: 도메인 서비스가 이 컴포넌트를 호출하는 지점은 항상 그 서비스 메서드 자신의
 * @Transactional 안이어야 한다 — 그래야 비즈니스 데이터 저장과 Outbox 기록이 같은 트랜잭션으로
 * 묶여 원자성이 보장된다("DB 저장 + Outbox 기록이 같은 트랜잭션", Groovy_MSA_전환계획.md Phase 9).
 * 별도로 @Transactional을 걸지 않는 이유도 이것 — 걸면 오히려 호출자의 트랜잭션과 분리돼버려
 * Outbox 패턴의 핵심 전제가 깨진다.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	public void write(String eventType, Object payload) {
		String eventId = UUID.randomUUID().toString();
		OutboxEnvelope envelope = new OutboxEnvelope(eventId, eventType, Instant.now().toString(), payload);

		try {
			String json = objectMapper.writeValueAsString(envelope);
			outboxEventRepository.save(OutboxEvent.builder()
				.eventId(eventId)
				.eventType(eventType)
				.payload(json)
				.build());
		} catch (JsonProcessingException e) {
			// 여기서 실패하면 호출자의 트랜잭션 전체가 롤백된다 — 의도된 동작이다. 알림을 못 남길
			// 상황이면(직렬화조차 안 되는 잘못된 payload) 비즈니스 데이터도 같이 저장하지 않는 게 맞다.
			throw new IllegalStateException("Outbox 이벤트 직렬화 실패: eventType=" + eventType, e);
		}
	}
}
