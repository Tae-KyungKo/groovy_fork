package com.groovy.backend.domain.study;

import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import com.groovy.backend.common.entity.BaseTimeEntity;
import com.groovy.backend.domain.user.User;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	// 회고록/댓글 작성 시 이 값만큼 경험치가 누적되고, EXP_PER_LEVEL마다 레벨이 오른다.
	private static final int EXP_PER_LEVEL = 100;

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

	@Column(nullable = false)
	private Integer capacity;

	// 요일 반복 일정: 프론트가 <input type="time">으로 다루는 시:분만 저장하므로 날짜 없는 LocalTime을 쓴다.
	// EAGER 컬렉션은 fetch join 없이 로딩되면 엔티티 건수만큼 개별 SELECT가 발생하므로(N+1),
	// SUBSELECT로 전환해 부모 조회 결과를 서브쿼리로 묶어 한 번에 채운다.
	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SUBSELECT)
	@CollectionTable(name = "study_meeting_days", joinColumns = @JoinColumn(name = "study_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false)
	private Set<MeetingDay> meetingDays = new LinkedHashSet<>();

	@Column
	private LocalTime meetingStartTime;

	@Column
	private LocalTime meetingEndTime;

	@Column(nullable = false)
	private Integer level = 1;

	@Column(name = "exp_point", nullable = false)
	private Integer expPoint = 0;

	@Builder
	public Study(String title, String description, User leader, Integer capacity, Set<MeetingDay> meetingDays, LocalTime meetingStartTime, LocalTime meetingEndTime) {
		this.title = title;
		this.description = description;
		this.leader = leader;
		this.capacity = capacity;
		this.meetingDays = meetingDays != null ? new LinkedHashSet<>(meetingDays) : new LinkedHashSet<>();
		this.meetingStartTime = meetingStartTime;
		this.meetingEndTime = meetingEndTime;
	}

	public void update(String title, String description, Integer capacity, Set<MeetingDay> meetingDays, LocalTime meetingStartTime, LocalTime meetingEndTime) {
		this.title = title;
		this.description = description;
		this.capacity = capacity;
		this.meetingDays.clear();
		if (meetingDays != null) {
			this.meetingDays.addAll(meetingDays);
		}
		this.meetingStartTime = meetingStartTime;
		this.meetingEndTime = meetingEndTime;
	}

	public boolean isLeader(Long userId) {
		return this.leader.getId().equals(userId);
	}

	public void addExp(int amount) {
		this.expPoint += amount;
		this.level = this.expPoint / EXP_PER_LEVEL + 1;
	}
}
