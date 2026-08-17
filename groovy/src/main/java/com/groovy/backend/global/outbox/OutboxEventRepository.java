package com.groovy.backend.global.outbox;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

	// id 오름차순 = 발생 순서. 배치 크기를 50으로 제한해 한 번의 폴링이 무한정 길어지지 않게 한다.
	List<OutboxEvent> findTop50ByPublishedFalseOrderByIdAsc();
}
