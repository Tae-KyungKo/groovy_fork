package com.groovy.backend.domain.study.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groovy.backend.domain.study.Application;
import com.groovy.backend.domain.study.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	boolean existsByStudyIdAndApplicantId(Long studyId, Long applicantId);

	Optional<Application> findByStudyIdAndApplicantId(Long studyId, Long applicantId);

	List<Application> findByStudyId(Long studyId);

	List<Application> findByApplicantIdAndStatus(Long applicantId, ApplicationStatus status);

	void deleteAllByStudyId(Long studyId);
}
