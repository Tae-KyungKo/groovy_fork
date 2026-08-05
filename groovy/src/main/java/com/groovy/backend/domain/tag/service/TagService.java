package com.groovy.backend.domain.tag.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.study.Study;
import com.groovy.backend.domain.tag.StudyTag;
import com.groovy.backend.domain.tag.Tag;
import com.groovy.backend.domain.tag.UserTag;
import com.groovy.backend.domain.tag.dto.TagResponse;
import com.groovy.backend.domain.tag.repository.StudyTagRepository;
import com.groovy.backend.domain.tag.repository.StudyTagRepository.StudyMatchCount;
import com.groovy.backend.domain.tag.repository.TagRepository;
import com.groovy.backend.domain.tag.repository.UserTagRepository;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

	private final TagRepository tagRepository;
	private final UserTagRepository userTagRepository;
	private final StudyTagRepository studyTagRepository;
	private final UserRepository userRepository;

	public List<TagResponse> getTags() {
		return tagRepository.findAll().stream()
			.map(TagResponse::from)
			.toList();
	}

	public List<TagResponse> getUserTags(String email) {
		User user = getUser(email);
		return userTagRepository.findByUserId(user.getId()).stream()
			.map(userTag -> TagResponse.from(userTag.getTag()))
			.toList();
	}

	@Transactional
	public void updateUserTags(String email, List<Long> tagIds) {
		User user = getUser(email);
		List<Tag> tags = resolveTags(tagIds);

		userTagRepository.deleteAllByUserId(user.getId());
		List<UserTag> userTags = tags.stream()
			.map(tag -> UserTag.builder().user(user).tag(tag).build())
			.toList();
		userTagRepository.saveAll(userTags);
		log.info("유저 관심 태그 변경: email={}, tagIds={}", email, tagIds);
	}

	@Transactional
	public void replaceStudyTags(Study study, List<Long> tagIds) {
		List<Tag> tags = resolveTags(tagIds);

		studyTagRepository.deleteAllByStudyId(study.getId());
		List<StudyTag> studyTags = tags.stream()
			.map(tag -> StudyTag.builder().study(study).tag(tag).build())
			.toList();
		studyTagRepository.saveAll(studyTags);
		log.info("스터디 태그 변경: studyId={}, tagIds={}", study.getId(), tagIds);
	}

	@Transactional
	public void deleteStudyTags(Long studyId) {
		studyTagRepository.deleteAllByStudyId(studyId);
		log.info("스터디 태그 삭제: studyId={}", studyId);
	}

	public List<Long> getUserTagIds(String email) {
		User user = getUser(email);
		return userTagRepository.findByUserId(user.getId()).stream()
			.map(userTag -> userTag.getTag().getId())
			.toList();
	}

	public List<Long> getStudyTagIds(Long studyId) {
		return studyTagRepository.findByStudyId(studyId).stream()
			.map(studyTag -> studyTag.getTag().getId())
			.toList();
	}

	// 태그 매칭 개수 기준 정렬 + 페이지네이션 결과. 정렬 기준(matchedCount)이 고정이라
	// pageable의 sort는 무시하고 page/size(offset/limit)만 사용해야 한다.
	public Page<StudyMatchCount> getMatchedStudyIds(List<Long> tagIds, Pageable pageable) {
		return studyTagRepository.findMatchedStudyIds(tagIds, pageable);
	}

	public Map<Long, List<Long>> getStudyTagIdsGroupedByStudyIds(List<Long> studyIds) {
		if (studyIds.isEmpty()) {
			return Map.of();
		}

		return studyTagRepository.findByStudyIdIn(studyIds).stream()
			.collect(Collectors.groupingBy(
				studyTag -> studyTag.getStudy().getId(),
				Collectors.mapping(studyTag -> studyTag.getTag().getId(), Collectors.toList())
			));
	}

	private List<Tag> resolveTags(List<Long> tagIds) {
		if (tagIds.isEmpty()) {
			return List.of();
		}

		List<Tag> tags = tagRepository.findAllById(tagIds);
		if (tags.size() != new HashSet<>(tagIds).size()) {
			log.warn("존재하지 않는 태그 포함: tagIds={}", tagIds);
			throw new IllegalArgumentException("존재하지 않는 태그가 포함되어 있습니다.");
		}

		return tags;
	}

	private User getUser(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 유저: email={}", email);
				return new IllegalArgumentException("존재하지 않는 유저입니다.");
			});
	}
}
