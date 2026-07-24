package com.groovy.backend.domain.study.dto;

import java.time.LocalDateTime;

import com.groovy.backend.domain.study.Application;
import com.groovy.backend.domain.study.ApplicationStatus;

public record ApplicationResponse(
	String id,
	String studyId,
	String userId,
	String userName,
	ApplicationStatus status,
	LocalDateTime appliedAt
) {

	public static ApplicationResponse from(Application application) {
		return new ApplicationResponse(
			String.valueOf(application.getId()),
			String.valueOf(application.getStudy().getId()),
			String.valueOf(application.getApplicant().getId()),
			application.getApplicant().getName(),
			application.getStatus(),
			application.getCreatedAt()
		);
	}
}
