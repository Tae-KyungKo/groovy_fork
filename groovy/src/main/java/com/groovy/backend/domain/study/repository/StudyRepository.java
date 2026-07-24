package com.groovy.backend.domain.study.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groovy.backend.domain.study.Study;

public interface StudyRepository extends JpaRepository<Study, Long> {

	List<Study> findByLeaderId(Long leaderId);
}
