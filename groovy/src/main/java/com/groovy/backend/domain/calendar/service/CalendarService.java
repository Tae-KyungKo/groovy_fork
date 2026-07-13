package com.groovy.backend.domain.calendar.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.calendar.Calendar;
import com.groovy.backend.domain.calendar.dto.CalendarCreateRequest;
import com.groovy.backend.domain.calendar.dto.IntegrationCalendarResponse;
import com.groovy.backend.domain.calendar.repository.CalendarRepository;
import com.groovy.backend.domain.study.Application;
import com.groovy.backend.domain.study.ApplicationStatus;
import com.groovy.backend.domain.study.repository.ApplicationRepository;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

	private final CalendarRepository calendarRepository;
	private final ApplicationRepository applicationRepository;
	private final UserRepository userRepository;

	@Transactional
	public Long addSchedule(String email, CalendarCreateRequest request) {
		User user = getUser(email);

		Calendar calendar = Calendar.builder()
			.user(user)
			.title(request.title())
			.description(request.description())
			.startTime(request.startTime())
			.endTime(request.endTime())
			.build();

		return calendarRepository.save(calendar).getId();
	}

	/**
	 * Step A: 개인 일정을 조회하고, Step B: 참여 승인된 스터디의 공식 일정을 조회한 뒤,
	 * Step C: DB 조인 없이 애플리케이션(서버 메모리) 계층에서 두 목록을 병합/정렬하여 반환한다.
	 */
	public List<IntegrationCalendarResponse> getIntegratedCalendar(String email) {
		User user = getUser(email);

		List<IntegrationCalendarResponse> personalSchedules = calendarRepository.findByUserId(user.getId()).stream()
			.map(IntegrationCalendarResponse::fromPersonal)
			.toList();

		List<IntegrationCalendarResponse> studySchedules = applicationRepository
			.findByApplicantIdAndStatus(user.getId(), ApplicationStatus.APPROVED).stream()
			.map(Application::getStudy)
			.filter(study -> study.getMeetingStartTime() != null && study.getMeetingEndTime() != null)
			.map(IntegrationCalendarResponse::fromStudy)
			.toList();

		return Stream.concat(personalSchedules.stream(), studySchedules.stream())
			.sorted(Comparator.comparing(IntegrationCalendarResponse::startTime))
			.toList();
	}

	private User getUser(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
	}
}
