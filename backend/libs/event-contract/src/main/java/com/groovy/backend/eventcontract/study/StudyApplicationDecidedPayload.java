package com.groovy.backend.eventcontract.study;

/**
 * study-service 발행, notification-service 소비. groovy(레거시)의 ApplicationDecidedEvent와 대응.
 * eventType은 approved 값에 따라 STUDY_APPLICATION_APPROVED/REJECTED로 나뉜다(payload 구조는 동일).
 */
public record StudyApplicationDecidedPayload(
	Long recipientUserId,
	boolean approved,
	Long studyId,
	String studyTitle
) {
}
