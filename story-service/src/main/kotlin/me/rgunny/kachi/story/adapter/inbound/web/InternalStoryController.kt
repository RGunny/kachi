package me.rgunny.kachi.story.adapter.inbound.web

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.story.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.story.application.port.inbound.merge.MergeStoryPairUseCase
import me.rgunny.kachi.story.application.port.inbound.merge.model.MergeStoryPairCommand
import me.rgunny.kachi.story.application.port.inbound.split.SplitStoryUseCase
import me.rgunny.kachi.story.application.port.inbound.split.model.SplitStoryCommand
import me.rgunny.kachi.story.application.port.inbound.story.FindStoriesUseCase
import me.rgunny.kachi.story.application.port.inbound.story.GetStoryUseCase
import me.rgunny.kachi.story.application.port.inbound.story.model.FindStoriesQuery
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 내부 운영용 story 조회·병합·분리 API.
 */
@RestController
class InternalStoryController(
    private val findStoriesUseCase: FindStoriesUseCase,
    private val getStoryUseCase: GetStoryUseCase,
    private val mergeStoryPairUseCase: MergeStoryPairUseCase,
    private val splitStoryUseCase: SplitStoryUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_STORIES, version = ApiVersions.V1)
    suspend fun findStories(
        @RequestParam(required = false) status: StoryStatus?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<ApiResponse<List<StoryResponse>>> {
        val result = findStoriesUseCase.find(
            FindStoriesQuery(
                status = status,
                openedAfter = from,
                limit = limit ?: FindStoriesQuery.DEFAULT_LIMIT
            )
        )

        return ResponseEntity.ok(ApiResponse.success(result.stories.map(StoryResponse::from)))
    }

    @GetMapping(ApiPaths.INTERNAL_STORY, version = ApiVersions.V1)
    suspend fun getStory(
        @PathVariable storyId: UUID
    ): ResponseEntity<ApiResponse<StoryDetailResponse>> {
        val detail = getStoryUseCase.get(StoryId.of(storyId))

        return ResponseEntity.ok(ApiResponse.success(StoryDetailResponse.from(detail)))
    }

    @PostMapping(ApiPaths.INTERNAL_STORY_MERGE, version = ApiVersions.V1)
    suspend fun mergeStories(
        @PathVariable storyId: UUID,
        @PathVariable mergedStoryId: UUID
    ): ResponseEntity<ApiResponse<MergeStoryPairResponse>> {
        val result = mergeStoryPairUseCase.mergeStoryPair(
            MergeStoryPairCommand(
                targetStoryId = StoryId.of(storyId),
                sourceStoryId = StoryId.of(mergedStoryId)
            )
        )

        return ResponseEntity.ok(ApiResponse.success(MergeStoryPairResponse.from(result)))
    }

    @PostMapping(ApiPaths.INTERNAL_STORY_SPLIT, version = ApiVersions.V1)
    suspend fun splitStory(
        @PathVariable storyId: UUID,
        @RequestBody request: SplitStoryRequest
    ): ResponseEntity<ApiResponse<SplitStoryResponse>> {
        val result = splitStoryUseCase.split(
            SplitStoryCommand(
                storyId = StoryId.of(storyId),
                newsIds = request.newsIds.map(NewsId::of)
            )
        )

        return ResponseEntity.ok(ApiResponse.success(SplitStoryResponse.from(result)))
    }
}
