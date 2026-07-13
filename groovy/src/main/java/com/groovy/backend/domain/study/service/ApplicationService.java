package com.groovy.backend.domain.study.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.study.Application;
import com.groovy.backend.domain.study.ApplicationStatus;
import com.groovy.backend.domain.study.Study;
import com.groovy.backend.domain.study.dto.ApplicationResponse;
import com.groovy.backend.domain.study.repository.ApplicationRepository;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

	private final ApplicationRepository applicationRepository;
	private final UserRepository userRepository;
	private final StudyService studyService;

	@Transactional
	public Long apply(String email, Long studyId) {
		User applicant = getUser(email);
		Study study = studyService.getStudyEntity(studyId);

		if (study.isLeader(applicant.getId())) {
			throw new IllegalArgumentException("방장은 본인 스터디에 참여 신청할 수 없습니다.");
		}
		if (applicationRepository.existsByStudyIdAndApplicantId(studyId, applicant.getId())) {
			throw new IllegalArgumentException("이미 참여 신청한 스터디입니다.");
		}

		Application application = Application.builder()
			.study(study)
			.applicant(applicant)
			.status(ApplicationStatus.PENDING)
			.build();

		return applicationRepository.save(application).getId();
	}

	@Transactional
	public void cancel(String email, Long studyId) {
		User applicant = getUser(email);
		Application application = applicationRepository.findByStudyIdAndApplicantId(studyId, applicant.getId())
			.orElseThrow(() -> new IllegalArgumentException("신청 내역이 존재하지 않습니다."));

		if (!application.isPending()) {
			throw new IllegalArgumentException("대기 중인 신청만 취소할 수 있습니다.");
		}

		applicationRepository.delete(application);
	}

	public List<ApplicationResponse> getApplications(String email, Long studyId) {
		Study study = studyService.getStudyEntity(studyId);
		studyService.validateLeader(study, email);

		return applicationRepository.findByStudyId(studyId).stream()
			.map(ApplicationResponse::from)
			.toList();
	}

	@Transactional
	public void updateStatus(String email, Long studyId, Long applicationId, ApplicationStatus status) {
		if (status != ApplicationStatus.APPROVED && status != ApplicationStatus.REJECTED) {
			throw new IllegalArgumentException("승인 또는 거절 상태만 지정할 수 있습니다.");
		}

		Study study = studyService.getStudyEntity(studyId);
		studyService.validateLeader(study, email);

		Application application = applicationRepository.findById(applicationId)
			.filter(app -> app.getStudy().getId().equals(studyId))
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신청입니다."));

		if (status == ApplicationStatus.APPROVED) {
			application.approve();
		} else {
			application.reject();
		}
	}

	private User getUser(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
	}
}
