package com.groovy.backend.domain.notification.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.groovy.backend.domain.notification.NotificationType;
import com.groovy.backend.domain.notification.event.ApplicationDecidedEvent;
import com.groovy.backend.domain.notification.event.ApplicationSubmittedEvent;
import com.groovy.backend.domain.notification.event.MemoirCommentAddedEvent;
import com.groovy.backend.domain.notification.event.MemoirLikeAddedEvent;
import com.groovy.backend.domain.notification.event.StudyLevelUpEvent;
import com.groovy.backend.domain.notification.event.StudyScheduleChangedEvent;
import com.groovy.backend.domain.notification.event.WaitlistSeatOpenedEvent;

import lombok.RequiredArgsConstructor;

/**
 * 도메인 서비스가 발행한 이벤트를 받아 알림을 만든다. 트랜잭션이 커밋된 뒤에만(AFTER_COMMIT)
 * 동작해서, 롤백된 작업에 대한 오알림을 막는다. 비동기로 돌려서 알림 생성/SSE push가
 * 원래 API 응답을 기다리게 하지 않는다. 도메인 지식(수신자가 누구인지)은 이벤트를 만든
 * 쪽이 이미 다 채워왔으므로, 여기서는 "누구에게 어떤 알림을" 그대로 실행만 한다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private final NotificationService notificationService;

	@Async("notificationTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onApplicationSubmitted(ApplicationSubmittedEvent event) {
		notificationService.createAndPublish(
			event.recipientUserId(),
			NotificationType.APPLICATION_RECEIVED,
			"새 참여 신청이 도착했어요",
			"%s님이 \"%s\"에 참여 신청했어요.".formatted(event.applicantName(), event.studyTitle()),
			event.studyId()
		);
	}

	@Async("notificationTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onApplicationDecided(ApplicationDecidedEvent event) {
		if (event.approved()) {
			notificationService.createAndPublish(
				event.recipientUserId(),
				NotificationType.APPLICATION_APPROVED,
				"참여 신청이 승인됐어요",
				"\"%s\"에 참여가 확정됐어요.".formatted(event.studyTitle()),
				event.studyId()
			);
		} else {
			notificationService.createAndPublish(
				event.recipientUserId(),
				NotificationType.APPLICATION_REJECTED,
				"참여 신청이 거절됐어요",
				"\"%s\" 참여 신청이 거절됐어요.".formatted(event.studyTitle()),
				event.studyId()
			);
		}
	}

	@Async("notificationTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onStudyScheduleChanged(StudyScheduleChangedEvent event) {
		String title = switch (event.changeType()) {
			case CREATED -> "새 일정이 등록됐어요";
			case UPDATED -> "일정이 변경됐어요";
			case DELETED -> "일정이 삭제됐어요";
		};
		String actionVerb = switch (event.changeType()) {
			case CREATED -> "등록됐어요";
			case UPDATED -> "변경됐어요";
			case DELETED -> "삭제됐어요";
		};
		for (Long recipientUserId : event.recipientUserIds()) {
			notificationService.createAndPublish(
				recipientUserId,
				NotificationType.STUDY_SCHEDULE_CHANGED,
				title,
				"\"%s\"의 \"%s\" 일정이 %s".formatted(event.studyTitle(), event.scheduleTitle(), actionVerb),
				event.studyId()
			);
		}
	}

	@Async("notificationTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onWaitlistSeatOpened(WaitlistSeatOpenedEvent event) {
		for (Long recipientUserId : event.recipientUserIds()) {
			notificationService.createAndPublish(
				recipientUserId,
				NotificationType.WAITLIST_SEAT_OPENED,
				"빈자리가 생겼어요",
				"\"%s\"에 빈자리가 생겼어요. 지금 신청해보세요.".formatted(event.studyTitle()),
				event.studyId()
			);
		}
	}

	@Async("notificationTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMemoirCommentAdded(MemoirCommentAddedEvent event) {
		notificationService.createAndPublish(
			event.recipientUserId(),
			NotificationType.MEMOIR_COMMENT_ADDED,
			"새로운 댓글이 달렸습니다",
			"%s님이 \"%s\"에 댓글을 남겼어요.".formatted(event.commenterName(), event.memoirTitle()),
			event.memoirId()
		);
	}

	@Async("notificationTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMemoirLikeAdded(MemoirLikeAddedEvent event) {
		notificationService.createAndPublish(
			event.recipientUserId(),
			NotificationType.MEMOIR_LIKE_ADDED,
			"회고록에 좋아요가 눌렸어요",
			"%s님이 \"%s\"을(를) 좋아해요.".formatted(event.likerName(), event.memoirTitle()),
			event.memoirId()
		);
	}

	@Async("notificationTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onStudyLevelUp(StudyLevelUpEvent event) {
		for (Long recipientUserId : event.recipientUserIds()) {
			notificationService.createAndPublish(
				recipientUserId,
				NotificationType.STUDY_LEVEL_UP,
				"스터디 레벨이 올랐어요",
				"\"%s\"이(가) 레벨 %d이 됐어요!".formatted(event.studyTitle(), event.newLevel()),
				event.studyId()
			);
		}
	}
}
