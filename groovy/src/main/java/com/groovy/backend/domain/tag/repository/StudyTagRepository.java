package com.groovy.backend.domain.tag.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.domain.tag.StudyTag;

public interface StudyTagRepository extends JpaRepository<StudyTag, Long> {

	List<StudyTag> findByStudyId(Long studyId);

	List<StudyTag> findByStudyIdIn(List<Long> studyIds);

	void deleteAllByStudyId(Long studyId);

	// 태그 매칭 개수(matchedCount) 기준 정렬 + 페이지네이션을 DB에서 수행한다.
	// LEFT JOIN이라 매칭 태그가 하나도 없는 스터디도 matchedCount=0으로 결과에 포함된다.
	@Query(
		value = "SELECT s.id AS studyId, COUNT(st.id) AS matchedCount "
			+ "FROM Study s LEFT JOIN StudyTag st ON st.study = s AND st.tag.id IN :tagIds "
			+ "GROUP BY s.id "
			+ "ORDER BY COUNT(st.id) DESC, s.id DESC",
		countQuery = "SELECT COUNT(s) FROM Study s"
	)
	Page<StudyMatchCount> findMatchedStudyIds(@Param("tagIds") List<Long> tagIds, Pageable pageable);

	interface StudyMatchCount {
		Long getStudyId();

		Long getMatchedCount();
	}
}
