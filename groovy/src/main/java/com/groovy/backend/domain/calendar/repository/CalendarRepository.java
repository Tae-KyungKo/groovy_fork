package com.groovy.backend.domain.calendar.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.domain.calendar.Calendar;

public interface CalendarRepository extends JpaRepository<Calendar, Long> {

	List<Calendar> findByUserIdAndStudyIsNull(Long userId);

	// CalendarEventResponse.from이 스터디 일정마다 calendar.getStudy().getTitle()/isLeader()를 호출하는데,
	// study가 LAZY라 fetch join 없이는 일정에 걸린 스터디 개수만큼 studies를 추가로 SELECT하는 N+1이 발생한다
	// (Study.meetingDays가 EAGER+SUBSELECT라 스터디당 study_meeting_days 서브쿼리까지 덩달아 실행됨).
	// join fetch로 한 번에 묶어 로딩해 이 문제를 없앤다.
	@Query("SELECT c FROM Calendar c JOIN FETCH c.study WHERE c.study.id IN :studyIds")
	List<Calendar> findByStudyIdIn(@Param("studyIds") List<Long> studyIds);
}
