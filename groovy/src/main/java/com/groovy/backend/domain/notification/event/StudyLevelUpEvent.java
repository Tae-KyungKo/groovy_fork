package com.groovy.backend.domain.notification.event;

import java.util.List;

/** 스터디가 레벨업했을 때, 그 스터디 멤버 전원(방장 포함)에게 보낼 알림 이벤트. */
public record StudyLevelUpEvent(
	List<Long> recipientUserIds,
	Long studyId,
	String studyTitle,
	int newLevel
) {
}
