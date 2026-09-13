package me.rgunny.kachi.story.adapter.outbound.outbox

import java.time.Duration
import kotlin.test.assertEquals
import me.rgunny.kachi.story.application.port.outbound.outbox.model.StoryArticleAttachedEvent
import me.rgunny.kachi.story.application.port.outbound.outbox.model.StoryMergedEvent
import me.rgunny.kachi.story.application.port.outbound.outbox.model.StoryOutboxEvent
import me.rgunny.kachi.story.contract.StoryArticleAttachedEvent as StoryArticleAttachedContract
import me.rgunny.kachi.story.contract.StoryMergedEvent as StoryMergedContract
import me.rgunny.kachi.story.domain.ArticleSource
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.UUID

/**
 * payload 형식을 발행 계약으로 고정하는 테스트.
 *
 * outbox 행의 키(type, eventKey, partitionKey)는 payload에 들어가지 않고, 발행자가 행에서 읽는다.
 */
@DisplayName("JacksonStoryOutboxEventSerializer")
class JacksonStoryOutboxEventSerializerTest {
    private val jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val serializer = JacksonStoryOutboxEventSerializer(jsonMapper)

    @Test
    @DisplayName("기사 부착 이벤트를 계약 필드만으로 직렬화한다")
    fun serializeArticleAttached() {
        val first = StoryTestFixture.article()
        val second = StoryTestFixture.article(
            newsId = NewsId.of(UUID.fromString("018f0000-0000-7000-8000-000000000010")),
            title = "엔비디아 실적 서프라이즈",
            excerpt = "매출이 예상을 넘었다",
            url = "https://kachi.com/news/2",
            source = ArticleSource.GOOGLE,
            publishedAt = StoryTestFixture.NOW.minus(Duration.ofMinutes(30)),
            keywords = listOf("nvidia", "실적")
        )
        val story = StoryTestFixture.story(first).attach(second, StoryTestFixture.NOW)
        val event = StoryArticleAttachedEvent.from(story, second)

        val payload = parse(serializer.serialize(event))

        assertEquals(
            setOf("schemaVersion", "storyId", "newsId", "title", "excerpt", "url", "source", "publishedAt", "storyKeywords", "storyArticleCount", "attachedAt"),
            payload.keys
        )
        assertEquals(StoryOutboxEvent.CURRENT_SCHEMA_VERSION, payload["schemaVersion"])
        assertEquals(story.id.value.toString(), payload["storyId"])
        assertEquals("018f0000-0000-7000-8000-000000000010", payload["newsId"])
        assertEquals("엔비디아 실적 서프라이즈", payload["title"])
        assertEquals("매출이 예상을 넘었다", payload["excerpt"])
        assertEquals("https://kachi.com/news/2", payload["url"])
        assertEquals("GOOGLE", payload["source"])
        assertEquals("2026-05-29T23:30:00Z", payload["publishedAt"])
        assertEquals(listOf("nvidia", "실적"), payload["storyKeywords"])
        assertEquals(2, payload["storyArticleCount"])
        assertEquals("2026-05-30T00:00:00Z", payload["attachedAt"])
    }

    @Test
    @DisplayName("기사 부착 payload는 소비자의 계약 타입으로 다시 읽힌다")
    fun articleAttachedPayloadReadsAsContract() {
        val article = StoryTestFixture.article()
        val event = StoryArticleAttachedEvent.from(StoryTestFixture.story(article), article)

        val contract = jsonMapper.readValue(serializer.serialize(event), StoryArticleAttachedContract::class.java)

        assertEquals(StoryArticleAttachedContract.CURRENT_SCHEMA_VERSION, contract.schemaVersion)
        assertEquals(article.storyId.value.toString(), contract.storyId)
        assertEquals(article.newsId.value.toString(), contract.newsId)
        assertEquals(article.title, contract.title)
        assertEquals(1, contract.storyArticleCount)
    }

    @Test
    @DisplayName("병합 이벤트를 계약 필드만으로 직렬화하고 계약 타입으로 다시 읽는다")
    fun serializeMerged() {
        val target = StoryTestFixture.story()
        val absorbed = StoryTestFixture.story(
            first = StoryTestFixture.article(
                newsId = NewsId.of(UUID.fromString("018f0000-0000-7000-8000-000000000010")),
                storyId = StoryId.of(UUID.fromString("018f0000-0000-7000-8000-000000000002"))
            )
        ).mergeInto(target, StoryTestFixture.NOW)
        val event = StoryMergedEvent.from(absorbed)

        val json = serializer.serialize(event)
        val payload = parse(json)
        val contract = jsonMapper.readValue(json, StoryMergedContract::class.java)

        assertEquals(setOf("schemaVersion", "storyId", "mergedStoryId", "mergedAt"), payload.keys)
        assertEquals(target.id.value.toString(), payload["storyId"])
        assertEquals("018f0000-0000-7000-8000-000000000002", payload["mergedStoryId"])
        assertEquals("2026-05-30T00:00:00Z", payload["mergedAt"])
        assertEquals(StoryMergedContract.CURRENT_SCHEMA_VERSION, contract.schemaVersion)
        assertEquals(absorbed.id.value.toString(), contract.mergedStoryId)
    }

    @ParameterizedTest
    @EnumSource(ArticleSource::class)
    @DisplayName("기사 source는 이름이 같은 계약 값으로 옮겨진다")
    fun mapEverySource(source: ArticleSource) {
        val article = StoryTestFixture.article(source = source)
        val event = StoryArticleAttachedEvent.from(StoryTestFixture.story(article), article)

        val payload = parse(serializer.serialize(event))

        assertEquals(source.name, payload["source"])
    }

    private fun parse(json: String): Map<*, *> {
        return jsonMapper.readValue(json, Map::class.java)
    }
}
