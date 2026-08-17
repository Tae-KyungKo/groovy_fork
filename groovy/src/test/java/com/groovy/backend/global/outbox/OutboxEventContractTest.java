package com.groovy.backend.global.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groovy.backend.global.notification.NotificationPayload;

/**
 * Phase 13: legacy-monolith(Producer)와 notification-service(Consumer)가 Kafka로 주고받는
 * OutboxEnvelope+NotificationPayload에 대한 Message Contract Test.
 *
 * legacy는 별도 Gradle 빌드라 notification-service와 컴파일 타임에 클래스를 공유할 수 없다
 * (TracingConfig를 서비스마다 복제한 것과 같은 이유). 대신 두 프로젝트의 test resources에
 * 동일한 JSON 픽스처(contracts/application-received-event.json)를 "계약"으로 두고,
 * 양쪽에서 각자 자신의 클래스로 그 픽스처를 검증하는 소비자 주도 계약 테스트
 * (Consumer-Driven Contract Test) 방식을 쓴다 — notification-service 쪽의 대응 테스트는
 * NotificationEventConsumerContractTest 참고.
 *
 * 이 테스트가 잡아내는 것: legacy가 필드를 이름 변경/추가/삭제하면 아래 두 테스트 중 하나가
 * 반드시 깨진다 — 픽스처를 legacy 클래스로 못 읽거나(역직렬화 실패), legacy가 실제로 만드는
 * JSON의 필드 구성이 픽스처와 달라지거나.
 */
class OutboxEventContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void 계약_픽스처를_legacy의_OutboxEnvelope_NotificationPayload로_역직렬화할_수_있다() throws Exception {
		JsonNode fixture = readFixture();

		OutboxEnvelope envelope = objectMapper.treeToValue(fixture, OutboxEnvelope.class);
		NotificationPayload payload = objectMapper.treeToValue(fixture.get("payload"), NotificationPayload.class);

		assertThat(envelope.eventId()).isEqualTo(fixture.get("eventId").asText());
		assertThat(envelope.eventType()).isEqualTo("APPLICATION_RECEIVED");
		assertThat(payload.recipientUserId()).isEqualTo(1L);
		assertThat(payload.type()).isEqualTo("APPLICATION_RECEIVED");
		assertThat(payload.title()).isNotBlank();
		assertThat(payload.targetId()).isEqualTo(1L);
	}

	@Test
	void legacy가_실제로_직렬화하는_envelope의_필드_구성이_계약과_정확히_같다() throws Exception {
		NotificationPayload payload = new NotificationPayload(1L, "APPLICATION_RECEIVED", "제목", "본문", 1L);
		OutboxEnvelope envelope = new OutboxEnvelope("test-event-id", "APPLICATION_RECEIVED", Instant.now().toString(), payload);

		JsonNode actual = objectMapper.valueToTree(envelope);
		JsonNode contract = readFixture();

		assertThat(fieldNames(actual)).isEqualTo(fieldNames(contract));
		assertThat(fieldNames(actual.get("payload"))).isEqualTo(fieldNames(contract.get("payload")));
	}

	private JsonNode readFixture() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/contracts/application-received-event.json")) {
			return objectMapper.readTree(in);
		}
	}

	private Set<String> fieldNames(JsonNode node) {
		Set<String> names = new TreeSet<>();
		node.fieldNames().forEachRemaining(names::add);
		return names;
	}
}
