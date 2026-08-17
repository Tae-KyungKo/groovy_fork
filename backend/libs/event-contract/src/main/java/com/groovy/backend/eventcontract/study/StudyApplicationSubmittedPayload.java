package com.groovy.backend.eventcontract.study;

/** study-service 발행, notification-service 소비. groovy(레거시)의 ApplicationSubmittedEvent와 대응. */
public record StudyApplicationSubmittedPayload(
	Long recipientUserId,
	String applicantName,
	Long studyId,
	String studyTitle
) {
}
