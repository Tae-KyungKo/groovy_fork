package com.groovy.backend.eventcontract.study;

import java.util.List;

/** study-service 발행, notification-service 소비. groovy(레거시)의 WaitlistSeatOpenedEvent와 대응. */
public record StudySeatAvailablePayload(
	List<Long> recipientUserIds,
	Long studyId,
	String studyTitle
) {
}
