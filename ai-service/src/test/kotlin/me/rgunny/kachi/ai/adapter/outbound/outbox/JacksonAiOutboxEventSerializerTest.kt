package me.rgunny.kachi.ai.adapter.outbound.outbox

import me.rgunny.kachi.ai.application.port.outbound.outbox.model.AiOutboxEvent
import me.rgunny.kachi.ai.application.port.outbound.outbox.model.KeywordQuarantinedEvent
import me.rgunny.kachi.ai.application.port.outbound.outbox.model.SummaryCreatedEvent
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals

/**
 * payload 형식을 발행 계약으로 고정하는 테스트.
 *
 * outbox 행의 키(type, eventKey, partitionKey)는 payload에 들어가지 않고, 발행자가 행에서 읽는다.
 */
@DisplayName("JacksonAiOutboxEventSerializer")
class JacksonAiOutboxEventSerializerTest {
    private val jsonMapper = JsonMapper.builder().build()
    private val serializer = JacksonAiOutboxEventSerializer(jsonMapper)

    @Test
    @DisplayName("요약 생성 이벤트를 계약대로 직렬화한다")
    fun serializeSummaryCreated() {
        val summary = AiTestFixture.newsSummary()
        val event = SummaryCreatedEvent.from(summary)

        val payload = parse(serializer.serialize(event))

        assertEquals(setOf("schemaVersion", "summaryId", "keyword", "title", "content", "sentiment",
            "sourceNewsCount", "provider", "model", "promptVersion", "createdAt"), payload.keys)
        assertEquals(AiOutboxEvent.CURRENT_SCHEMA_VERSION, payload["schemaVersion"])
        assertEquals(summary.id.value.toString(), payload["summaryId"])
        assertEquals("NVIDIA", payload["keyword"])
        assertEquals(summary.title, payload["title"])
        assertEquals(summary.content, payload["content"])
        assertEquals("NEUTRAL", payload["sentiment"])
        assertEquals(1, payload["sourceNewsCount"])
        assertEquals(AiTestFixture.PROVIDER.code, payload["provider"])
        assertEquals(AiTestFixture.MODEL, payload["model"])
        assertEquals(AiTestFixture.NEWS_SUMMARY_PROMPT_VERSION.value, payload["promptVersion"])
        assertEquals("2026-06-03T00:00:00Z", payload["createdAt"])
    }

    @Test
    @DisplayName("키워드 격리 이벤트를 계약대로 직렬화한다")
    fun serializeKeywordQuarantined() {
        val quarantine = AiTestFixture.quarantine(consecutiveFailures = 3)
        val event = KeywordQuarantinedEvent.from(quarantine)

        val payload = parse(serializer.serialize(event))

        assertEquals(setOf("schemaVersion", "quarantineId", "targetType", "keyword", "consecutiveFailures",
            "lastFailureReason", "quarantinedAt"), payload.keys)
        assertEquals(AiOutboxEvent.CURRENT_SCHEMA_VERSION, payload["schemaVersion"])
        assertEquals(quarantine.id.value.toString(), payload["quarantineId"])
        assertEquals("NEWS_SUMMARY", payload["targetType"])
        assertEquals("NVIDIA", payload["keyword"])
        assertEquals(3, payload["consecutiveFailures"])
        assertEquals("UNKNOWN", payload["lastFailureReason"])
        assertEquals("2026-06-03T00:00:00Z", payload["quarantinedAt"])
    }

    private fun parse(json: String): Map<*, *> {
        return jsonMapper.readValue(json, Map::class.java)
    }
}
