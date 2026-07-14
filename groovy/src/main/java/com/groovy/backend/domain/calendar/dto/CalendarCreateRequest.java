package com.groovy.backend.domain.calendar.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CalendarCreateRequest(

	@NotBlank(message = "일정 제목은 필수입니다.")
	String title,

	@NotNull(message = "일정 날짜는 필수입니다.")
	LocalDate date
) {
}
