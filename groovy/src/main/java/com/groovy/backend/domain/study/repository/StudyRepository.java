package com.groovy.backend.domain.study.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groovy.backend.domain.study.Study;

public interface StudyRepository extends JpaRepository<Study, Long> {
}
