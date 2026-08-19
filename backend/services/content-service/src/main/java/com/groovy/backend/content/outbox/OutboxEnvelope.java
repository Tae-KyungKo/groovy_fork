package com.groovy.backend.content.outbox;

/**
 * Kafka 메시지 바디로 그대로 나가는 봉투 구조. groovy(레거시 시절)/study-service/
 * calendar-service/notification-service의 OutboxEnvelope와 필드가 정확히 일치해야 한다(여러
 * 서비스가 같은 구조로 계약을 맺는다).
 */
public record OutboxEnvelope(
	String eventId,
	String eventType,
	String occurredAt,
	Object payload
) {
}
