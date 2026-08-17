package com.groovy.backend.domain.calendar.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.calendar.Calendar;
import com.groovy.backend.domain.calendar.dto.CalendarCreateRequest;
import com.groovy.backend.domain.calendar.dto.CalendarEventResponse;
import com.groovy.backend.domain.calendar.dto.CalendarUpdateRequest;
import com.groovy.backend.domain.calendar.dto.MyStudyOptionResponse;
import com.groovy.backend.domain.calendar.repository.CalendarRepository;
import com.groovy.backend.domain.study.Study;
import com.groovy.backend.domain.study.service.ApplicationService;
import com.groovy.backend.domain.study.service.StudyService;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.service.UserService;
import com.groovy.backend.global.exception.ForbiddenException;
import com.groovy.backend.global.notification.NotificationOutboxPublisher;
import com.groovy.backend.global.notification.ScheduleChangeType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

	private final CalendarRepository calendarRepository;
	private final StudyService studyService;
	private final ApplicationService applicationService;
	private final UserService userService;
	private final NotificationOutboxPublisher notificationOutboxPublisher;

	@Transactional
	public CalendarEventResponse addSchedule(String email, CalendarCreateRequest request) {
		User user = getUser(email);
		Study study = request.studyId() != null ? requireMembership(user, request.studyId()) : null;

		LocalDate startDate = request.startDate();
		LocalDate endDate = request.endDate() != null ? request.endDate() : startDate;
		validatePeriod(startDate, endDate);

		Calendar calendar = Calendar.builder()
			.user(user)
			.studyId(study != null ? study.getId() : null)
			.title(request.title())
			.content(request.content())
			.startDate(startDate)
			.endDate(endDate)
			.build();

		Calendar saved = calendarRepository.save(calendar);
		log.info("일정 등록: email={}, studyId={}, calendarId={}", email, request.studyId(), saved.getId());

		if (study == null) {
			return CalendarEventResponse.forPersonal(saved, user.getId());
		}

		notifyStudyMembers(study, user.getId(), saved.getTitle(), ScheduleChangeType.CREATED);
		return CalendarEventResponse.forStudy(saved, study.getTitle(), study.isLeader(user.getId()));
	}

	public CalendarEventResponse getSchedule(String email, Long id) {
		User user = getUser(email);
		Calendar calendar = findCalendarOrThrow(id);

		if (calendar.isPersonal()) {
			assertOwnsPersonal(user, calendar);
			return CalendarEventResponse.forPersonal(calendar, user.getId());
		}

		Study study = studyService.getStudyEntity(calendar.getStudyId());
		assertStudyMember(user, study);
		return CalendarEventResponse.forStudy(calendar, study.getTitle(), study.isLeader(user.getId()));
	}

	@Transactional
	public CalendarEventResponse updateSchedule(String email, Long id, CalendarUpdateRequest request) {
		User user = getUser(email);
		Calendar calendar = findCalendarOrThrow(id);
		Study study = calendar.isPersonal() ? null : studyService.getStudyEntity(calendar.getStudyId());
		assertManageable(user, calendar, study);

		LocalDate startDate = request.startDate();
		LocalDate endDate = request.endDate() != null ? request.endDate() : startDate;
		validatePeriod(startDate, endDate);

		calendar.update(request.title(), request.content(), startDate, endDate);
		log.info("일정 수정: email={}, calendarId={}", email, id);

		if (study == null) {
			return CalendarEventResponse.forPersonal(calendar, user.getId());
		}

		notifyStudyMembers(study, user.getId(), calendar.getTitle(), ScheduleChangeType.UPDATED);
		return CalendarEventResponse.forStudy(calendar, study.getTitle(), study.isLeader(user.getId()));
	}

	@Transactional
	public void deleteSchedule(String email, Long id) {
		User user = getUser(email);
		Calendar calendar = findCalendarOrThrow(id);
		Study study = calendar.isPersonal() ? null : studyService.getStudyEntity(calendar.getStudyId());
		assertManageable(user, calendar, study);

		String title = calendar.getTitle();
		calendarRepository.delete(calendar);
		log.info("일정 삭제: email={}, calendarId={}", email, id);

		if (study != null) {
			notifyStudyMembers(study, user.getId(), title, ScheduleChangeType.DELETED);
		}
	}

	/**
	 * Step A: 개인 일정을 조회하고, Step B: 내가 속한(방장이거나 승인된) 스터디들의 약속을 조회한 뒤,
	 * Step C: 두 목록을 병합/정렬하여 반환한다.
	 */
	public List<CalendarEventResponse> getIntegratedCalendar(String email) {
		User user = getUser(email);

		List<Calendar> personalSchedules = calendarRepository.findByUserIdAndStudyIdIsNull(user.getId());

		Map<Long, Study> myStudyById = getMyStudies(user).stream()
			.collect(Collectors.toMap(Study::getId, study -> study));
		List<Calendar> studySchedules = myStudyById.isEmpty()
			? List.of()
			: calendarRepository.findByStudyIdIn(List.copyOf(myStudyById.keySet()));

		Stream<CalendarEventResponse> personalEvents = personalSchedules.stream()
			.map(calendar -> CalendarEventResponse.forPersonal(calendar, user.getId()));
		Stream<CalendarEventResponse> studyEvents = studySchedules.stream()
			.map(calendar -> {
				Study study = myStudyById.get(calendar.getStudyId());
				return CalendarEventResponse.forStudy(calendar, study.getTitle(), study.isLeader(user.getId()));
			});

		return Stream.concat(personalEvents, studyEvents)
			.sorted(Comparator.comparing(CalendarEventResponse::startDate))
			.toList();
	}

	public List<MyStudyOptionResponse> getMyStudyOptions(String email) {
		User user = getUser(email);
		return getMyStudies(user).stream()
			.map(MyStudyOptionResponse::from)
			.toList();
	}

	private Study requireMembership(User user, Long studyId) {
		Study study = studyService.getStudyEntity(studyId);

		boolean isMember = study.isLeader(user.getId())
			|| applicationService.isApprovedMember(studyId, user.getId());
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

		studyService.getStudiesLedBy(user.getId())
			.forEach(study -> studiesById.put(study.getId(), study));

		applicationService.getApprovedStudies(user.getId())
			.forEach(study -> studiesById.put(study.getId(), study));

		return List.copyOf(studiesById.values());
	}

	private User getUser(String email) {
		return userService.findByEmail(email)
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

	// 개인 일정은 작성자 본인만 조회할 수 있다.
	private void assertOwnsPersonal(User user, Calendar calendar) {
		if (!calendar.getUser().getId().equals(user.getId())) {
			log.warn("개인 일정 조회 권한 없음: userId={}, calendarId={}", user.getId(), calendar.getId());
			throw new ForbiddenException("본인의 개인 일정만 조회할 수 있습니다.");
		}
	}

	// 스터디 일정은 방장이거나 승인된 멤버만 조회할 수 있다.
	private void assertStudyMember(User user, Study study) {
		boolean isMember = study.isLeader(user.getId())
			|| applicationService.isApprovedMember(study.getId(), user.getId());
		if (!isMember) {
			log.warn("스터디 일정 조회 권한 없음: userId={}, studyId={}", user.getId(), study.getId());
			throw new ForbiddenException("스터디 멤버만 그룹 일정을 조회할 수 있습니다.");
		}
	}

	// 개인 일정은 작성자 본인만, 스터디 일정은 방장만 수정/삭제할 수 있다(작성자가 누구든 무관).
	private void assertManageable(User user, Calendar calendar, Study study) {
		if (calendar.isPersonal()) {
			if (!calendar.getUser().getId().equals(user.getId())) {
				log.warn("개인 일정 수정/삭제 권한 없음: userId={}, calendarId={}", user.getId(), calendar.getId());
				throw new ForbiddenException("본인의 개인 일정만 수정/삭제할 수 있습니다.");
			}
			return;
		}

		if (!study.isLeader(user.getId())) {
			log.warn("스터디 일정 수정/삭제 권한 없음: userId={}, studyId={}", user.getId(), study.getId());
			throw new ForbiddenException("스터디 방장만 그룹 일정을 수정/삭제할 수 있습니다.");
		}
	}

	// 방장 + 승인된 멤버 전원(행위자 본인 제외)에게 스터디 일정 변경을 알린다.
	private void notifyStudyMembers(Study study, Long actorUserId, String scheduleTitle, ScheduleChangeType changeType) {
		List<Long> recipientUserIds = Stream.concat(
				Stream.of(study.getLeader().getId()),
				applicationService.getApprovedMemberUserIds(study.getId()).stream()
			)
			.filter(userId -> !userId.equals(actorUserId))
			.distinct()
			.toList();

		if (recipientUserIds.isEmpty()) {
			return;
		}

		notificationOutboxPublisher.studyScheduleChanged(
			recipientUserIds, study.getId(), study.getTitle(), scheduleTitle, changeType);
	}

	private void validatePeriod(LocalDate startDate, LocalDate endDate) {
		if (endDate.isBefore(startDate)) {
			log.warn("잘못된 일정 기간: startDate={}, endDate={}", startDate, endDate);
			throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
		}
	}
}
