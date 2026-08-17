package com.groovy.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.groovy.backend.domain.calendar.Calendar;

/**
 * MSA 전환 Phase 1: 계획서(Groovy_MSA_전환계획.md)가 명시한 "Calendar → Study Entity 참조 금지" 규칙.
 * Calendar가 Study를 JPA 연관관계(@ManyToOne)로 영속화하면, DB가 공유되는 동안은 편하지만
 * Phase 7(DB per Service)에서 반드시 다시 걷어내야 하는 빚이 된다. studyId(Long)만 들고 있게 강제한다.
 */
class CalendarEntityBoundaryTest {

	@Test
	void calendarEntityMustNotHoldStudyEntityAsField() {
		boolean holdsStudyEntity = Arrays.stream(Calendar.class.getDeclaredFields())
			.map(Field::getType)
			.anyMatch(type -> type.getSimpleName().equals("Study"));

		assertThat(holdsStudyEntity)
			.as("Calendar는 Study 엔티티를 직접 참조하면 안 된다. studyId(Long)만 보관하고, "
				+ "상세 정보는 StudyService의 공개 API로 조회해야 한다.")
			.isFalse();
	}
}
