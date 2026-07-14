package com.groovy.backend.domain.calendar.dto;

import com.groovy.backend.domain.calendar.Calendar;
import com.groovy.backend.domain.calendar.CalendarSourceType;
import com.groovy.backend.domain.study.Study;

/**
 * 개인 일정(Calendar)과 스터디 공식 일정(Study)을 DB 조인 없이
 * 애플리케이션 계층에서 병합하기 위한 공통 응답 DTO. 프론트엔드 캘린더 월간 뷰의
 * CalendarEvent 타입과 필드를 맞춘다.
 */
public record CalendarEventResponse(
	String id,
	String title,
	String date,
	String studyId,
	String studyTitle,
	CalendarSourceType type
) {

	public static CalendarEventResponse fromPersonal(Calendar calendar) {
		return new CalendarEventResponse(
			"personal-" + calendar.getId(),
			calendar.getTitle(),
			calendar.getDate().toString(),
			null,
			null,
			CalendarSourceType.PERSONAL
		);
	}

	public static CalendarEventResponse fromStudy(Study study) {
		String studyId = String.valueOf(study.getId());
		return new CalendarEventResponse(
			"study-" + studyId,
			study.getTitle(),
			study.getMeetingStartTime().toLocalDate().toString(),
			studyId,
			study.getTitle(),
			CalendarSourceType.STUDY
		);
	}
}
