package me.rgunny.kachi.story.application.port.inbound.assembly.model

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.story.domain.ArticleLanguage
import me.rgunny.kachi.story.domain.ArticleSource
import me.rgunny.kachi.story.domain.EmbeddingText
import me.rgunny.kachi.story.domain.StoryKeyword
import me.rgunny.kachi.story.fixture.StoryTestFixture.NEWS_ID
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AttachArticleCommand")
class AttachArticleCommandTest {

    @Test
    @DisplayName("임베딩 텍스트는 제목과 발췌문이다")
    fun embeddingText() {
        assertEquals(EmbeddingText.of("제목", "발췌문"), command(title = "제목", excerpt = "발췌문").embeddingText)
    }

    @Test
    @DisplayName("제목·발췌문·URL·키워드 중 하나라도 비면 거부한다")
    fun rejectBlankFields() {
        assertFailsWith<IllegalArgumentException> { command(title = " ") }
        assertFailsWith<IllegalArgumentException> { command(excerpt = " ") }
        assertFailsWith<IllegalArgumentException> { command(url = " ") }
        assertFailsWith<IllegalArgumentException> { command(keywords = emptyList()) }
    }

    private fun command(
        title: String = "NVIDIA 실적 발표",
        excerpt: String = "엔비디아가 2분기 실적을 발표했다",
        url: String = "https://kachi.com/news/1",
        keywords: List<String> = listOf("nvidia")
    ): AttachArticleCommand {
        return AttachArticleCommand(
            newsId = NEWS_ID,
            source = ArticleSource.NAVER,
            title = title,
            excerpt = excerpt,
            url = url,
            language = ArticleLanguage.of("ko"),
            publishedAt = NOW,
            collectedAt = NOW,
            matchedKeywords = keywords.map { StoryKeyword.of(it) }
        )
    }
}
