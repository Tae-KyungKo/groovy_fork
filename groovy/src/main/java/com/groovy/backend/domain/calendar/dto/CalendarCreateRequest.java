package com.groovy.backend.domain.calendar.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CalendarCreateRequest(

	@NotBlank(message = "일정 제목은 필수입니다.")
	String title,

	String description,

	@NotNull(message = "일정 시작 시간은 필수입니다.")
	LocalDateTime startTime,

	@NotNull(message = "일정 종료 시간은 필수입니다.")
	LocalDateTime endTime
) {
}
