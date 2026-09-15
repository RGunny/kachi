package me.rgunny.kachi.story.adapter.inbound.web

import java.time.Instant
import me.rgunny.kachi.story.application.port.inbound.outbox.model.RecoverStoryOutboxResult

/**
 * outbox 복구 응답.
 */
data class RecoverStoryOutboxResponse(
    val outbox: StoryOutboxResponse,
    val recoveredAt: Instant
) {
    companion object {

        fun from(result: RecoverStoryOutboxResult): RecoverStoryOutboxResponse {
            return RecoverStoryOutboxResponse(
                outbox = StoryOutboxResponse.from(result.outbox),
                recoveredAt = result.recoveredAt
            )
        }
    }
}
