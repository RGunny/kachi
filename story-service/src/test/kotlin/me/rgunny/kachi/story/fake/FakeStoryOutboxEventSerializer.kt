package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxEventSerializer
import me.rgunny.kachi.story.application.port.outbound.outbox.model.StoryOutboxEvent

/**
 * 호출만 기록하는 serializer.
 */
class FakeStoryOutboxEventSerializer : StoryOutboxEventSerializer {
    val serialized: MutableList<StoryOutboxEvent> = mutableListOf()
    var failure: RuntimeException? = null

    override fun serialize(event: StoryOutboxEvent): String {
        failure?.let { throw it }
        serialized.add(event)

        return payloadOf(event)
    }

    companion object {
        fun payloadOf(event: StoryOutboxEvent): String {
            return "payload:${event.type}:${event.eventKey}"
        }
    }
}
