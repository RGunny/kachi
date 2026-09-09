package me.rgunny.kachi.story.domain

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.story.fixture.StoryTestFixture.STORY_ID
import me.rgunny.kachi.story.fixture.StoryTestFixture.article
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("StoryArticle")
class StoryArticleTest {
    private val otherStoryId = StoryId.of(UUID.fromString("018f0000-0000-7000-8000-000000000002"))

    @Nested
    @DisplayName("create()")
    inner class Create {

        @Test
        @DisplayName("중복 키워드는 제거한다")
        fun removeDuplicatedKeywords() {
            val article = article(keywords = listOf("nvidia", "nvidia"))

            assertEquals(listOf(StoryKeyword.of("nvidia")), article.matchedKeywords)
        }

        @Test
        @DisplayName("제목·발췌문·URL·키워드 중 하나라도 비면 거부한다")
        fun rejectBlankFields() {
            assertFailsWith<IllegalArgumentException> { article(title = " ") }
            assertFailsWith<IllegalArgumentException> { article(excerpt = " ") }
            assertFailsWith<IllegalArgumentException> { article(url = " ") }
            assertFailsWith<IllegalArgumentException> { article(keywords = emptyList()) }
        }

        @Test
        @DisplayName("붙였다고 판정한 story와 소속이 다르면 거부한다")
        fun rejectMismatchedMergeDecision() {
            assertFailsWith<IllegalArgumentException> {
                article(storyId = STORY_ID, decision = AutoMergedLinkDecision(otherStoryId, 0.9))
            }
        }

        @Test
        @DisplayName("붙이지 않은 판정의 후보는 소속과 달라도 된다")
        fun allowCandidateOfNewStory() {
            val article = article(storyId = STORY_ID, decision = NewStoryLinkDecision(otherStoryId, 0.4))

            assertEquals(STORY_ID, article.storyId)
        }

        @Test
        @DisplayName("임베딩 텍스트는 제목과 발췌문이다")
        fun embeddingText() {
            assertEquals(EmbeddingText.of("제목", "발췌문"), article(title = "제목", excerpt = "발췌문").embeddingText)
        }
    }

    @Nested
    @DisplayName("reassign()")
    inner class Reassign {

        @Test
        @DisplayName("소속만 바뀌고 판정 기록은 그대로다")
        fun moveStoryOnly() {
            val original = article()

            val moved = original.reassign(otherStoryId)

            assertEquals(otherStoryId, moved.storyId)
            assertEquals(original.decision, moved.decision)
            assertEquals(original.newsId, moved.newsId)
        }

        @Test
        @DisplayName("같은 story로는 옮길 수 없다")
        fun rejectSameStory() {
            assertFailsWith<IllegalArgumentException> { article().reassign(STORY_ID) }
        }
    }
}
