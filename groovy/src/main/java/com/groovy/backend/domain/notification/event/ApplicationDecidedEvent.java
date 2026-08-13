package com.groovy.backend.domain.notification.event;

/** 방장이 참여 신청을 승인/거절했을 때, 신청자 1명에게 보낼 알림 이벤트. */
public record ApplicationDecidedEvent(
	Long recipientUserId,
	boolean approved,
	Long studyId,
	String studyTitle
) {
}
