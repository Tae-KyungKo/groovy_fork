package com.groovy.backend.domain.notification.event;

/**
 * 누군가 스터디에 참여 신청을 제출했을 때, 방장 1명에게 보낼 알림 이벤트.
 * 수신자 id를 발행 시점(원 트랜잭션 커밋 전)에 미리 채워서 담는다 — 리스너는 도메인을 모른다.
 */
public record ApplicationSubmittedEvent(
	Long recipientUserId,
	String applicantName,
	Long studyId,
	String studyTitle
) {
}
