package me.rgunny.kachi.story

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.port.inbound.outbox.RelayStoryOutboxUseCase
import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxEventSerializer
import me.rgunny.kachi.story.application.port.outbound.outbox.model.StoryArticleAttachedEvent
import me.rgunny.kachi.story.application.port.outbound.outbox.model.StoryMergedEvent
import me.rgunny.kachi.story.application.port.outbound.outbox.model.StoryOutboxEvent
import me.rgunny.kachi.story.config.StoryEventsProperties
import me.rgunny.kachi.story.contract.StoryArticleAttachedEvent as StoryArticleAttachedContract
import me.rgunny.kachi.story.contract.StoryMergedEvent as StoryMergedContract
import me.rgunny.kachi.story.domain.ArticleSource
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus
import me.rgunny.kachi.story.fixture.StoryTestFixture
import me.rgunny.kachi.story.support.StoryOutboxCollection
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.kafka.KafkaContainer
import tools.jackson.databind.json.JsonMapper

/**
 * relay가 outbox 행을 실제 broker의 story topic으로 내보내는 계약을 본다.
 *
 * payload는 application 이벤트를 serializer로 굳힌 값이고, topic에서 읽은 레코드는 소비자 계약 타입(`story-contract`)으로 검증한다.
 */
@ActiveProfiles("test")
// scheduler tick이 테스트 사이에 끼어들지 않도록 첫 실행을 멀리 미루고, relay는 테스트가 직접 부른다.
@SpringBootTest(
    properties = [
        "kachi.story.outbox.relay.enabled=true",
        "kachi.story.outbox.relay.initial-delay=1h",
        "kachi.story.events.enabled=true"
    ]
)
@Import(StoryServiceTestContainersConfig::class)
class StoryOutboxRelayKafkaIntegrationTest {

    @Autowired
    private lateinit var relayUseCase: RelayStoryOutboxUseCase

    @Autowired
    private lateinit var serializer: StoryOutboxEventSerializer

    @Autowired
    private lateinit var eventsProperties: StoryEventsProperties

    @Autowired
    private lateinit var kafkaContainer: KafkaContainer

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    private val outboxCollection by lazy { StoryOutboxCollection(mongoTemplate) }

    @BeforeEach
    fun clearOutbox() {
        outboxCollection.clear()
    }

    @Test
    @DisplayName("기사 부착 행은 story.article.attached로 나가고 계약 타입으로 읽힌다")
    fun relayArticleAttachedRow() = runBlocking {
        val event = articleAttachedEvent()
        outboxCollection.insert(outboxOf(event))

        val result = relayUseCase.relay()

        assertEquals(1, result.published)
        assertEquals(StoryOutboxStatus.PUBLISHED.name, outboxCollection.findAll().single().status)

        val record = recordsOf(eventsProperties.topics.articleAttached, expected = 1) {
            it.key() == event.partitionKey
        }.single()
        val payload = jsonMapper.readValue(record.value(), StoryArticleAttachedContract::class.java)
        assertEquals(StoryArticleAttachedContract.CURRENT_SCHEMA_VERSION, payload.schemaVersion)
        assertEquals(event.storyId.toString(), payload.storyId)
        assertEquals(event.newsId.toString(), payload.newsId)
        assertEquals(event.title, payload.title)
        assertEquals(event.storyArticleCount, payload.storyArticleCount)
    }

    @Test
    @DisplayName("병합 행은 story.merged로 나가고 계약 타입으로 읽힌다")
    fun relayMergedRow() = runBlocking {
        val event = mergedEvent()
        outboxCollection.insert(outboxOf(event))

        val result = relayUseCase.relay()

        assertEquals(1, result.published)

        val record = recordsOf(eventsProperties.topics.merged, expected = 1) {
            it.key() == event.partitionKey
        }.single()
        val payload = jsonMapper.readValue(record.value(), StoryMergedContract::class.java)
        assertEquals(StoryMergedContract.CURRENT_SCHEMA_VERSION, payload.schemaVersion)
        assertEquals(event.storyId.toString(), payload.storyId)
        assertEquals(event.mergedStoryId.toString(), payload.mergedStoryId)
    }

    @Test
    @DisplayName("한 tick이 종류가 다른 행을 각자의 topic으로 나눠 보낸다")
    fun relayMixedRowsToTheirTopics() = runBlocking {
        val attached = articleAttachedEvent()
        val merged = mergedEvent()
        outboxCollection.insert(outboxOf(attached))
        outboxCollection.insert(outboxOf(merged))

        val result = relayUseCase.relay()

        assertEquals(2, result.published)
        assertEquals(1, recordsOf(eventsProperties.topics.articleAttached, expected = 1) { it.key() == attached.partitionKey }.size)
        assertEquals(1, recordsOf(eventsProperties.topics.merged, expected = 1) { it.key() == merged.partitionKey }.size)
        assertTrue(outboxCollection.findAll().all { it.status == StoryOutboxStatus.PUBLISHED.name })
    }

    private fun outboxOf(event: StoryOutboxEvent): StoryOutbox {
        return StoryOutbox.create(
            eventType = event.type,
            eventKey = event.eventKey,
            partitionKey = event.partitionKey,
            payload = serializer.serialize(event),
            now = StoryTestFixture.NOW
        )
    }

    private fun articleAttachedEvent(): StoryArticleAttachedEvent {
        val now = Instant.now()

        return StoryArticleAttachedEvent(
            storyId = UUID.randomUUID(),
            newsId = UUID.randomUUID(),
            title = "NVIDIA 실적 발표",
            excerpt = "엔비디아가 2분기 실적을 발표했다",
            url = "https://kachi.com/news/1",
            source = ArticleSource.NAVER,
            publishedAt = now.minus(Duration.ofHours(1)),
            storyKeywords = listOf("nvidia"),
            storyArticleCount = 1,
            attachedAt = now
        )
    }

    private fun mergedEvent(): StoryMergedEvent {
        return StoryMergedEvent(
            storyId = UUID.randomUUID(),
            mergedStoryId = UUID.randomUUID(),
            mergedAt = Instant.now()
        )
    }

    private fun recordsOf(
        topic: String,
        expected: Int,
        matches: (ConsumerRecord<String, String>) -> Boolean
    ): List<ConsumerRecord<String, String>> {
        val config = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "story-relay-test-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
        )
        KafkaConsumer<String, String>(config).use { consumer ->
            consumer.subscribe(listOf(topic))
            val found = mutableListOf<ConsumerRecord<String, String>>()
            val deadline = Instant.now().plus(POLL_TIMEOUT)
            while (found.size < expected && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(500)).forEach { record -> if (matches(record)) found += record }
            }
            assertTrue(found.size >= expected, "topic $topic 에서 레코드 $expected 건을 기다렸으나 ${found.size} 건이다")

            return found
        }
    }

    private companion object {
        val POLL_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
