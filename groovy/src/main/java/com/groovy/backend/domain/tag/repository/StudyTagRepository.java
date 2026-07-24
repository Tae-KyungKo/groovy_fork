package com.groovy.backend.domain.tag.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groovy.backend.domain.tag.StudyTag;

public interface StudyTagRepository extends JpaRepository<StudyTag, Long> {

	List<StudyTag> findByStudyId(Long studyId);

	List<StudyTag> findByStudyIdIn(List<Long> studyIds);

	void deleteAllByStudyId(Long studyId);
}
