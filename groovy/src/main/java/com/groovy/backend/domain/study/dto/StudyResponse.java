package com.groovy.backend.domain.study.dto;

import java.time.LocalDateTime;

import com.groovy.backend.domain.study.Study;

public record StudyResponse(
	Long id,
	String title,
	String description,
	Long leaderId,
	String leaderName,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	public static StudyResponse from(Study study) {
		return new StudyResponse(
			study.getId(),
			study.getTitle(),
			study.getDescription(),
			study.getLeader().getId(),
			study.getLeader().getName(),
			study.getCreatedAt(),
			study.getUpdatedAt()
		);
	}
}
