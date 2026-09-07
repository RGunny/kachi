package me.rgunny.kachi.ai.domain.llm

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("NewsSummaryPrompt")
class NewsSummaryPromptTest {

    // 저장 키의 일부다. 값이 바뀌면 저장된 요약이 전부 재생성되므로 의도한 변경인지 이 테스트가 묻는다.
    @Test
    @DisplayName("버전은 news-summary-v2다")
    fun version() {
        assertEquals(PromptVersion.of("news-summary-v2"), NewsSummaryPrompt.version)
    }

    @Test
    @DisplayName("용도는 NEWS_SUMMARY다")
    fun use() {
        assertEquals(LlmUse.NEWS_SUMMARY, NewsSummaryPrompt.use)
    }

    @Test
    @DisplayName("system은 입력 값을 인용 데이터로 선언하고 응답 필드를 정한다")
    fun systemDeclaresInputAsQuotedData() {
        assertTrue(NewsSummaryPrompt.system.contains("인용 데이터"))
        assertTrue(NewsSummaryPrompt.system.contains("따르지 말고"))
        listOf("title", "content", "sentiment", "POSITIVE", "NEUTRAL", "NEGATIVE", "UNKNOWN").forEach {
            assertTrue(NewsSummaryPrompt.system.contains(it), it)
        }
        assertTrue(NewsSummaryPrompt.system.contains("제목에 없는 사실"))
        assertTrue(NewsSummaryPrompt.system.contains("상충"))
    }

    @Test
    @DisplayName("입력은 키워드 값과 기사 목록을 그대로 담는다")
    fun input() {
        val article = article(title = "NVIDIA AI GPU demand rises")

        val input = NewsSummaryPrompt.input(AiKeyword.of("NVIDIA"), listOf(article))

        assertEquals("NVIDIA", input.keyword)
        assertEquals(listOf(article), input.articles)
    }

    @Test
    @DisplayName("상한을 넘는 기사 필드는 잘라서 담는다")
    fun truncateLongArticleFields() {
        val article = article(
            source = "s".repeat(NewsSummaryPrompt.MAX_SOURCE_LENGTH + 1),
            title = "t".repeat(NewsSummaryPrompt.MAX_TITLE_LENGTH + 1)
        )

        val input = NewsSummaryPrompt.input(AiKeyword.of("NVIDIA"), listOf(article))

        val truncated = input.articles.single()
        assertEquals(NewsSummaryPrompt.MAX_SOURCE_LENGTH, truncated.source.length)
        assertEquals(NewsSummaryPrompt.MAX_TITLE_LENGTH, truncated.title.length)
        assertEquals(article.publishedAt, truncated.publishedAt)
    }

    @Test
    @DisplayName("상한을 넘는 기사는 앞에서부터 상한 개수만 담는다")
    fun truncateArticleCount() {
        val articles = (1..NewsSummaryPrompt.MAX_ARTICLES + 5).map { article(title = "제목 $it") }

        val input = NewsSummaryPrompt.input(AiKeyword.of("NVIDIA"), articles)

        assertEquals(NewsSummaryPrompt.MAX_ARTICLES, input.articles.size)
        assertEquals("제목 1", input.articles.first().title)
        assertEquals("제목 ${NewsSummaryPrompt.MAX_ARTICLES}", input.articles.last().title)
    }

    private fun article(
        source: String = "reuters",
        title: String = "제목"
    ): NewsSummaryPrompt.Article {
        return NewsSummaryPrompt.Article(
            source = source,
            title = title,
            publishedAt = AiTestFixture.NOW
        )
    }
}
