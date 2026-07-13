package com.groovy.backend.domain.study;

import java.time.LocalDateTime;

import com.groovy.backend.common.entity.BaseTimeEntity;
import com.groovy.backend.domain.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "studies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Study extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "leader_id", nullable = false)
	private User leader;

	// 스터디 공식 일정(캘린더 통합 조회용). 아직 별도 일정 도메인이 없어 임시로 Study에 보관한다.
	@Column
	private LocalDateTime meetingStartTime;

	@Column
	private LocalDateTime meetingEndTime;

	@Builder
	public Study(String title, String description, User leader, LocalDateTime meetingStartTime, LocalDateTime meetingEndTime) {
		this.title = title;
		this.description = description;
		this.leader = leader;
		this.meetingStartTime = meetingStartTime;
		this.meetingEndTime = meetingEndTime;
	}

	public void update(String title, String description, LocalDateTime meetingStartTime, LocalDateTime meetingEndTime) {
		this.title = title;
		this.description = description;
		this.meetingStartTime = meetingStartTime;
		this.meetingEndTime = meetingEndTime;
	}

	public boolean isLeader(Long userId) {
		return this.leader.getId().equals(userId);
	}
}
