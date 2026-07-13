package com.groovy.backend.domain.study.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.study.Study;
import com.groovy.backend.domain.study.dto.StudyCreateRequest;
import com.groovy.backend.domain.study.dto.StudyResponse;
import com.groovy.backend.domain.study.dto.StudyUpdateRequest;
import com.groovy.backend.domain.study.repository.ApplicationRepository;
import com.groovy.backend.domain.study.repository.StudyRepository;
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

	@Transactional
	public Long createStudy(String leaderEmail, StudyCreateRequest request) {
		User leader = getUser(leaderEmail);

		Study study = Study.builder()
			.title(request.title())
			.description(request.description())
			.leader(leader)
			.build();

		return studyRepository.save(study).getId();
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

		study.update(request.title(), request.description());
	}

	@Transactional
	public void deleteStudy(String email, Long studyId) {
		Study study = getStudyEntity(studyId);
		validateLeader(study, email);

		applicationRepository.deleteAllByStudyId(studyId);
		studyRepository.delete(study);
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
