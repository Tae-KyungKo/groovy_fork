package com.groovy.backend.eventcontract.study;

import java.util.List;

/** study-service 발행, notification-service 소비. groovy(레거시)의 StudyLevelUpEvent와 대응. */
public record StudyLevelUpPayload(
	List<Long> recipientUserIds,
	Long studyId,
	String studyTitle,
	int newLevel
) {
}
