package me.rgunny.kachi.story.adapter.outbound.outbox

import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType

/**
 * 발행 어댑터가 보는 이벤트 종류별 topic 이름.
 *
 * `kachi.story.events.topics`
 */
data class StoryEventTopics(
    val articleAttached: String,
    val merged: String
) {
    fun topicOf(eventType: StoryOutboxEventType): String {
        return when (eventType) {
            StoryOutboxEventType.ARTICLE_ATTACHED -> articleAttached
            StoryOutboxEventType.MERGED -> merged
        }
    }
}
