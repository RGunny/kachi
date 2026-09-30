package me.rgunny.kachi.ai.domain.summary

/**
 * story 요약 한 버전의 직전 버전 대비 전개 종류.
 *
 * 첫 버전은 항상 [DEVELOPMENT]다.
 */
enum class StoryDevelopmentKind {
    /** 새 기사 전부가 이 story와 다른 사건으로 판정된 전개. */
    NEW_STORY,

    /** 직전 요약에 없던 정보가 더해진 전개. */
    DEVELOPMENT,

    /** 직전 요약 대비 새 정보가 없는 전개. */
    NO_CHANGE
}
