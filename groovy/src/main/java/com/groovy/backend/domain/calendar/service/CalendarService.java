package com.groovy.backend.domain.calendar.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.calendar.Calendar;
import com.groovy.backend.domain.calendar.dto.CalendarCreateRequest;
import com.groovy.backend.domain.calendar.dto.CalendarEventResponse;
import com.groovy.backend.domain.calendar.dto.MyStudyOptionResponse;
import com.groovy.backend.domain.calendar.repository.CalendarRepository;
import com.groovy.backend.domain.study.ApplicationStatus;
import com.groovy.backend.domain.study.Study;
import com.groovy.backend.domain.study.repository.ApplicationRepository;
import com.groovy.backend.domain.study.repository.StudyRepository;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.repository.UserRepository;
import com.groovy.backend.global.exception.ForbiddenException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

	private final CalendarRepository calendarRepository;
	private final ApplicationRepository applicationRepository;
	private final StudyRepository studyRepository;
	private final UserRepository userRepository;

	@Transactional
	public CalendarEventResponse addSchedule(String email, CalendarCreateRequest request) {
		User user = getUser(email);
		Study study = resolveStudyIfPresent(user, request.studyId());

		Calendar calendar = Calendar.builder()
			.user(user)
			.study(study)
			.title(request.title())
			.date(request.date())
			.build();

		return CalendarEventResponse.from(calendarRepository.save(calendar));
	}

	/**
	 * Step A: 개인 일정을 조회하고, Step B: 내가 속한(방장이거나 승인된) 스터디들의 약속을 조회한 뒤,
	 * Step C: 두 목록을 병합/정렬하여 반환한다.
	 */
	public List<CalendarEventResponse> getIntegratedCalendar(String email) {
		User user = getUser(email);

		List<Calendar> personalSchedules = calendarRepository.findByUserIdAndStudyIsNull(user.getId());

		List<Long> myStudyIds = getMyStudies(user).stream().map(Study::getId).toList();
		List<Calendar> studySchedules = myStudyIds.isEmpty()
			? List.of()
			: calendarRepository.findByStudyIdIn(myStudyIds);

		return Stream.concat(personalSchedules.stream(), studySchedules.stream())
			.map(CalendarEventResponse::from)
			.sorted(Comparator.comparing(CalendarEventResponse::date))
			.toList();
	}

	public List<MyStudyOptionResponse> getMyStudyOptions(String email) {
		User user = getUser(email);
		return getMyStudies(user).stream()
			.map(MyStudyOptionResponse::from)
			.toList();
	}

	private Study resolveStudyIfPresent(User user, Long studyId) {
		if (studyId == null) {
			return null;
		}

		Study study = studyRepository.findById(studyId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 스터디입니다."));

		boolean isMember = study.isLeader(user.getId())
			|| applicationRepository.existsByStudyIdAndApplicantIdAndStatus(studyId, user.getId(), ApplicationStatus.APPROVED);
		if (!isMember) {
			throw new ForbiddenException("스터디 멤버만 약속을 등록할 수 있습니다.");
		}

		return study;
	}

	/**
	 * 내가 방장인 스터디와, 참여 신청이 승인된 스터디를 id 기준 중복 없이 합쳐 반환한다.
	 */
	private List<Study> getMyStudies(User user) {
		Map<Long, Study> studiesById = new LinkedHashMap<>();

		studyRepository.findByLeaderId(user.getId())
			.forEach(study -> studiesById.put(study.getId(), study));

		applicationRepository.findByApplicantIdAndStatus(user.getId(), ApplicationStatus.APPROVED).stream()
			.map(application -> application.getStudy())
			.forEach(study -> studiesById.put(study.getId(), study));

		return List.copyOf(studiesById.values());
	}

	private User getUser(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
	}
}
