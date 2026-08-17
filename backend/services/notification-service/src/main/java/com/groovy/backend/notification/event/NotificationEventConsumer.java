package com.groovy.backend.notification.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groovy.backend.notification.NotificationType;
import com.groovy.backend.notification.inbox.ProcessedEvent;
import com.groovy.backend.notification.inbox.ProcessedEventRepository;
import com.groovy.backend.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환 Phase 9: 예전 InternalNotificationController(HTTP)를 대체한다. legacy의 OutboxRelay가
 * Kafka로 발행한 이벤트를 소비한다.
 *
 * Inbox(멱등성): Kafka는 at-least-once만 보장하므로 같은 메시지가 두 번 배달될 수 있다.
 * eventId가 이미 처리된 적 있으면 조용히 무시한다 — "알림 생성 + Inbox 기록"을 같은
 * @Transactional 안에서 원자적으로 처리해서, 두 작업 사이에 크래시가 나도 상태가 어긋나지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

	private final ProcessedEventRepository processedEventRepository;
	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;

	@KafkaListener(topics = "${notification.kafka.topic:notification-events}", groupId = "notification-service")
	@Transactional
	public void onMessage(String message) {
		OutboxEnvelope envelope;
		try {
			envelope = objectMapper.readValue(message, OutboxEnvelope.class);
		} catch (JsonProcessingException e) {
			log.error("이벤트 메시지 파싱 실패, 스킵: {}", message, e);
			return;
		}

		if (processedEventRepository.existsById(envelope.eventId())) {
			log.info("이미 처리한 이벤트, 중복 무시: eventId={}, eventType={}", envelope.eventId(), envelope.eventType());
			return;
		}

		try {
			NotificationPayload payload = objectMapper.treeToValue(envelope.payload(), NotificationPayload.class);
			notificationService.createAndPublish(
				payload.recipientUserId(),
				NotificationType.valueOf(payload.type()),
				payload.title(),
				payload.message(),
				payload.targetId()
			);
			processedEventRepository.save(new ProcessedEvent(envelope.eventId()));
			log.info("알림 이벤트 처리 완료: eventId={}, eventType={}", envelope.eventId(), envelope.eventType());
		} catch (JsonProcessingException e) {
			log.error("이벤트 payload 파싱 실패, 스킵: eventId={}", envelope.eventId(), e);
		}
	}
}
