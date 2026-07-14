package com.groovy.backend.domain.calendar;

import java.time.LocalDate;

import com.groovy.backend.common.entity.BaseTimeEntity;
import com.groovy.backend.domain.study.Study;
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
@Table(name = "calendars")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Calendar extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// null이면 작성자 개인 일정, 값이 있으면 해당 스터디 멤버 전원에게 공유되는 스터디 약속이다.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_id")
	private Study study;

	@Column(nullable = false)
	private String title;

	// 캘린더 UI가 날짜 단위 그리드(월간 뷰)로만 일정을 다루므로 시간 없이 날짜만 저장한다.
	@Column(nullable = false)
	private LocalDate date;

	@Builder
	public Calendar(User user, Study study, String title, LocalDate date) {
		this.user = user;
		this.study = study;
		this.title = title;
		this.date = date;
	}

	public boolean isPersonal() {
		return this.study == null;
	}
}
