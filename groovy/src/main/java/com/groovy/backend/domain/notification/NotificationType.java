package com.groovy.backend.domain.notification;

public enum NotificationType {
	// 방장에게: 누군가 내 스터디에 참여 신청함
	APPLICATION_RECEIVED,
	// 신청자에게: 내 신청이 승인됨
	APPLICATION_APPROVED,
	// 신청자에게: 내 신청이 거절됨
	APPLICATION_REJECTED,
	// 스터디 멤버 전원에게: 그 스터디의 일정이 생성/수정/삭제됨
	STUDY_SCHEDULE_CHANGED,
	// 대기열 등록자 전원에게: 정원이 가득 찼던 스터디에 빈자리가 생김
	WAITLIST_SEAT_OPENED
}
