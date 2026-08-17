package com.groovy.backend.notification.event;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * legacy-monolith(global/outbox/OutboxEnvelope)가 Kafka로 보내는 메시지와 정확히 같은 모양이어야
 * 한다 — 이 record 자체가 두 서비스 간 계약이다(Groovy_MSA_Phase2_서비스경계와Contract.md §3-3).
 * payload는 이벤트마다 모양이 달라질 수 있어 우선 JsonNode로 받고, NotificationEventConsumer가
 * NotificationPayload로 다시 변환한다.
 */
public record OutboxEnvelope(
	String eventId,
	String eventType,
	String occurredAt,
	JsonNode payload
) {
}
