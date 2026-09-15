package me.rgunny.kachi.story.adapter.inbound.web

/**
 * story-service HTTP API Path Contract.
 *
 * Controller mapping은 리소스 경로만 선언하고, API version prefix는 ApiVersionConfig에서 붙인다.
 */
object ApiPaths {
    const val INTERNAL_STORIES = "/internal/stories"
    const val INTERNAL_STORY = "/internal/stories/{storyId}"
    const val INTERNAL_STORY_MERGE = "/internal/stories/{storyId}/merge/{mergedStoryId}"
    const val INTERNAL_STORY_SPLIT = "/internal/stories/{storyId}/split"
    const val INTERNAL_STORY_INDEX_REBUILD = "/internal/story-index/rebuild"

    const val V1_INTERNAL_STORIES = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_STORIES"
    const val V1_INTERNAL_STORY = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_STORY"
    const val V1_INTERNAL_STORY_MERGE = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_STORY_MERGE"
    const val V1_INTERNAL_STORY_SPLIT = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_STORY_SPLIT"
    const val V1_INTERNAL_STORY_INDEX_REBUILD = "${ApiVersions.V1_PATH_PREFIX}$INTERNAL_STORY_INDEX_REBUILD"
}
