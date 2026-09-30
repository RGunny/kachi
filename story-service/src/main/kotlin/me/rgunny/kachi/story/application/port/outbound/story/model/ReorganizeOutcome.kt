package me.rgunny.kachi.story.application.port.outbound.story.model

/**
 * story 재편성 쓰기의 결과.
 */
enum class ReorganizeOutcome {

    /** 관련 story·기사·outbox가 함께 저장됐다. */
    REORGANIZED,

    /** story가 그 사이 바뀌어 아무것도 쓰지 않았다. */
    STORY_CHANGED
}
