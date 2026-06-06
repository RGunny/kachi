package me.rgunny.kachi.ai.application.port.`in`.keyword

/**
 * 원본 키워드를 관련 검색어로 확장하는 입력 포트
 */
interface ExpandKeywordsUseCase {

    suspend fun expand(command: ExpandKeywordsCommand): ExpandKeywordsResult
}
