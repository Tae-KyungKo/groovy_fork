package com.groovy.backend.domain.calendar.dto;

import com.groovy.backend.domain.calendar.Calendar;
import com.groovy.backend.domain.calendar.CalendarSourceType;

/**
 * 개인 일정과 스터디 약속(둘 다 Calendar 엔티티)을 프론트엔드 캘린더 월간 뷰의
 * CalendarEvent 타입에 맞춰 내려주는 응답 DTO.
 */
public record CalendarEventResponse(
	String id,
	// 목록 렌더링용 합성 id(id)와 별개로, 상세조회/수정/삭제 API 경로에 쓰는 원본 PK.
	String calendarId,
	String title,
	String content,
	String startDate,
	String endDate,
	String studyId,
	String studyTitle,
	CalendarSourceType type,
	// 요청자가 이 일정을 수정/삭제할 수 있는지. 개인 일정은 작성자 본인, 스터디 일정은 방장만 true.
	boolean canManage
) {

	public static CalendarEventResponse from(Calendar calendar, Long requesterId) {
		if (calendar.isPersonal()) {
			boolean canManage = calendar.getUser().getId().equals(requesterId);
			return new CalendarEventResponse(
				"personal-" + calendar.getId(),
				String.valueOf(calendar.getId()),
				calendar.getTitle(),
				calendar.getContent(),
				calendar.getStartDate().toString(),
				calendar.getEndDate().toString(),
				null,
				null,
				CalendarSourceType.PERSONAL,
				canManage
			);
		}

		String studyId = String.valueOf(calendar.getStudy().getId());
		boolean canManage = calendar.getStudy().isLeader(requesterId);
		return new CalendarEventResponse(
			"study-" + calendar.getId(),
			String.valueOf(calendar.getId()),
			calendar.getTitle(),
			calendar.getContent(),
			calendar.getStartDate().toString(),
			calendar.getEndDate().toString(),
			studyId,
			calendar.getStudy().getTitle(),
			CalendarSourceType.STUDY,
			canManage
		);
	}
}
