package com.groovy.backend.domain.calendar.dto;

import java.time.LocalDateTime;

import com.groovy.backend.domain.calendar.Calendar;
import com.groovy.backend.domain.calendar.CalendarSourceType;
import com.groovy.backend.domain.study.Study;

/**
 * 개인 일정(Calendar)과 스터디 공식 일정(Study)을 DB 조인 없이
 * 애플리케이션 계층에서 병합하기 위한 공통 응답 DTO.
 */
public record IntegrationCalendarResponse(
	Long sourceId,
	CalendarSourceType sourceType,
	String title,
	String description,
	LocalDateTime startTime,
	LocalDateTime endTime
) {

	public static IntegrationCalendarResponse fromPersonal(Calendar calendar) {
		return new IntegrationCalendarResponse(
			calendar.getId(),
			CalendarSourceType.PERSONAL,
			calendar.getTitle(),
			calendar.getDescription(),
			calendar.getStartTime(),
			calendar.getEndTime()
		);
	}

	public static IntegrationCalendarResponse fromStudy(Study study) {
		return new IntegrationCalendarResponse(
			study.getId(),
			CalendarSourceType.STUDY,
			study.getTitle(),
			study.getDescription(),
			study.getMeetingStartTime(),
			study.getMeetingEndTime()
		);
	}
}
