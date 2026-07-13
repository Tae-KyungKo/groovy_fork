package com.groovy.backend.domain.study.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.study.Study;
import com.groovy.backend.domain.study.dto.StudyCreateRequest;
import com.groovy.backend.domain.study.dto.StudyMatchResponse;
import com.groovy.backend.domain.study.dto.StudyResponse;
import com.groovy.backend.domain.study.dto.StudyUpdateRequest;
import com.groovy.backend.domain.study.repository.ApplicationRepository;
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
			.meetingStartTime(request.meetingStartTime())
			.meetingEndTime(request.meetingEndTime())
			.build();

		Study savedStudy = studyRepository.save(study);
		tagService.replaceStudyTags(savedStudy, request.tagIds());

		return savedStudy.getId();
	}

	public Page<StudyResponse> getStudies(Pageable pageable) {
		return studyRepository.findAll(pageable).map(StudyResponse::from);
	}

	public StudyResponse getStudy(Long studyId) {
		return StudyResponse.from(getStudyEntity(studyId));
	}

	@Transactional
	public void updateStudy(String email, Long studyId, StudyUpdateRequest request) {
		Study study = getStudyEntity(studyId);
		validateLeader(study, email);

		study.update(request.title(), request.description(), request.meetingStartTime(), request.meetingEndTime());
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
	 * 로그인 유저의 선호 태그(UserTag)와 스터디별 태그(StudyTag)를 애플리케이션 계층에서 비교하여
	 * 일치 개수가 많은 순으로 정렬한 추천 목록을 반환한다.
	 */
	public List<StudyMatchResponse> getMatchedStudies(String email) {
		List<Long> userTagIds = tagService.getUserTagIds(email);
		List<Study> studies = studyRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
		List<Long> studyIds = studies.stream().map(Study::getId).toList();

		Map<Long, List<Long>> studyTagIdsByStudyId = tagService.getStudyTagIdsGroupedByStudyIds(studyIds);

		return studies.stream()
			.map(study -> toMatchResponse(study, studyTagIdsByStudyId.getOrDefault(study.getId(), List.of()), userTagIds))
			.sorted(
				Comparator.comparingInt(StudyMatchResponse::matchedTagCount).reversed()
					.thenComparing(Comparator.comparingDouble(StudyMatchResponse::matchScore).reversed())
			)
			.toList();
	}

	private StudyMatchResponse toMatchResponse(Study study, List<Long> studyTagIds, List<Long> userTagIds) {
		long matchedCount = studyTagIds.stream().filter(userTagIds::contains).count();
		double matchScore = studyTagIds.isEmpty() ? 0.0 : matchedCount * 100.0 / studyTagIds.size();

		return StudyMatchResponse.of(StudyResponse.from(study), (int)matchedCount, matchScore);
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
