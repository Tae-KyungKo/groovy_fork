package com.groovy.backend.domain.calendar.dto;

import com.groovy.backend.domain.calendar.Calendar;
import com.groovy.backend.domain.calendar.CalendarSourceType;

/**
 * 개인 일정과 스터디 약속(둘 다 Calendar 엔티티)을 프론트엔드 캘린더 월간 뷰의
 * CalendarEvent 타입에 맞춰 내려주는 응답 DTO.
 */
public record CalendarEventResponse(
	String id,
	String title,
	String date,
	String studyId,
	String studyTitle,
	CalendarSourceType type
) {

	public static CalendarEventResponse from(Calendar calendar) {
		if (calendar.isPersonal()) {
			return new CalendarEventResponse(
				"personal-" + calendar.getId(),
				calendar.getTitle(),
				calendar.getDate().toString(),
				null,
				null,
				CalendarSourceType.PERSONAL
			);
		}

		String studyId = String.valueOf(calendar.getStudy().getId());
		return new CalendarEventResponse(
			"study-" + calendar.getId(),
			calendar.getTitle(),
			calendar.getDate().toString(),
			studyId,
			calendar.getStudy().getTitle(),
			CalendarSourceType.STUDY
		);
	}
}
