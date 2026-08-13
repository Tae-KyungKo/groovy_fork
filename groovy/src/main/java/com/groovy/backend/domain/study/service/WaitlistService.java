package com.groovy.backend.domain.study.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.study.ApplicationStatus;
import com.groovy.backend.domain.study.Study;
import com.groovy.backend.domain.study.StudyWaitlist;
import com.groovy.backend.domain.study.repository.ApplicationRepository;
import com.groovy.backend.domain.study.repository.StudyRepository;
import com.groovy.backend.domain.study.repository.WaitlistRepository;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitlistService {

	private final WaitlistRepository waitlistRepository;
	private final ApplicationRepository applicationRepository;
	private final UserRepository userRepository;
	// StudyService를 쓰면 StudyService.updateStudy() -> WaitlistService -> StudyService로
	// 순환 의존이 생겨서, 여기서는 StudyRepository를 직접 참조한다(ApplicationService와는 다른 예외적 케이스).
	private final StudyRepository studyRepository;

	@Transactional
	public void register(String email, Long studyId) {
		User user = getUser(email);
		Study study = getStudy(studyId);

		if (study.isLeader(user.getId())) {
			log.warn("방장의 대기열 등록 시도: studyId={}, userId={}", studyId, user.getId());
			throw new IllegalArgumentException("방장은 대기열에 등록할 수 없습니다.");
		}
		if (applicationRepository.existsByStudyIdAndApplicantIdAndStatus(studyId, user.getId(), ApplicationStatus.APPROVED)) {
			log.warn("이미 멤버인 유저의 대기열 등록 시도: studyId={}, userId={}", studyId, user.getId());
			throw new IllegalArgumentException("이미 참여 중인 스터디입니다.");
		}

		long approvedCount = applicationRepository.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED) + 1;
		if (approvedCount < study.getCapacity()) {
			log.warn("정원이 남은 스터디의 대기열 등록 시도: studyId={}, userId={}", studyId, user.getId());
			throw new IllegalArgumentException("정원이 남아있는 스터디는 대기열에 등록할 수 없습니다.");
		}
		if (waitlistRepository.existsByStudyIdAndUserId(studyId, user.getId())) {
			log.warn("중복 대기열 등록 시도: studyId={}, userId={}", studyId, user.getId());
			throw new IllegalArgumentException("이미 빈자리 알림을 등록했습니다.");
		}

		waitlistRepository.save(StudyWaitlist.builder().study(study).user(user).build());
		log.info("대기열 등록: studyId={}, userId={}", studyId, user.getId());
	}

	@Transactional
	public void cancel(String email, Long studyId) {
		User user = getUser(email);
		StudyWaitlist waitlist = waitlistRepository.findByStudyIdAndUserId(studyId, user.getId())
			.orElseThrow(() -> {
				log.warn("대기열 등록 내역 없음: studyId={}, userId={}", studyId, user.getId());
				return new IllegalArgumentException("빈자리 알림을 등록하지 않았습니다.");
			});

		waitlistRepository.delete(waitlist);
		log.info("대기열 취소: studyId={}, userId={}", studyId, user.getId());
	}

	// 실제로 그 스터디에 합류(승인)했을 때만 대기열에서 빠진다. 등록한 적이 없어도 조용히 무시한다.
	@Transactional
	public void removeIfRegistered(Long studyId, Long userId) {
		waitlistRepository.findByStudyIdAndUserId(studyId, userId)
			.ifPresent(waitlist -> {
				waitlistRepository.delete(waitlist);
				log.info("합류 완료로 대기열 자동 제거: studyId={}, userId={}", studyId, userId);
			});
	}

	public boolean isRegistered(Long studyId, Long userId) {
		return waitlistRepository.existsByStudyIdAndUserId(studyId, userId);
	}

	// 빈자리 알림 발행 시 대상자 id 목록 조회용. 알림을 보낸다고 여기서 삭제하지 않는다.
	public List<Long> findRecipientUserIds(Long studyId) {
		return waitlistRepository.findByStudyId(studyId).stream()
			.map(waitlist -> waitlist.getUser().getId())
			.toList();
	}

	private User getUser(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 유저: email={}", email);
				return new IllegalArgumentException("존재하지 않는 유저입니다.");
			});
	}

	private Study getStudy(Long studyId) {
		return studyRepository.findById(studyId)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 스터디: studyId={}", studyId);
				return new IllegalArgumentException("존재하지 않는 스터디입니다.");
			});
	}
}
