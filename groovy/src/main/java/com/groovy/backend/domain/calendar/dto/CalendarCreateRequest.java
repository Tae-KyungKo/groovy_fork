package com.groovy.backend.domain.calendar.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CalendarCreateRequest(

	@NotBlank(message = "일정 제목은 필수입니다.")
	String title,

	@NotNull(message = "일정 날짜는 필수입니다.")
	LocalDate date,

	// null이면 개인 일정, 값이 있으면 해당 스터디 멤버 전원과 공유되는 스터디 약속으로 등록된다.
	Long studyId
) {
}
