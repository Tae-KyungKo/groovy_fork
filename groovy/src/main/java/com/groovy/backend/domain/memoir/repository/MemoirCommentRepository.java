package com.groovy.backend.domain.memoir.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.domain.memoir.MemoirComment;

public interface MemoirCommentRepository extends JpaRepository<MemoirComment, Long> {

	// author가 지연 로딩이라 fetch join 없이 조회하면 댓글 수만큼 작성자 조회 쿼리가 추가로 발생한다(N+1).
	@Query("SELECT c FROM MemoirComment c JOIN FETCH c.author WHERE c.memoir.id = :memoirId ORDER BY c.id ASC")
	List<MemoirComment> findByMemoirIdWithAuthor(@Param("memoirId") Long memoirId);

	long countByMemoirId(Long memoirId);

	void deleteAllByMemoirId(Long memoirId);

	@Query("SELECT c.memoir.id AS memoirId, COUNT(c) AS commentCount FROM MemoirComment c WHERE c.memoir.id IN :memoirIds GROUP BY c.memoir.id")
	List<MemoirCommentCount> countByMemoirIdIn(@Param("memoirIds") List<Long> memoirIds);

	interface MemoirCommentCount {
		Long getMemoirId();

		Long getCommentCount();
	}
}
