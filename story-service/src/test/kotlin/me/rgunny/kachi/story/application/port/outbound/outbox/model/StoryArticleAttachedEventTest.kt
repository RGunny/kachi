package me.rgunny.kachi.story.application.port.outbound.outbox.model

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import me.rgunny.kachi.story.fixture.StoryTestFixture.article
import me.rgunny.kachi.story.fixture.StoryTestFixture.story
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryArticleAttachedEvent")
class StoryArticleAttachedEventTest {

    @Test
    @DisplayName("기사 내용과 붙인 뒤 story의 키워드·기사 수를 싣는다")
    fun snapshotOfStoryAfterAttach() {
        val first = article(keywords = listOf("nvidia"))
        val second = article(newsId = NewsId.of(UUID.randomUUID()), keywords = listOf("ai"))
        val story = story(first).attach(second, NOW)

        val event = StoryArticleAttachedEvent.from(story, second)

        assertEquals(StoryOutboxEventType.ARTICLE_ATTACHED, event.type)
        assertEquals(1, event.schemaVersion)
        assertEquals("${story.id.value}:${second.newsId.value}", event.eventKey)
        assertEquals(story.id.value.toString(), event.partitionKey)
        assertEquals(second.title, event.title)
        assertEquals(listOf("ai", "nvidia"), event.storyKeywords)
        assertEquals(2, event.storyArticleCount)
        assertEquals(second.attachedAt, event.attachedAt)
    }

    @Test
    @DisplayName("다른 story의 기사로는 만들 수 없다")
    fun rejectForeignArticle() {
        val foreign = article(storyId = StoryId.of(UUID.randomUUID()))

        assertFailsWith<IllegalArgumentException> { StoryArticleAttachedEvent.from(story(), foreign) }
    }
}
