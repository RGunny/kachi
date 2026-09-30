package me.rgunny.kachi.ai.application.exception

import me.rgunny.kachi.ai.domain.story.StoryId

/**
 * story 상태 CAS 경합이 재시도 상한을 넘었을 때의 실패.
 */
class StoryRecordConflictException(
    storyId: StoryId,
    attempts: Int
) : AiException(
    errorCode = AiStoryErrorCode.STORY_RECORD_CONFLICT_EXHAUSTED,
    message = messageOf(
        AiStoryErrorCode.STORY_RECORD_CONFLICT_EXHAUSTED,
        "storyId=${storyId.value}, attempts=$attempts"
    )
)
