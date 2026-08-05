package com.groovy.backend.domain.study.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.study.Application;
import com.groovy.backend.domain.study.ApplicationStatus;
import com.groovy.backend.domain.study.Study;
import com.groovy.backend.domain.study.dto.ApplicationResponse;
import com.groovy.backend.domain.study.dto.MyApplicationResponse;
import com.groovy.backend.domain.study.repository.ApplicationRepository;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

	private final ApplicationRepository applicationRepository;
	private final UserRepository userRepository;
	private final StudyService studyService;

	@Transactional
	public ApplicationResponse apply(String email, Long studyId) {
		User applicant = getUser(email);
		Study study = studyService.getStudyEntity(studyId);

		if (study.isLeader(applicant.getId())) {
			log.warn("방장 자가 신청 시도: studyId={}, applicantId={}", studyId, applicant.getId());
			throw new IllegalArgumentException("방장은 본인 스터디에 참여 신청할 수 없습니다.");
		}
		if (applicationRepository.existsByStudyIdAndApplicantId(studyId, applicant.getId())) {
			log.warn("중복 신청 시도: studyId={}, applicantId={}", studyId, applicant.getId());
			throw new IllegalArgumentException("이미 참여 신청한 스터디입니다.");
		}

		long currentMemberCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED)
				+ 1;
		if (currentMemberCount >= study.getCapacity()) {
			log.warn("정원 마감된 스터디 신청 시도: studyId={}, applicantId={}", studyId, applicant.getId());
			throw new IllegalArgumentException("정원이 마감된 스터디입니다.");
		}

		Application application = Application.builder()
				.study(study)
				.applicant(applicant)
				.status(ApplicationStatus.PENDING)
				.build();

		Application saved = applicationRepository.save(application);
		log.info("스터디 참여 신청: studyId={}, applicantId={}, applicationId={}", studyId, applicant.getId(), saved.getId());

		return ApplicationResponse.from(saved);
	}

	@Transactional
	public void cancel(String email, Long studyId) {
		User applicant = getUser(email);
		Application application = applicationRepository.findByStudyIdAndApplicantId(studyId, applicant.getId())
				.orElseThrow(() -> {
					log.warn("신청 내역 없음: studyId={}, applicantId={}", studyId, applicant.getId());
					return new IllegalArgumentException("신청 내역이 존재하지 않습니다.");
				});

		if (!application.isPending()) {
			log.warn("취소 불가 상태 신청 취소 시도: studyId={}, applicantId={}, status={}", studyId, applicant.getId(), application.getStatus());
			throw new IllegalArgumentException("대기 중인 신청만 취소할 수 있습니다.");
		}

		applicationRepository.delete(application);
		log.info("스터디 참여 신청 취소: studyId={}, applicantId={}", studyId, applicant.getId());
	}

	public List<ApplicationResponse> getApplications(String email, Long studyId) {
		Study study = studyService.getStudyEntity(studyId);
		studyService.validateLeader(study, email);

		return applicationRepository.findByStudyId(studyId).stream()
				.map(ApplicationResponse::from)
				.toList();
	}

	@Transactional
	public ApplicationResponse updateStatus(String email, Long studyId, Long applicationId, ApplicationStatus status) {
		if (status != ApplicationStatus.APPROVED && status != ApplicationStatus.REJECTED) {
			log.warn("잘못된 신청 상태 지정 시도: studyId={}, applicationId={}, status={}", studyId, applicationId, status);
			throw new IllegalArgumentException("승인 또는 거절 상태만 지정할 수 있습니다.");
		}

		Study study = studyService.getStudyEntity(studyId);
		studyService.validateLeader(study, email);

		Application application = applicationRepository.findById(applicationId)
				.filter(app -> app.getStudy().getId().equals(studyId))
				.orElseThrow(() -> {
					log.warn("존재하지 않는 신청: studyId={}, applicationId={}", studyId, applicationId);
					return new IllegalArgumentException("존재하지 않는 신청입니다.");
				});

		if (status == ApplicationStatus.APPROVED) {
			application.approve();
		} else {
			application.reject();
		}
		log.info("스터디 신청 상태 변경: studyId={}, applicationId={}, status={}", studyId, applicationId, status);

		return ApplicationResponse.from(application);
	}

	// 마이페이지 "참여 중인 스터디 / 신청 내역"용. 상태 무관 내 신청 전체를 반환한다.
	public List<MyApplicationResponse> getMyApplications(String email) {
		User user = getUser(email);
		return applicationRepository.findByApplicantId(user.getId()).stream()
				.map(MyApplicationResponse::from)
				.toList();
	}

	private User getUser(String email) {
		return userRepository.findByEmail(email)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 유저: email={}", email);
					return new IllegalArgumentException("존재하지 않는 유저입니다.");
				});
	}
}
