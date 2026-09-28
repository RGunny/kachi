package me.rgunny.kachi.ai.application.port.outbound.story.model

/**
 * 기사 사본 기록 요청의 결과.
 */
enum class RecordStoryArticleOutcome {
    /** 기사와 story 상태가 함께 저장된 결과. */
    RECORDED,

    /** 같은 newsId의 기사가 이미 있어 저장하지 않은 결과. */
    DUPLICATED,

    /** story 상태 CAS에 져 기사도 저장하지 않은 결과. */
    STORY_CHANGED
}
