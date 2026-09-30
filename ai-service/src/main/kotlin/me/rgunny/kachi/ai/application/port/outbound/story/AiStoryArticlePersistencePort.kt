package me.rgunny.kachi.ai.application.port.outbound.story

import me.rgunny.kachi.ai.application.port.outbound.story.model.RecordStoryArticleOutcome
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.AiStoryArticle
import me.rgunny.kachi.ai.domain.story.StoryId

/**
 * 기사 사본 저장소 출력 포트.
 *
 * newsId는 전역 unique다.
 * 기사 한 건은 story 하나에만 속한다.
 */
interface AiStoryArticlePersistencePort {

    /**
     * 첫 기사와 새 story 상태를 한 트랜잭션으로 저장한다.
     */
    suspend fun openStory(story: AiStory, article: AiStoryArticle): RecordStoryArticleOutcome

    /**
     * 기사 insert와 story 상태 CAS 갱신을 한 트랜잭션으로 쓴다.
     */
    suspend fun attach(
        article: AiStoryArticle,
        story: AiStory,
        expectedVersion: Long
    ): RecordStoryArticleOutcome

    /**
     * 미요약 기사를 attachedAt 오름차순으로 읽는다.
     */
    suspend fun findPendingByStory(storyId: StoryId, limit: Int): List<AiStoryArticle>
}
