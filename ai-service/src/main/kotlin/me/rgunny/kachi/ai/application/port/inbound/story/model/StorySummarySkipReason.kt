package me.rgunny.kachi.ai.application.port.inbound.story.model

/**
 * 요약을 실행하지 않고 끝낸 이유.
 */
enum class StorySummarySkipReason {
    /** 요약할 미요약 기사가 없는 story(상태 없음·흡수됨 포함). */
    NO_PENDING,

    /** 격리 상태인 story. */
    QUARANTINED,

    /** 같은 버전을 다른 실행이 먼저 저장한 저장 경합 패배. */
    SUPERSEDED
}
