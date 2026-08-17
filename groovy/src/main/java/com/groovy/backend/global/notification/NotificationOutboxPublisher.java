package com.groovy.backend.global.notification;

import java.util.List;

import org.springframework.stereotype.Component;

import com.groovy.backend.global.outbox.OutboxEventWriter;

import lombok.RequiredArgsConstructor;

/**
 * MSA 전환 Phase 9: 예전 NotificationEventBridge(HTTP 동기 호출)를 대체한다. Study/Memoir/
 * Calendar 서비스가 이제 이 컴포넌트를 직접 호출하고, 이 컴포넌트는 (Spring Event로 우회하지
 * 않고) 호출자의 트랜잭션 안에서 곧바로 OutboxEventWriter로 기록한다 — 그래야 "비즈니스 데이터
 * 저장 + Outbox 기록"이 원자적이라는 Outbox 패턴의 전제가 성립한다. 알림 텍스트(한국어 제목/
 * 본문) 조합도 여기서 그대로 담당한다(예전 NotificationEventListener/Bridge와 동일).
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxPublisher {

	private final OutboxEventWriter outboxEventWriter;

	public void applicationSubmitted(Long recipientUserId, String applicantName, Long studyId, String studyTitle) {
		publish(recipientUserId, "APPLICATION_RECEIVED", "새 참여 신청이 도착했어요",
			"%s님이 \"%s\"에 참여 신청했어요.".formatted(applicantName, studyTitle), studyId);
	}

	public void applicationDecided(Long recipientUserId, boolean approved, Long studyId, String studyTitle) {
		if (approved) {
			publish(recipientUserId, "APPLICATION_APPROVED", "참여 신청이 승인됐어요",
				"\"%s\"에 참여가 확정됐어요.".formatted(studyTitle), studyId);
		} else {
			publish(recipientUserId, "APPLICATION_REJECTED", "참여 신청이 거절됐어요",
				"\"%s\" 참여 신청이 거절됐어요.".formatted(studyTitle), studyId);
		}
	}

	public void waitlistSeatOpened(List<Long> recipientUserIds, Long studyId, String studyTitle) {
		for (Long recipientUserId : recipientUserIds) {
			publish(recipientUserId, "WAITLIST_SEAT_OPENED", "빈자리가 생겼어요",
				"\"%s\"에 빈자리가 생겼어요. 지금 신청해보세요.".formatted(studyTitle), studyId);
		}
	}

	public void studyLevelUp(List<Long> recipientUserIds, Long studyId, String studyTitle, int newLevel) {
		for (Long recipientUserId : recipientUserIds) {
			publish(recipientUserId, "STUDY_LEVEL_UP", "스터디 레벨이 올랐어요",
				"\"%s\"이(가) 레벨 %d이 됐어요!".formatted(studyTitle, newLevel), studyId);
		}
	}

	public void studyScheduleChanged(List<Long> recipientUserIds, Long studyId, String studyTitle,
		String scheduleTitle, ScheduleChangeType changeType) {
		String title = switch (changeType) {
			case CREATED -> "새 일정이 등록됐어요";
			case UPDATED -> "일정이 변경됐어요";
			case DELETED -> "일정이 삭제됐어요";
		};
		String actionVerb = switch (changeType) {
			case CREATED -> "등록됐어요";
			case UPDATED -> "변경됐어요";
			case DELETED -> "삭제됐어요";
		};
		for (Long recipientUserId : recipientUserIds) {
			publish(recipientUserId, "STUDY_SCHEDULE_CHANGED", title,
				"\"%s\"의 \"%s\" 일정이 %s".formatted(studyTitle, scheduleTitle, actionVerb), studyId);
		}
	}

	public void memoirCommentAdded(Long recipientUserId, String commenterName, Long memoirId, String memoirTitle) {
		publish(recipientUserId, "MEMOIR_COMMENT_ADDED", "새로운 댓글이 달렸습니다",
			"%s님이 \"%s\"에 댓글을 남겼어요.".formatted(commenterName, memoirTitle), memoirId);
	}

	public void memoirLikeAdded(Long recipientUserId, String likerName, Long memoirId, String memoirTitle) {
		publish(recipientUserId, "MEMOIR_LIKE_ADDED", "회고록에 좋아요가 눌렸어요",
			"%s님이 \"%s\"을(를) 좋아해요.".formatted(likerName, memoirTitle), memoirId);
	}

	private void publish(Long recipientUserId, String type, String title, String message, Long targetId) {
		outboxEventWriter.write(type, new NotificationPayload(recipientUserId, type, title, message, targetId));
	}
}
