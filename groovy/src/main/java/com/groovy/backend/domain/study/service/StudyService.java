package com.groovy.backend.domain.study.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.study.ApplicationStatus;
import com.groovy.backend.domain.study.Study;
import com.groovy.backend.domain.study.dto.StudyCreateRequest;
import com.groovy.backend.domain.study.dto.StudyMatchResponse;
import com.groovy.backend.domain.study.dto.StudyResponse;
import com.groovy.backend.domain.study.dto.StudyUpdateRequest;
import com.groovy.backend.domain.study.repository.ApplicationRepository;
import com.groovy.backend.domain.study.repository.ApplicationRepository.StudyMemberCount;
import com.groovy.backend.domain.study.repository.StudyRepository;
import com.groovy.backend.domain.tag.service.TagService;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.repository.UserRepository;
import com.groovy.backend.global.exception.ForbiddenException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyService {

	private final StudyRepository studyRepository;
	private final ApplicationRepository applicationRepository;
	private final UserRepository userRepository;
	private final TagService tagService;

	@Transactional
	public Long createStudy(String leaderEmail, StudyCreateRequest request) {
		User leader = getUser(leaderEmail);

		Study study = Study.builder()
			.title(request.title())
			.description(request.description())
			.leader(leader)
			.capacity(request.capacity())
			.meetingStartTime(request.meetingStartTime())
			.meetingEndTime(request.meetingEndTime())
			.build();

		Study savedStudy = studyRepository.save(study);
		tagService.replaceStudyTags(savedStudy, request.tagIds());

		return savedStudy.getId();
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

	public StudyResponse getStudy(Long studyId) {
		Study study = getStudyEntity(studyId);
		long memberCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED) + 1;
		List<Long> tagIds = tagService.getStudyTagIds(studyId);

		return StudyResponse.from(study, memberCount, tagIds);
	}

	@Transactional
	public void updateStudy(String email, Long studyId, StudyUpdateRequest request) {
		Study study = getStudyEntity(studyId);
		validateLeader(study, email);

		study.update(request.title(), request.description(), request.capacity(), request.meetingStartTime(), request.meetingEndTime());
		tagService.replaceStudyTags(study, request.tagIds());
	}

	@Transactional
	public void deleteStudy(String email, Long studyId) {
		Study study = getStudyEntity(studyId);
		validateLeader(study, email);

		applicationRepository.deleteAllByStudyId(studyId);
		tagService.deleteStudyTags(studyId);
		studyRepository.delete(study);
	}

	/**
	 * tagIds가 주어지면(즉석 태그 선택) 해당 태그 기준으로, 주어지지 않으면 로그인 유저의 저장된 선호 태그(UserTag)
	 * 기준으로 스터디별 태그(StudyTag)와 비교하여 일치율이 높은 순으로 정렬한 추천 목록을 반환한다.
	 */
	public List<StudyMatchResponse> getMatchedStudies(String email, List<Long> tagIds) {
		List<Long> targetTagIds = (tagIds != null && !tagIds.isEmpty()) ? tagIds : tagService.getUserTagIds(email);

		List<Study> studies = studyRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
		List<Long> studyIds = studies.stream().map(Study::getId).toList();

		Map<Long, List<Long>> studyTagIdsByStudyId = tagService.getStudyTagIdsGroupedByStudyIds(studyIds);
		Map<Long, Long> approvedMemberCountByStudyId = getApprovedMemberCounts(studyIds);

		return studies.stream()
			.map(study -> toMatchResponse(
				study,
				studyTagIdsByStudyId.getOrDefault(study.getId(), List.of()),
				targetTagIds,
				resolveMemberCount(approvedMemberCountByStudyId, study.getId())
			))
			.sorted(Comparator.comparingDouble(StudyMatchResponse::matchScore).reversed())
			.toList();
	}

	private StudyMatchResponse toMatchResponse(Study study, List<Long> studyTagIds, List<Long> targetTagIds, long memberCount) {
		long matchedCount = studyTagIds.stream().filter(targetTagIds::contains).count();
		double matchScore = targetTagIds.isEmpty() ? 0.0 : matchedCount * 100.0 / targetTagIds.size();

		StudyResponse studyResponse = StudyResponse.from(study, memberCount, studyTagIds);
		return StudyMatchResponse.of(studyResponse, matchScore);
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
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 스터디입니다."));
	}

	void validateLeader(Study study, String email) {
		User user = getUser(email);
		if (!study.isLeader(user.getId())) {
			throw new ForbiddenException("스터디 방장만 수행할 수 있는 작업입니다.");
		}
	}

	private User getUser(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
	}
}
