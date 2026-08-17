package com.groovy.backend.eventcontract.calendar;

import java.util.List;

/**
 * calendar-service 발행, notification-service 소비. groovy(레거시)의 StudyScheduleChangedEvent와 대응.
 * changeType은 "CREATED"/"UPDATED"/"DELETED" 중 하나(레거시의 ChangeType enum과 동일한 값).
 */
public record StudyScheduleChangedPayload(
	List<Long> recipientUserIds,
	Long studyId,
	String studyTitle,
	String scheduleTitle,
	String changeType
) {
}
