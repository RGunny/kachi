package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxPublisherPort
import me.rgunny.kachi.story.domain.outbox.StoryOutbox

/**
 * 발행 시도를 기록하고 지정한 예외를 던지는 fake.
 */
class FakeStoryOutboxPublisherPort : StoryOutboxPublisherPort {
    val published: MutableList<StoryOutbox> = mutableListOf()
    var failure: Throwable? = null

    override suspend fun publish(outbox: StoryOutbox) {
        published.add(outbox)

        failure?.let { throw it }
    }
}
