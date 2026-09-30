package me.rgunny.kachi.story.application.port.outbound.outbox

import me.rgunny.kachi.story.application.port.outbound.outbox.model.StoryOutboxEvent

/**
 * 도메인 이벤트를 outbox payload 문자열로 바꾸는 출력 포트.
 */
interface StoryOutboxEventSerializer {

    fun serialize(event: StoryOutboxEvent): String
}
