package me.rgunny.kachi.ai.domain.llm

import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StorySummaryPrompt")
class StorySummaryPromptTest {

    @Test
    @DisplayName("기사 수와 필드 길이를 상한으로 자른다")
    fun truncateArticles() {
        val articles = (1..StorySummaryPrompt.MAX_ARTICLES + 5).map { index ->
            StorySummaryPrompt.Article(
                source = "S".repeat(100),
                title = "T".repeat(500),
                excerpt = "E".repeat(1000),
                publishedAt = AiTestFixture.NOW
            ).copy(title = "T$index" + "T".repeat(500))
        }

        val input = StorySummaryPrompt.input(
            keywords = listOf(AiKeyword.of("NVIDIA")),
            previousSummary = null,
            articles = articles
        )

        assertEquals(StorySummaryPrompt.MAX_ARTICLES, input.articles.size)
        assertEquals(StorySummaryPrompt.MAX_SOURCE_LENGTH, input.articles.first().source.length)
        assertEquals(StorySummaryPrompt.MAX_TITLE_LENGTH, input.articles.first().title.length)
        assertEquals(StorySummaryPrompt.MAX_EXCERPT_LENGTH, input.articles.first().excerpt.length)
    }

    @Test
    @DisplayName("키워드 수를 상한으로 자르고 직전 요약이 없으면 null을 그대로 둔다")
    fun truncateKeywordsAndKeepNullPrevious() {
        val keywords = (1..StorySummaryPrompt.MAX_KEYWORDS + 3).map { AiKeyword.of("k$it") }

        val input = StorySummaryPrompt.input(
            keywords = keywords,
            previousSummary = null,
            articles = listOf(
                StorySummaryPrompt.Article(source = "GOOGLE", title = "t", excerpt = "e", publishedAt = AiTestFixture.NOW)
            )
        )

        assertEquals(StorySummaryPrompt.MAX_KEYWORDS, input.storyKeywords.size)
        assertNull(input.previousSummary)
    }

    @Test
    @DisplayName("직전 요약은 그대로 싣는다")
    fun keepPreviousSummary() {
        val input = StorySummaryPrompt.input(
            keywords = listOf(AiKeyword.of("NVIDIA")),
            previousSummary = StorySummaryPrompt.PreviousSummary(title = "이전 제목", content = "이전 본문"),
            articles = listOf(
                StorySummaryPrompt.Article(source = "GOOGLE", title = "t", excerpt = "e", publishedAt = AiTestFixture.NOW)
            )
        )

        assertEquals("이전 제목", input.previousSummary?.title)
        assertEquals("이전 본문", input.previousSummary?.content)
    }
}
