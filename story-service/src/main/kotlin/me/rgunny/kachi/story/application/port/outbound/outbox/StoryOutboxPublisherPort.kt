package me.rgunny.kachi.story.application.port.outbound.outbox

import me.rgunny.kachi.story.domain.outbox.StoryOutbox

/**
 * 기록된 outbox 이벤트를 broker로 내보내는 출력 포트.
 */
interface StoryOutboxPublisherPort {

    suspend fun publish(outbox: StoryOutbox)
}
