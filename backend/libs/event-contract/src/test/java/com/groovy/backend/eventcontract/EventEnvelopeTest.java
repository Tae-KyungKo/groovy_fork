package com.groovy.backend.eventcontract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.groovy.backend.eventcontract.study.StudyLevelUpPayload;

import java.util.List;

class EventEnvelopeTest {

	@Test
	void ofWrapsPayloadWithGeneratedIdAndTimestamp() {
		StudyLevelUpPayload payload = new StudyLevelUpPayload(List.of(1L, 2L), 10L, "스터디", 2);

		EventEnvelope<StudyLevelUpPayload> envelope = EventEnvelope.of(EventTypes.STUDY_LEVEL_UP, payload);

		assertNotNull(envelope.eventId());
		assertNotNull(envelope.occurredAt());
		assertEquals(EventTypes.STUDY_LEVEL_UP, envelope.eventType());
		assertEquals(1, envelope.schemaVersion());
		assertEquals(payload, envelope.payload());
	}
}
