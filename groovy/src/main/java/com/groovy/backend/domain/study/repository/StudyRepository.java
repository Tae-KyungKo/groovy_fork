package com.groovy.backend.domain.study.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.domain.study.Study;

public interface StudyRepository extends JpaRepository<Study, Long> {

	List<Study> findByLeaderId(Long leaderId);

	// leader가 지연 로딩이라 fetch join 없이 조회하면 건수만큼 리더 조회 쿼리가 추가로 발생한다(N+1).
	@Query(
		value = "SELECT s FROM Study s JOIN FETCH s.leader",
		countQuery = "SELECT COUNT(s) FROM Study s"
	)
	Page<Study> findAllWithLeader(Pageable pageable);

	// 매칭 결과 한 페이지(소수)에 대해서만 상세 조회 시 리더를 함께 fetch join한다.
	@Query("SELECT s FROM Study s JOIN FETCH s.leader WHERE s.id IN :ids")
	List<Study> findAllWithLeaderByIdIn(@Param("ids") List<Long> ids);
}
