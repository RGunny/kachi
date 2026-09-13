package me.rgunny.kachi.story.adapter.inbound.messaging

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.collector.contract.CollectorNewsCollectedEvent
import me.rgunny.kachi.collector.contract.CollectorNewsSource
import me.rgunny.kachi.story.domain.ArticleLanguage
import me.rgunny.kachi.story.domain.ArticleSource
import me.rgunny.kachi.story.domain.EmbeddingText
import me.rgunny.kachi.story.domain.StoryKeyword
import me.rgunny.kachi.story.fixture.StoryTestFixture.NEWS_ID
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("NewsCollectedEventMapper")
class NewsCollectedEventMapperTest {

    @Test
    @DisplayName("계약의 값을 붙일 기사 명령으로 옮긴다")
    fun mapEvent() {
        val command = NewsCollectedEventMapper.toCommand(event())

        assertEquals(NEWS_ID, command.newsId)
        assertEquals(ArticleSource.NAVER, command.source)
        assertEquals("NVIDIA 실적 발표", command.title)
        assertEquals("엔비디아가 2분기 실적을 발표했다", command.excerpt)
        assertEquals("https://kachi.com/news/1", command.url)
        assertEquals(ArticleLanguage.of("ko"), command.language)
        assertEquals(NOW.minus(Duration.ofHours(1)), command.publishedAt)
        assertEquals(NOW, command.collectedAt)
        assertEquals(listOf(StoryKeyword.of("nvidia"), StoryKeyword.of("실적")), command.matchedKeywords)
        assertEquals(EmbeddingText.of("NVIDIA 실적 발표", "엔비디아가 2분기 실적을 발표했다"), command.embeddingText)
    }

    @Test
    @DisplayName("지원하지 않는 schemaVersion은 거부한다")
    fun rejectUnsupportedSchemaVersion() {
        val error = assertFailsWith<IllegalArgumentException> {
            NewsCollectedEventMapper.toCommand(event(schemaVersion = CollectorNewsCollectedEvent.CURRENT_SCHEMA_VERSION + 1))
        }

        assertEquals("unsupported collector news collected schemaVersion=2", error.message)
    }

    @Test
    @DisplayName("newsId가 UUID가 아니면 거부한다")
    fun rejectMalformedNewsId() {
        assertFailsWith<IllegalArgumentException> { NewsCollectedEventMapper.toCommand(event(newsId = "news-1")) }
    }

    @Test
    @DisplayName("언어 부호는 기본 부호로 정규화한다")
    fun normalizeLanguage() {
        assertEquals(ArticleLanguage.of("en"), NewsCollectedEventMapper.toCommand(event(language = "en-US")).language)
    }

    @Test
    @DisplayName("빈 제목이나 빈 키워드 목록은 명령 검증에서 거부된다")
    fun rejectBlankTitleAndEmptyKeywords() {
        assertFailsWith<IllegalArgumentException> { NewsCollectedEventMapper.toCommand(event(title = " ")) }
        assertFailsWith<IllegalArgumentException> { NewsCollectedEventMapper.toCommand(event(matchedKeywords = emptyList())) }
    }

    @ParameterizedTest
    @EnumSource(CollectorNewsSource::class)
    @DisplayName("계약의 source는 이름이 같은 도메인 값으로 옮겨진다")
    fun mapEverySource(source: CollectorNewsSource) {
        assertEquals(source.name, NewsCollectedEventMapper.toCommand(event(source = source)).source.name)
    }

    private fun event(
        schemaVersion: Int = CollectorNewsCollectedEvent.CURRENT_SCHEMA_VERSION,
        newsId: String = NEWS_ID.value.toString(),
        source: CollectorNewsSource = CollectorNewsSource.NAVER,
        title: String = "NVIDIA 실적 발표",
        language: String = "ko",
        matchedKeywords: List<String> = listOf("nvidia", "실적")
    ): CollectorNewsCollectedEvent {
        return CollectorNewsCollectedEvent(
            schemaVersion = schemaVersion,
            newsId = newsId,
            source = source,
            title = title,
            excerpt = "엔비디아가 2분기 실적을 발표했다",
            url = "https://kachi.com/news/1",
            language = language,
            publishedAt = NOW.minus(Duration.ofHours(1)),
            collectedAt = NOW,
            matchedKeywords = matchedKeywords
        )
    }
}
