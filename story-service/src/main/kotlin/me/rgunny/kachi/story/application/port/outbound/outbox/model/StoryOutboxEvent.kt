package me.rgunny.kachi.story.application.port.outbound.outbox.model

import java.time.Instant
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType

/**
 * 발행 대상 story 이벤트.
 */
sealed interface StoryOutboxEvent {
    val schemaVersion: Int
    val type: StoryOutboxEventType

    /** outbox unique index의 값. 같은 이벤트를 두 번 기록하지 않게 막는다. */
    val eventKey: String

    /** 발행 순서를 지켜야 하는 단위. story id이며, 같은 story의 이벤트는 같은 파티션으로 가서 순서가 지켜진다. */
    val partitionKey: String

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** 이벤트를 발행 대기 행으로 만든다. */
fun StoryOutboxEvent.toOutbox(payload: String, now: Instant): StoryOutbox {
    return StoryOutbox.create(
        eventType = type,
        eventKey = eventKey,
        partitionKey = partitionKey,
        payload = payload,
        now = now
    )
}
