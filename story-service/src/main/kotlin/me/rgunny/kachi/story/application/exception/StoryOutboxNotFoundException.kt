package me.rgunny.kachi.story.application.exception

import me.rgunny.kachi.story.domain.outbox.StoryOutboxId

/**
 * 복구 대상 outbox 행이 없을 때의 실패.
 */
class StoryOutboxNotFoundException(
    id: StoryOutboxId
) : StoryException(
    errorCode = StoryOutboxErrorCode.OUTBOX_NOT_FOUND,
    message = messageOf(StoryOutboxErrorCode.OUTBOX_NOT_FOUND, "id=${id.value}")
)
