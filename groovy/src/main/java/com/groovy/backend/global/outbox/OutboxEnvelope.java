package com.groovy.backend.global.outbox;

/**
 * Kafka 메시지 바디로 그대로 나가는 봉투 구조.
 * Groovy_MSA_Phase2_서비스경계와Contract.md §3-3에서 정의한 EventEnvelope 스키마와 동일한 모양
 * (eventId/eventType/occurredAt/payload)이며, notification-service의 OutboxEnvelope와 필드가
 * 정확히 일치해야 한다(두 서비스가 이 구조로 계약을 맺는다).
 */
public record OutboxEnvelope(
	String eventId,
	String eventType,
	String occurredAt,
	Object payload
) {
}
