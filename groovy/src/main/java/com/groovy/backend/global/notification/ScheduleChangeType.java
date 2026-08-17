package com.groovy.backend.global.notification;

// 예전 domain.notification.event.StudyScheduleChangedEvent.ChangeType을 대체한다.
// Phase 9에서 그 이벤트 클래스 자체(Spring ApplicationEvent)를 없앴지만, CalendarService가
// 일정 변경 종류를 표현할 값 타입은 계속 필요해 여기로 옮겼다.
public enum ScheduleChangeType {
	CREATED, UPDATED, DELETED
}
