package me.rgunny.kachi.story.domain.outbox

/**
 * outbox에 기록하는 도메인 이벤트 종류.
 */
enum class StoryOutboxEventType {
    /** 기사 한 건이 story에 붙은 사건. */
    ARTICLE_ATTACHED,

    /** story 하나가 다른 story에 흡수된 사건. */
    MERGED
}
