package com.groovy.backend.domain.notification.event;

import java.util.List;

/** 개인 일정을 제외한 스터디 일정이 생성/수정/삭제됐을 때, 그 스터디 멤버 전원(행위자 본인 제외)에게 보낼 알림 이벤트. */
public record StudyScheduleChangedEvent(
	List<Long> recipientUserIds,
	Long studyId,
	String studyTitle,
	String scheduleTitle,
	ChangeType changeType
) {
	public enum ChangeType {
		CREATED, UPDATED, DELETED
	}
}
