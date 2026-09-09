package me.rgunny.kachi.collector.adapter.outbound.outbox

import me.rgunny.kachi.collector.application.port.outbound.outbox.model.CollectorOutboxEvent
import me.rgunny.kachi.collector.application.port.outbound.outbox.model.NewsCollectedEvent
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals

/**
 * payload 형식을 발행 계약으로 고정하는 테스트.
 *
 * outbox 행의 키(type, eventKey, partitionKey)는 payload에 들어가지 않고, 발행자가 행에서 읽는다.
 */
@DisplayName("JacksonCollectorOutboxEventSerializer")
class JacksonCollectorOutboxEventSerializerTest {
    private val jsonMapper = JsonMapper.builder().build()
    private val serializer = JacksonCollectorOutboxEventSerializer(jsonMapper)

    @Test
    @DisplayName("기사 수집 이벤트를 계약대로 직렬화한다")
    fun serializeNewsCollected() {
        val news = CollectorTestFixture.news()
        val event = NewsCollectedEvent.from(news)

        val payload = parse(serializer.serialize(event))

        assertEquals(
            setOf("schemaVersion", "newsId", "source", "title", "excerpt", "url", "language", "publishedAt", "collectedAt", "matchedKeywords"),
            payload.keys
        )
        assertEquals(CollectorOutboxEvent.CURRENT_SCHEMA_VERSION, payload["schemaVersion"])
        assertEquals(news.id.value.toString(), payload["newsId"])
        assertEquals("NAVER", payload["source"])
        assertEquals("NVIDIA 실적 발표", payload["title"])
        assertEquals("엔비디아가 2분기 실적을 발표했다", payload["excerpt"])
        assertEquals("https://kachi.com/news/1", payload["url"])
        assertEquals("ko", payload["language"])
        assertEquals("2026-05-29T23:00:00Z", payload["publishedAt"])
        assertEquals("2026-05-30T00:00:00Z", payload["collectedAt"])
        assertEquals(listOf("NVIDIA"), payload["matchedKeywords"])
    }

    private fun parse(json: String): Map<*, *> {
        return jsonMapper.readValue(json, Map::class.java)
    }
}
