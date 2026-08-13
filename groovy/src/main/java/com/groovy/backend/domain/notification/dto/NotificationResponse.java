package com.groovy.backend.domain.notification.dto;

import com.groovy.backend.domain.notification.Notification;
import com.groovy.backend.domain.notification.NotificationType;

public record NotificationResponse(
	String id,
	NotificationType type,
	String title,
	String message,
	String targetStudyId,
	String createdAt
) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
			String.valueOf(notification.getId()),
			notification.getType(),
			notification.getTitle(),
			notification.getMessage(),
			notification.getTargetStudyId() != null ? String.valueOf(notification.getTargetStudyId()) : null,
			notification.getCreatedAt().toString()
		);
	}
}
