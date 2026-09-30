package me.rgunny.kachi.story.application.exception

import me.rgunny.kachi.story.domain.outbox.StoryOutboxId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus

/**
 * DEAD가 아닌 행을 복구하려 했을 때의 실패.
 */
class StoryOutboxNotRecoverableException(
    id: StoryOutboxId,
    status: StoryOutboxStatus
) : StoryException(
    errorCode = StoryOutboxErrorCode.OUTBOX_NOT_RECOVERABLE,
    message = messageOf(StoryOutboxErrorCode.OUTBOX_NOT_RECOVERABLE, "id=${id.value}, status=$status")
)
