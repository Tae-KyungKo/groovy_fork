package com.groovy.backend.domain.study.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.groovy.backend.domain.study.MeetingDay;
import com.groovy.backend.domain.study.Study;

public record StudyResponse(
	String id,
	String title,
	String description,
	String leaderId,
	String leaderName,
	Integer capacity,
	Long memberCount,
	List<Long> tagIds,
	List<MeetingDay> meetingDays,
	LocalTime meetingStartTime,
	LocalTime meetingEndTime,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	public static StudyResponse from(Study study, long memberCount, List<Long> tagIds) {
		return new StudyResponse(
			String.valueOf(study.getId()),
			study.getTitle(),
			study.getDescription(),
			String.valueOf(study.getLeader().getId()),
			study.getLeader().getName(),
			study.getCapacity(),
			memberCount,
			tagIds,
			study.getMeetingDays().stream().sorted().toList(),
			study.getMeetingStartTime(),
			study.getMeetingEndTime(),
			study.getCreatedAt(),
			study.getUpdatedAt()
		);
	}
}
