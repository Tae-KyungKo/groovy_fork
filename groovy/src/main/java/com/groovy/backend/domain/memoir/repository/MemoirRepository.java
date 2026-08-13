package com.groovy.backend.domain.memoir.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.domain.memoir.Memoir;

public interface MemoirRepository extends JpaRepository<Memoir, Long> {

	// study/author가 지연 로딩이라 fetch join 없이 목록을 조회하면 건수만큼 추가 쿼리가 발생한다(N+1).
	// keyword가 없으면(:keyword IS NULL) 조건이 전부 통과되어 전체 목록 조회와 동일하게 동작한다.
	@Query(
		value = "SELECT m FROM Memoir m JOIN FETCH m.study JOIN FETCH m.author a "
			+ "WHERE (:keyword IS NULL "
			+ "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')))",
		countQuery = "SELECT COUNT(m) FROM Memoir m JOIN m.author a "
			+ "WHERE (:keyword IS NULL "
			+ "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')))"
	)
	Page<Memoir> search(@Param("keyword") String keyword, Pageable pageable);

	@Query(
		value = "SELECT m FROM Memoir m JOIN FETCH m.study JOIN FETCH m.author WHERE m.author.id = :authorId",
		countQuery = "SELECT COUNT(m) FROM Memoir m WHERE m.author.id = :authorId"
	)
	Page<Memoir> findByAuthorIdWithStudyAndAuthor(@Param("authorId") Long authorId, Pageable pageable);

	@Query("SELECT m FROM Memoir m JOIN FETCH m.study JOIN FETCH m.author WHERE m.id = :id")
	Optional<Memoir> findByIdWithStudyAndAuthor(@Param("id") Long id);

	// 인기순 정렬 한 페이지(소수)에 대해서만 상세 조회 시 study/author를 함께 fetch join한다.
	@Query("SELECT m FROM Memoir m JOIN FETCH m.study JOIN FETCH m.author WHERE m.id IN :ids")
	List<Memoir> findAllWithStudyAndAuthorByIdIn(@Param("ids") List<Long> ids);

	/**
	 * "인기순"(좋아요 수 + 댓글 수 내림차순) 정렬 id 목록. 좋아요/댓글 카운트를 서브쿼리로 미리 집계한 뒤
	 * 정렬·페이지네이션을 DB에서 수행해, 회고록 건수가 늘어나도 요청당 처리량은 페이지 크기만큼으로 고정된다.
	 */
	@Query(
		value = "SELECT m.id FROM memoirs m "
			+ "JOIN users a ON a.id = m.author_id "
			+ "LEFT JOIN (SELECT memoir_id, COUNT(*) AS cnt FROM memoir_likes GROUP BY memoir_id) lc ON lc.memoir_id = m.id "
			+ "LEFT JOIN (SELECT memoir_id, COUNT(*) AS cnt FROM memoir_comments GROUP BY memoir_id) cc ON cc.memoir_id = m.id "
			+ "WHERE (:keyword IS NULL "
			+ "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
			+ "ORDER BY (COALESCE(lc.cnt, 0) + COALESCE(cc.cnt, 0)) DESC, m.id DESC",
		countQuery = "SELECT COUNT(*) FROM memoirs m JOIN users a ON a.id = m.author_id "
			+ "WHERE (:keyword IS NULL "
			+ "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) "
			+ "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')))",
		nativeQuery = true
	)
	Page<Long> findIdsByPopularity(@Param("keyword") String keyword, Pageable pageable);
}
