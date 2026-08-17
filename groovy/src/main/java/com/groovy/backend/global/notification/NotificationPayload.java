package com.groovy.backend.global.notification;

/**
 * Outbox 이벤트의 payload 모양. notification-service가 도메인(Study/Memoir/Calendar) 의미를
 * 몰라도 되도록, 한국어 제목/본문 조합은 여기 담기 전에 이미 끝나 있다(NotificationOutboxPublisher가
 * 채운다) — Phase 6에서 정립한 "notification-service는 저장+push만" 원칙을 그대로 유지한다.
 * notification-service의 동일한 이름의 record와 필드가 정확히 일치해야 한다(서비스 간 계약).
 */
public record NotificationPayload(
	Long recipientUserId,
	String type,
	String title,
	String message,
	Long targetId
) {
}
