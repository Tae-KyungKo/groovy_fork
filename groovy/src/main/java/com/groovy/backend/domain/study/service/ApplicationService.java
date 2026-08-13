package com.groovy.backend.domain.study.service;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.notification.event.ApplicationDecidedEvent;
import com.groovy.backend.domain.notification.event.ApplicationSubmittedEvent;
import com.groovy.backend.domain.notification.event.WaitlistSeatOpenedEvent;
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
	private final WaitlistService waitlistService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public ApplicationResponse apply(String email, Long studyId) {
		User applicant = getUser(email);
		Study study = studyService.getStudyEntity(studyId);

		if (study.isLeader(applicant.getId())) {
			log.warn("방장 자가 신청 시도: studyId={}, applicantId={}", studyId, applicant.getId());
			throw new IllegalArgumentException("방장은 본인 스터디에 참여 신청할 수 없습니다.");
		}

		// (study_id, applicant_id)가 유니크라 과거 이력이 있으면 새 row를 만들 수 없다.
		// PENDING/APPROVED로 이미 관계가 있으면 막고, REJECTED였던 기록만 재사용해 재신청을 허용한다.
		Optional<Application> existingApplication = applicationRepository.findByStudyIdAndApplicantId(studyId, applicant.getId());
		if (existingApplication.isPresent() && existingApplication.get().getStatus() != ApplicationStatus.REJECTED) {
			log.warn("중복 신청 시도: studyId={}, applicantId={}", studyId, applicant.getId());
			throw new IllegalArgumentException("이미 참여 신청한 스터디입니다.");
		}

		long currentMemberCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED)
				+ 1;
		if (currentMemberCount >= study.getCapacity()) {
			log.warn("정원 마감된 스터디 신청 시도: studyId={}, applicantId={}", studyId, applicant.getId());
			throw new IllegalArgumentException("정원이 마감된 스터디입니다.");
		}

		Application saved;
		if (existingApplication.isPresent()) {
			saved = existingApplication.get();
			saved.reapply();
		} else {
			saved = applicationRepository.save(Application.builder()
					.study(study)
					.applicant(applicant)
					.status(ApplicationStatus.PENDING)
					.build());
		}
		log.info("스터디 참여 신청: studyId={}, applicantId={}, applicationId={}", studyId, applicant.getId(), saved.getId());

		eventPublisher.publishEvent(new ApplicationSubmittedEvent(
			study.getLeader().getId(), applicant.getName(), studyId, study.getTitle()));

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

	// "탈퇴" — 이미 승인되어 멤버가 된 사람이 스스로 나가는 것. cancel()과 달리 APPROVED 상태에서만 허용된다.
	@Transactional
	public void leave(String email, Long studyId) {
		User applicant = getUser(email);
		Application application = applicationRepository.findByStudyIdAndApplicantId(studyId, applicant.getId())
				.orElseThrow(() -> {
					log.warn("신청 내역 없음: studyId={}, applicantId={}", studyId, applicant.getId());
					return new IllegalArgumentException("신청 내역이 존재하지 않습니다.");
				});

		if (application.getStatus() != ApplicationStatus.APPROVED) {
			log.warn("탈퇴 불가 상태: studyId={}, applicantId={}, status={}", studyId, applicant.getId(), application.getStatus());
			throw new IllegalArgumentException("참여 중인 멤버만 탈퇴할 수 있습니다.");
		}

		// 탈퇴로 실제 빈자리가 생기는지(= 탈퇴 전에 정원이 꽉 차 있었는지)는 락을 잡고 판단해야
		// 동시에 다른 트랜잭션이 승인을 진행 중이어도 정확한 값을 본다.
		Study study = studyService.getStudyEntityForUpdate(studyId);
		long approvedCountBeforeLeave = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED);
		boolean wasFull = approvedCountBeforeLeave + 1 >= study.getCapacity();

		applicationRepository.delete(application);
		log.info("스터디 탈퇴: studyId={}, applicantId={}", studyId, applicant.getId());

		if (wasFull) {
			List<Long> recipientUserIds = waitlistService.findRecipientUserIds(studyId);
			if (!recipientUserIds.isEmpty()) {
				eventPublisher.publishEvent(new WaitlistSeatOpenedEvent(recipientUserIds, studyId, study.getTitle()));
			}
		}
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

		// 승인 여부를 정확히 판단하려면 이 스터디에 대한 다른 승인 트랜잭션과 순서가 보장돼야 하므로,
		// 조회 시점부터 락을 잡는다(REJECTED만 하는 경우도 어차피 방장 1명만 조작하는 자원이라 락 비용은 미미하다).
		Study study = studyService.getStudyEntityForUpdate(studyId);
		studyService.validateLeader(study, email);

		Application application = applicationRepository.findById(applicationId)
				.filter(app -> app.getStudy().getId().equals(studyId))
				.orElseThrow(() -> {
					log.warn("존재하지 않는 신청: studyId={}, applicationId={}", studyId, applicationId);
					return new IllegalArgumentException("존재하지 않는 신청입니다.");
				});

		if (status == ApplicationStatus.APPROVED) {
			long approvedCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED) + 1;
			if (approvedCount >= study.getCapacity()) {
				log.warn("정원 초과 승인 시도: studyId={}, applicationId={}", studyId, applicationId);
				throw new IllegalArgumentException("정원이 가득 차 승인할 수 없습니다.");
			}
			application.approve();
			waitlistService.removeIfRegistered(studyId, application.getApplicant().getId());
		} else {
			application.reject();
		}
		log.info("스터디 신청 상태 변경: studyId={}, applicationId={}, status={}", studyId, applicationId, status);

		eventPublisher.publishEvent(new ApplicationDecidedEvent(
			application.getApplicant().getId(), status == ApplicationStatus.APPROVED, studyId, study.getTitle()));

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
