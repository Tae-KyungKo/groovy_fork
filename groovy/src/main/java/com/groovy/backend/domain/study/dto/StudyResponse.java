package com.groovy.backend.domain.study.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.groovy.backend.domain.study.Study;

public record StudyResponse(
	Long id,
	String title,
	String description,
	Long ownerId,
	String ownerName,
	Integer capacity,
	Long memberCount,
	List<Long> tagIds,
	LocalDateTime meetingStartTime,
	LocalDateTime meetingEndTime,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	public static StudyResponse from(Study study, long memberCount, List<Long> tagIds) {
		return new StudyResponse(
			study.getId(),
			study.getTitle(),
			study.getDescription(),
			study.getLeader().getId(),
			study.getLeader().getName(),
			study.getCapacity(),
			memberCount,
			tagIds,
			study.getMeetingStartTime(),
			study.getMeetingEndTime(),
			study.getCreatedAt(),
			study.getUpdatedAt()
		);
	}
}
