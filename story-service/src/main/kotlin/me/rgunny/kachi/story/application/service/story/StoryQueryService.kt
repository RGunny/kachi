package me.rgunny.kachi.story.application.service.story

import me.rgunny.kachi.story.application.exception.StoryOperationErrorCode
import me.rgunny.kachi.story.application.exception.StoryOperationException
import me.rgunny.kachi.story.application.port.inbound.story.FindStoriesUseCase
import me.rgunny.kachi.story.application.port.inbound.story.GetStoryUseCase
import me.rgunny.kachi.story.application.port.inbound.story.model.FindStoriesQuery
import me.rgunny.kachi.story.application.port.inbound.story.model.FindStoriesResult
import me.rgunny.kachi.story.application.port.inbound.story.model.StoryDetail
import me.rgunny.kachi.story.application.port.outbound.story.StoryArticlePersistencePort
import me.rgunny.kachi.story.application.port.outbound.story.StoryPersistencePort
import me.rgunny.kachi.story.domain.StoryId
import org.springframework.stereotype.Service

/**
 * story 목록과 상세를 읽는 유스케이스.
 */
@Service
class StoryQueryService(
    private val storyPersistencePort: StoryPersistencePort,
    private val storyArticlePersistencePort: StoryArticlePersistencePort
) : FindStoriesUseCase, GetStoryUseCase {

    override suspend fun find(query: FindStoriesQuery): FindStoriesResult {
        return FindStoriesResult(
            stories = storyPersistencePort.find(
                status = query.status,
                openedAfter = query.openedAfter,
                limit = query.limit
            )
        )
    }

    override suspend fun get(storyId: StoryId): StoryDetail {
        val story = storyPersistencePort.findById(storyId)
            ?: throw StoryOperationException(StoryOperationErrorCode.STORY_NOT_FOUND, "storyId=${storyId.value}")

        return StoryDetail(
            story = story,
            articles = storyArticlePersistencePort.findByStory(storyId)
        )
    }
}
