package com.groovy.backend.domain.study.dto;

import jakarta.validation.constraints.NotBlank;

public record StudyCreateRequest(

	@NotBlank(message = "스터디 제목은 필수입니다.")
	String title,

	@NotBlank(message = "스터디 설명은 필수입니다.")
	String description
) {
}
