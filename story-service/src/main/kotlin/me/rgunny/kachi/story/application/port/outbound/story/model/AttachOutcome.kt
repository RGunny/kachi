package me.rgunny.kachi.story.application.port.outbound.story.model

/**
 * 기사를 붙이는 쓰기의 결과.
 */
enum class AttachOutcome {

    /** 기사·story·outbox가 함께 저장됐다. */
    ATTACHED,

    /** story가 그 사이 바뀌어 아무것도 쓰지 않았다. */
    STORY_CHANGED,

    /** 같은 기사가 이미 있어 아무것도 쓰지 않았다. */
    DUPLICATED
}
