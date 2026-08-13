package com.groovy.backend.domain.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groovy.backend.domain.notification.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId);

	Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

	List<Notification> findByRecipientIdAndReadFalse(Long recipientId);
}
