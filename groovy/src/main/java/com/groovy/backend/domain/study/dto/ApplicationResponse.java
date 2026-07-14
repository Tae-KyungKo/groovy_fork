package com.groovy.backend.domain.study.dto;

import java.time.LocalDateTime;

import com.groovy.backend.domain.study.Application;
import com.groovy.backend.domain.study.ApplicationStatus;

public record ApplicationResponse(
	Long id,
	Long studyId,
	Long applicantId,
	String applicantName,
	ApplicationStatus status,
	LocalDateTime createdAt
) {

	public static ApplicationResponse from(Application application) {
		return new ApplicationResponse(
			application.getId(),
			application.getStudy().getId(),
			application.getApplicant().getId(),
			application.getApplicant().getName(),
			application.getStatus(),
			application.getCreatedAt()
		);
	}
}
