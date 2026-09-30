package me.rgunny.kachi.ai.domain.story

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AiStoryArticle")
class AiStoryArticleTest {

    @Test
    @DisplayName("생성 직후에는 미요약이고 제목·발췌문은 다듬어 담는다")
    fun createPendingArticle() {
        val article = AiStoryArticle.create(
            newsId = AiTestFixture.NEWS_ID,
            storyId = AiTestFixture.STORY_ID,
            source = "GOOGLE",
            title = " NVIDIA news ",
            excerpt = " excerpt ",
            url = "https://news.example.com/1",
            publishedAt = AiTestFixture.NOW,
            attachedAt = AiTestFixture.NOW
        )

        assertTrue(article.pending)
        assertNull(article.summarizedInVersion)
        assertEquals("NVIDIA news", article.title)
        assertEquals("excerpt", article.excerpt)
    }

    @Test
    @DisplayName("빈 제목이나 발췌문은 거부한다")
    fun rejectBlankFields() {
        assertFailsWith<IllegalArgumentException> { AiTestFixture.storyArticle(title = " ") }
    }

    @Test
    @DisplayName("요약 버전에 실리면 미요약이 아니고 두 번 실릴 수 없다")
    fun summarizedInVersionOnce() {
        val summarized = AiTestFixture.storyArticle().summarizedIn(1)

        assertEquals(1L, summarized.summarizedInVersion)
        assertFailsWith<IllegalArgumentException> { summarized.summarizedIn(2) }
    }

    @Test
    @DisplayName("미요약 기사만 소속을 옮길 수 있다")
    fun reassignOnlyPending() {
        val reassigned = AiTestFixture.storyArticle().reassign(AiTestFixture.OTHER_STORY_ID)

        assertEquals(AiTestFixture.OTHER_STORY_ID, reassigned.storyId)
        assertTrue(reassigned.pending)
        assertFailsWith<IllegalArgumentException> {
            AiTestFixture.storyArticle().summarizedIn(1).reassign(AiTestFixture.OTHER_STORY_ID)
        }
    }
}
