package me.rgunny.kachi.collector.application.port.outbound.outbox.model

import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxEventType
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("NewsCollectedEvent")
class NewsCollectedEventTest {

    @Test
    @DisplayName("기사에서 발행할 값을 그대로 옮긴다")
    fun mapFromNews() {
        val news = CollectorTestFixture.news()

        val event = NewsCollectedEvent.from(news)

        assertEquals(news.id.value, event.newsId)
        assertEquals(NewsSource.NAVER, event.source)
        assertEquals("NVIDIA 실적 발표", event.title)
        assertEquals("엔비디아가 2분기 실적을 발표했다", event.excerpt)
        assertEquals("https://kachi.com/news/1", event.url)
        assertEquals("ko", event.language)
        assertEquals(news.publishedAt, event.publishedAt)
        assertEquals(CollectorTestFixture.NOW, event.collectedAt)
        assertEquals(listOf("NVIDIA"), event.matchedKeywords)
        assertEquals(CollectorOutboxEvent.CURRENT_SCHEMA_VERSION, event.schemaVersion)
    }

    @Test
    @DisplayName("기사 id가 이벤트 키이자 파티션 키다")
    fun keysComeFromNewsId() {
        val news = CollectorTestFixture.news()

        val event = NewsCollectedEvent.from(news)

        assertEquals(CollectorOutboxEventType.NEWS_COLLECTED, event.type)
        assertEquals(news.id.value.toString(), event.eventKey)
        assertEquals(news.id.value.toString(), event.partitionKey)
    }

    @Test
    @DisplayName("이벤트를 발행 대기 행으로 만든다")
    fun toOutboxRow() {
        val news = CollectorTestFixture.news()
        val event = NewsCollectedEvent.from(news)

        val outbox = event.toOutbox(payload = "{}", now = CollectorTestFixture.NOW)

        assertEquals(CollectorOutboxEventType.NEWS_COLLECTED, outbox.eventType)
        assertEquals(news.id.value.toString(), outbox.eventKey)
        assertEquals("{}", outbox.payload)
    }
}
