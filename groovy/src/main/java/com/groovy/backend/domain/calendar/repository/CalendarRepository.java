package com.groovy.backend.domain.calendar.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groovy.backend.domain.calendar.Calendar;

public interface CalendarRepository extends JpaRepository<Calendar, Long> {

	List<Calendar> findByUserId(Long userId);
}
