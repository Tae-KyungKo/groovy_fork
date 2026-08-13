package com.groovy.backend.domain.notification.event;

import java.util.List;

/** 정원이 가득 찼던 스터디에 빈자리가 생겼을 때, 대기열 등록자 전원에게 보낼 알림 이벤트. */
public record WaitlistSeatOpenedEvent(
	List<Long> recipientUserIds,
	Long studyId,
	String studyTitle
) {
}
