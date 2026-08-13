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
	Integer level,
	Integer expPoint,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	// 목록/매칭 등 다건 조회에서는 항상 기본값("NONE"/false)이고, 상세조회(getStudy)에서만 실제 값이 채워진다.
	// 다건 조회 경로에서 유저별로 계산하면 페이지당 N번 조회하는 N+1이 되므로 절대 붙이지 않는다.
	String myApplicationStatus,
	boolean myWaitlistRegistered
) {

	public static StudyResponse from(Study study, long memberCount, List<Long> tagIds) {
		return from(study, memberCount, tagIds, "NONE", false);
	}

	public static StudyResponse from(
		Study study,
		long memberCount,
		List<Long> tagIds,
		String myApplicationStatus,
		boolean myWaitlistRegistered
	) {
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
			study.getLevel(),
			study.getExpPoint(),
			study.getCreatedAt(),
			study.getUpdatedAt(),
			myApplicationStatus,
			myWaitlistRegistered
		);
	}
}
