package me.rgunny.kachi.story.application.port.outbound.story

import java.time.Instant
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryStatus

/**
 * story 저장소 출력 포트.
 */
interface StoryPersistencePort {

    suspend fun save(story: Story): Story

    suspend fun findById(id: StoryId): Story?

    suspend fun findByIds(ids: Collection<StoryId>): List<Story>

    /**
     * [expectedVersion]은 읽어 온 story의 version이다.
     */
    suspend fun update(story: Story, expectedVersion: Long): Boolean

    /**
     * `lastArticleAt`이 [threshold] 이전인 OPEN story를 오래된 순으로 읽는다.
     */
    suspend fun findOpenWithLastArticleBefore(threshold: Instant, limit: Int): List<Story>

    /**
     * 상태와 시작 시각으로 story를 최근 순으로 읽는다.
     */
    suspend fun find(status: StoryStatus?, openedAfter: Instant?, limit: Int): List<Story>
}
