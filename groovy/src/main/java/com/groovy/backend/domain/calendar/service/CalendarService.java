package com.groovy.backend.domain.calendar.service;

import java.time.LocalDate;
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
import com.groovy.backend.domain.calendar.dto.CalendarUpdateRequest;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

		LocalDate startDate = request.startDate();
		LocalDate endDate = request.endDate() != null ? request.endDate() : startDate;
		validatePeriod(startDate, endDate);

		Calendar calendar = Calendar.builder()
			.user(user)
			.study(study)
			.title(request.title())
			.content(request.content())
			.startDate(startDate)
			.endDate(endDate)
			.build();

		Calendar saved = calendarRepository.save(calendar);
		log.info("일정 등록: email={}, studyId={}, calendarId={}", email, request.studyId(), saved.getId());

		return CalendarEventResponse.from(saved, user.getId());
	}

	public CalendarEventResponse getSchedule(String email, Long id) {
		User user = getUser(email);
		Calendar calendar = findCalendarOrThrow(id);
		assertViewable(user, calendar);
		return CalendarEventResponse.from(calendar, user.getId());
	}

	@Transactional
	public CalendarEventResponse updateSchedule(String email, Long id, CalendarUpdateRequest request) {
		User user = getUser(email);
		Calendar calendar = findCalendarOrThrow(id);
		assertManageable(user, calendar);

		LocalDate startDate = request.startDate();
		LocalDate endDate = request.endDate() != null ? request.endDate() : startDate;
		validatePeriod(startDate, endDate);

		calendar.update(request.title(), request.content(), startDate, endDate);
		log.info("일정 수정: email={}, calendarId={}", email, id);

		return CalendarEventResponse.from(calendar, user.getId());
	}

	@Transactional
	public void deleteSchedule(String email, Long id) {
		User user = getUser(email);
		Calendar calendar = findCalendarOrThrow(id);
		assertManageable(user, calendar);

		calendarRepository.delete(calendar);
		log.info("일정 삭제: email={}, calendarId={}", email, id);
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
			.map(calendar -> CalendarEventResponse.from(calendar, user.getId()))
			.sorted(Comparator.comparing(CalendarEventResponse::startDate))
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
			.orElseThrow(() -> {
				log.warn("존재하지 않는 스터디: studyId={}", studyId);
				return new IllegalArgumentException("존재하지 않는 스터디입니다.");
			});

		boolean isMember = study.isLeader(user.getId())
			|| applicationRepository.existsByStudyIdAndApplicantIdAndStatus(studyId, user.getId(), ApplicationStatus.APPROVED);
		if (!isMember) {
			log.warn("스터디 멤버 아님: studyId={}, userId={}", studyId, user.getId());
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
			.orElseThrow(() -> {
				log.warn("존재하지 않는 유저: email={}", email);
				return new IllegalArgumentException("존재하지 않는 유저입니다.");
			});
	}

	private Calendar findCalendarOrThrow(Long id) {
		return calendarRepository.findById(id)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 일정: calendarId={}", id);
				return new IllegalArgumentException("존재하지 않는 일정입니다.");
			});
	}

	// 개인 일정은 작성자 본인만, 스터디 일정은 방장이거나 승인된 멤버만 조회할 수 있다.
	private void assertViewable(User user, Calendar calendar) {
		if (calendar.isPersonal()) {
			if (!calendar.getUser().getId().equals(user.getId())) {
				log.warn("개인 일정 조회 권한 없음: userId={}, calendarId={}", user.getId(), calendar.getId());
				throw new ForbiddenException("본인의 개인 일정만 조회할 수 있습니다.");
			}
			return;
		}

		Long studyId = calendar.getStudy().getId();
		boolean isMember = calendar.getStudy().isLeader(user.getId())
			|| applicationRepository.existsByStudyIdAndApplicantIdAndStatus(studyId, user.getId(), ApplicationStatus.APPROVED);
		if (!isMember) {
			log.warn("스터디 일정 조회 권한 없음: userId={}, studyId={}", user.getId(), studyId);
			throw new ForbiddenException("스터디 멤버만 그룹 일정을 조회할 수 있습니다.");
		}
	}

	// 개인 일정은 작성자 본인만, 스터디 일정은 방장만 수정/삭제할 수 있다(작성자가 누구든 무관).
	private void assertManageable(User user, Calendar calendar) {
		if (calendar.isPersonal()) {
			if (!calendar.getUser().getId().equals(user.getId())) {
				log.warn("개인 일정 수정/삭제 권한 없음: userId={}, calendarId={}", user.getId(), calendar.getId());
				throw new ForbiddenException("본인의 개인 일정만 수정/삭제할 수 있습니다.");
			}
			return;
		}

		if (!calendar.getStudy().isLeader(user.getId())) {
			log.warn("스터디 일정 수정/삭제 권한 없음: userId={}, studyId={}", user.getId(), calendar.getStudy().getId());
			throw new ForbiddenException("스터디 방장만 그룹 일정을 수정/삭제할 수 있습니다.");
		}
	}

	private void validatePeriod(LocalDate startDate, LocalDate endDate) {
		if (endDate.isBefore(startDate)) {
			log.warn("잘못된 일정 기간: startDate={}, endDate={}", startDate, endDate);
			throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
		}
	}
}
