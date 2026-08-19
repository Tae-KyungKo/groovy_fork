package com.groovy.backend.study.notification;

/**
 * Outbox 이벤트의 payload 모양. groovy(레거시)/notification-service의 동일한 이름의 record와
 * 필드가 정확히 일치해야 한다(서비스 간 계약).
 */
public record NotificationPayload(
	Long recipientUserId,
	String type,
	String title,
	String message,
	Long targetId
) {
}
