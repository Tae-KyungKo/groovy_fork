package com.groovy.backend.notification.event;

/**
 * legacy-monolith(global/notification/NotificationPayload)와 필드가 정확히 일치해야 하는
 * 서비스 간 계약. 한국어 제목/본문은 legacy 쪽에서 이미 완성해서 보낸다 — 이 서비스는
 * Study/Memoir 같은 도메인 의미를 몰라도 저장+push만 하면 된다(Phase 6에서 정립한 원칙).
 */
public record NotificationPayload(
	Long recipientUserId,
	String type,
	String title,
	String message,
	Long targetId
) {
}
