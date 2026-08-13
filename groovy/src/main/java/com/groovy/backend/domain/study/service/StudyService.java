package com.groovy.backend.domain.study.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.notification.event.StudyLevelUpEvent;
import com.groovy.backend.domain.notification.event.WaitlistSeatOpenedEvent;
import com.groovy.backend.domain.study.ApplicationStatus;
import com.groovy.backend.domain.study.Study;
import com.groovy.backend.domain.study.dto.StudyCreateRequest;
import com.groovy.backend.domain.study.dto.StudyMatchResponse;
import com.groovy.backend.domain.study.dto.StudyResponse;
import com.groovy.backend.domain.study.dto.StudyUpdateRequest;
import com.groovy.backend.domain.study.repository.ApplicationRepository;
import com.groovy.backend.domain.study.repository.ApplicationRepository.StudyMemberCount;
import com.groovy.backend.domain.study.repository.StudyRepository;
import com.groovy.backend.domain.tag.repository.StudyTagRepository.StudyMatchCount;
import com.groovy.backend.domain.tag.service.TagService;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.repository.UserRepository;
import com.groovy.backend.global.exception.ForbiddenException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyService {

	private static final String ANONYMOUS_PRINCIPAL = "anonymousUser";

	private final StudyRepository studyRepository;
	private final ApplicationRepository applicationRepository;
	private final UserRepository userRepository;
	private final TagService tagService;
	private final WaitlistService waitlistService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public StudyResponse createStudy(String leaderEmail, StudyCreateRequest request) {
		User leader = getUser(leaderEmail);

		Study study = Study.builder()
			.title(request.title())
			.description(request.description())
			.leader(leader)
			.capacity(request.capacity())
			.meetingDays(request.meetingDays())
			.meetingStartTime(request.meetingStartTime())
			.meetingEndTime(request.meetingEndTime())
			.build();

		Study savedStudy = studyRepository.save(study);
		tagService.replaceStudyTags(savedStudy, request.tagIds());
		log.info("스터디 생성: studyId={}, leaderId={}", savedStudy.getId(), leader.getId());

		// 스터디장은 신청 없이 항상 멤버로 집계되므로, 생성 직후 멤버 수는 1명이다.
		return StudyResponse.from(savedStudy, 1L, request.tagIds());
	}

	public Page<StudyResponse> getStudies(Pageable pageable) {
		Page<Study> studies = studyRepository.findAll(pageable);
		List<Long> studyIds = studies.getContent().stream().map(Study::getId).toList();

		Map<Long, List<Long>> tagIdsByStudyId = tagService.getStudyTagIdsGroupedByStudyIds(studyIds);
		Map<Long, Long> approvedMemberCountByStudyId = getApprovedMemberCounts(studyIds);

		return studies.map(study -> StudyResponse.from(
			study,
			resolveMemberCount(approvedMemberCountByStudyId, study.getId()),
			tagIdsByStudyId.getOrDefault(study.getId(), List.of())
		));
	}

	// 마이페이지 "내가 만든 스터디" 목록. 내가 방장인 스터디만 조회한다.
	public List<StudyResponse> getMyStudies(String email) {
		User user = getUser(email);
		List<Study> myStudies = studyRepository.findByLeaderId(user.getId());
		List<Long> studyIds = myStudies.stream().map(Study::getId).toList();

		Map<Long, List<Long>> tagIdsByStudyId = tagService.getStudyTagIdsGroupedByStudyIds(studyIds);
		Map<Long, Long> approvedMemberCountByStudyId = getApprovedMemberCounts(studyIds);

		return myStudies.stream()
			.map(study -> StudyResponse.from(
				study,
				resolveMemberCount(approvedMemberCountByStudyId, study.getId()),
				tagIdsByStudyId.getOrDefault(study.getId(), List.of())
			))
			.toList();
	}

	// 비로그인(permitAll)으로도 접근 가능한 엔드포인트라 email이 null이거나 Spring Security의
	// 익명 필터가 채우는 "anonymousUser"일 수 있다 — 이 경우 내 신청/대기열 상태는 전부 기본값으로 둔다.
	public StudyResponse getStudy(String email, Long studyId) {
		Study study = getStudyEntity(studyId);
		long memberCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED) + 1;
		List<Long> tagIds = tagService.getStudyTagIds(studyId);

		if (email == null || ANONYMOUS_PRINCIPAL.equals(email)) {
			return StudyResponse.from(study, memberCount, tagIds, "NONE", false);
		}

		User viewer = getUser(email);
		String myApplicationStatus = resolveMyApplicationStatus(study, viewer);
		boolean myWaitlistRegistered = waitlistService.isRegistered(studyId, viewer.getId());
		return StudyResponse.from(study, memberCount, tagIds, myApplicationStatus, myWaitlistRegistered);
	}

	private String resolveMyApplicationStatus(Study study, User viewer) {
		if (study.isLeader(viewer.getId())) {
			return "APPROVED";
		}
		return applicationRepository.findByStudyIdAndApplicantId(study.getId(), viewer.getId())
			.map(application -> application.getStatus().name())
			.orElse("NONE");
	}

	@Transactional
	public StudyResponse updateStudy(String email, Long studyId, StudyUpdateRequest request) {
		Study study = getStudyEntityForUpdate(studyId);
		validateLeader(study, email);

		Integer previousCapacity = study.getCapacity();
		long approvedCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED);
		boolean wasFull = approvedCount + 1 >= previousCapacity;

		study.update(request.title(), request.description(), request.capacity(), request.meetingDays(), request.meetingStartTime(), request.meetingEndTime());
		tagService.replaceStudyTags(study, request.tagIds());
		log.info("스터디 수정: studyId={}, email={}", studyId, email);

		boolean isFullNow = approvedCount + 1 >= study.getCapacity();
		if (wasFull && !isFullNow) {
			List<Long> recipientUserIds = waitlistService.findRecipientUserIds(studyId);
			if (!recipientUserIds.isEmpty()) {
				eventPublisher.publishEvent(new WaitlistSeatOpenedEvent(recipientUserIds, studyId, study.getTitle()));
			}
		}

		long memberCount = approvedCount + 1;
		return StudyResponse.from(study, memberCount, request.tagIds());
	}

	// 회고록/댓글 작성 등 스터디 경험치가 쌓이는 모든 지점에서 이 메서드를 통해 exp를 올린다.
	// 레벨업 여부 판단 + 멤버 전원 조회 + 알림 발행을 한 곳에서 처리해, 호출부(MemoirService,
	// MemoirCommentService)가 이 로직을 중복해서 갖지 않게 한다.
	@Transactional
	public void addExpAndNotifyLevelUp(Study study, int expAmount) {
		boolean leveledUp = study.addExp(expAmount);
		if (!leveledUp) {
			return;
		}

		List<Long> memberUserIds = getMemberUserIds(study);
		log.info("스터디 레벨업: studyId={}, newLevel={}", study.getId(), study.getLevel());
		eventPublisher.publishEvent(new StudyLevelUpEvent(memberUserIds, study.getId(), study.getTitle(), study.getLevel()));
	}

	// 방장 + 승인된 멤버 전원의 유저 id 목록(레벨업 등 스터디 전체 알림 대상 조회용).
	private List<Long> getMemberUserIds(Study study) {
		List<Long> memberUserIds = new ArrayList<>();
		memberUserIds.add(study.getLeader().getId());
		applicationRepository.findByStudyIdAndStatus(study.getId(), ApplicationStatus.APPROVED)
			.forEach(application -> memberUserIds.add(application.getApplicant().getId()));
		return memberUserIds;
	}

	@Transactional
	public void deleteStudy(String email, Long studyId) {
		Study study = getStudyEntity(studyId);
		validateLeader(study, email);

		applicationRepository.deleteAllByStudyId(studyId);
		tagService.deleteStudyTags(studyId);
		studyRepository.delete(study);
		log.info("스터디 삭제: studyId={}, email={}", studyId, email);
	}

	/**
	 * tagIds가 주어지면(즉석 태그 선택) 해당 태그 기준으로, 주어지지 않으면 로그인 유저의 저장된 선호 태그(UserTag)
	 * 기준으로 스터디별 태그(StudyTag)와 비교하여 일치율이 높은 순으로 정렬한 추천 목록을 반환한다.
	 *
	 * 매칭 개수 계산·정렬·페이지네이션을 DB에서 수행해, 스터디 건수가 늘어나도 요청당 처리량은
	 * 페이지 크기만큼으로 고정된다(스터디 전체를 애플리케이션 메모리로 읽어와 정렬하지 않는다).
	 */
	public Page<StudyMatchResponse> getMatchedStudies(String email, List<Long> tagIds, Pageable pageable) {
		List<Long> targetTagIds = (tagIds != null && !tagIds.isEmpty()) ? tagIds : tagService.getUserTagIds(email);
		log.info("스터디 매칭 조회: email={}, targetTagIds={}", email, targetTagIds);
		// 매칭 정렬 기준(matchedCount)이 고정이므로, 요청에 딸려온 정렬 조건은 무시하고 페이지 범위만 사용한다.
		Pageable pageRange = Pageable.ofSize(pageable.getPageSize()).withPage(pageable.getPageNumber());

		if (targetTagIds.isEmpty()) {
			// 매칭 기준 태그가 없으면 전부 matchScore=0으로 동률이므로, 최신 스터디가 먼저 보이도록 id desc로 정렬한다.
			Pageable idDescPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
			Page<Study> studyPage = studyRepository.findAllWithLeader(idDescPage);
			return buildMatchPage(studyPage, Map.of(), targetTagIds);
		}

		Page<StudyMatchCount> matchCountPage = tagService.getMatchedStudyIds(targetTagIds, pageRange);
		List<Long> pageIds = matchCountPage.getContent().stream().map(StudyMatchCount::getStudyId).toList();
		Map<Long, Long> matchedCountByStudyId = matchCountPage.getContent().stream()
			.collect(Collectors.toMap(StudyMatchCount::getStudyId, StudyMatchCount::getMatchedCount));

		Map<Long, Study> studyById = studyRepository.findAllWithLeaderByIdIn(pageIds).stream()
			.collect(Collectors.toMap(Study::getId, study -> study));
		List<Study> orderedStudies = pageIds.stream().map(studyById::get).toList();
		Page<Study> studyPage = new PageImpl<>(orderedStudies, pageRange, matchCountPage.getTotalElements());

		return buildMatchPage(studyPage, matchedCountByStudyId, targetTagIds);
	}

	private Page<StudyMatchResponse> buildMatchPage(Page<Study> studyPage, Map<Long, Long> matchedCountByStudyId, List<Long> targetTagIds) {
		List<Long> studyIds = studyPage.getContent().stream().map(Study::getId).toList();

		Map<Long, List<Long>> studyTagIdsByStudyId = tagService.getStudyTagIdsGroupedByStudyIds(studyIds);
		Map<Long, Long> approvedMemberCountByStudyId = getApprovedMemberCounts(studyIds);

		return studyPage.map(study -> {
			List<Long> studyTagIds = studyTagIdsByStudyId.getOrDefault(study.getId(), List.of());
			long matchedCount = matchedCountByStudyId.getOrDefault(study.getId(), 0L);
			double matchScore = targetTagIds.isEmpty() ? 0.0 : matchedCount * 100.0 / targetTagIds.size();

			StudyResponse studyResponse = StudyResponse.from(
				study,
				resolveMemberCount(approvedMemberCountByStudyId, study.getId()),
				studyTagIds
			);
			return StudyMatchResponse.of(studyResponse, matchedCount, matchScore);
		});
	}

	private Map<Long, Long> getApprovedMemberCounts(List<Long> studyIds) {
		if (studyIds.isEmpty()) {
			return Map.of();
		}

		return applicationRepository.countByStudyIdInAndStatus(studyIds, ApplicationStatus.APPROVED).stream()
			.collect(Collectors.toMap(StudyMemberCount::getStudyId, StudyMemberCount::getMemberCount));
	}

	private long resolveMemberCount(Map<Long, Long> approvedMemberCountByStudyId, Long studyId) {
		// 스터디장은 신청 없이 항상 멤버로 집계되므로 승인된 신청 수에 1을 더한다.
		return approvedMemberCountByStudyId.getOrDefault(studyId, 0L) + 1;
	}

	Study getStudyEntity(Long studyId) {
		return studyRepository.findById(studyId)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 스터디: studyId={}", studyId);
				return new IllegalArgumentException("존재하지 않는 스터디입니다.");
			});
	}

	// 정원 관련 판단(승인/탈퇴/정원변경)이 걸리는 지점에서만 사용. 트랜잭션이 끝날 때까지 이 스터디 행을 잠근다.
	Study getStudyEntityForUpdate(Long studyId) {
		return studyRepository.findByIdForUpdate(studyId)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 스터디: studyId={}", studyId);
				return new IllegalArgumentException("존재하지 않는 스터디입니다.");
			});
	}

	void validateLeader(Study study, String email) {
		User user = getUser(email);
		if (!study.isLeader(user.getId())) {
			log.warn("스터디 방장 아님: studyId={}, email={}", study.getId(), email);
			throw new ForbiddenException("스터디 방장만 수행할 수 있는 작업입니다.");
		}
	}

	private User getUser(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 유저: email={}", email);
				return new IllegalArgumentException("존재하지 않는 유저입니다.");
			});
	}
}
