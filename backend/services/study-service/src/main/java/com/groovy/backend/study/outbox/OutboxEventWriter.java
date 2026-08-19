package com.groovy.backend.study.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * MSA 전환(study-service 추출): groovy(레거시)의 OutboxEventWriter와 동일한 패턴(Phase 9).
 * 호출하는 도메인 서비스 메서드 자신의 @Transactional 안에서 호출돼야 원자성이 보장된다.
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
			throw new IllegalStateException("Outbox 이벤트 직렬화 실패: eventType=" + eventType, e);
		}
	}
}
