package me.rgunny.kachi.story.application.port.outbound.story

import java.time.Instant
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryArticle
import me.rgunny.kachi.story.domain.StoryId

/**
 * 기사 사본과 소속의 저장소 출력 포트.
 */
interface StoryArticlePersistencePort {

    suspend fun findByNewsId(newsId: NewsId): StoryArticle?

    /**
     * story의 기사를 최근에 붙은 순으로 [limit]건 읽는다.
     */
    suspend fun findRecentByStory(storyId: StoryId, limit: Int): List<StoryArticle>

    suspend fun findByStory(storyId: StoryId): List<StoryArticle>

    /**
     * [from]에 속한 기사를 전부 [to]로 옮기고 옮긴 수를 돌려준다.
     */
    suspend fun reassign(from: StoryId, to: StoryId): Long

    /**
     * [threshold] 이후 수집된 기사를 기사 id 순으로 [limit]건 읽는다. [after]가 있으면 그 다음부터다.
     */
    suspend fun findCollectedAfter(threshold: Instant, after: NewsId?, limit: Int): List<StoryArticle>
}
