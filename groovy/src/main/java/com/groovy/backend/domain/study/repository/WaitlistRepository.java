package com.groovy.backend.domain.study.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groovy.backend.domain.study.StudyWaitlist;

public interface WaitlistRepository extends JpaRepository<StudyWaitlist, Long> {

	boolean existsByStudyIdAndUserId(Long studyId, Long userId);

	Optional<StudyWaitlist> findByStudyIdAndUserId(Long studyId, Long userId);

	List<StudyWaitlist> findByStudyId(Long studyId);
}
