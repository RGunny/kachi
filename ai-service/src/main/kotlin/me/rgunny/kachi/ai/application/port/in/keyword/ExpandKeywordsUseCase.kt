package me.rgunny.kachi.ai.application.port.`in`.keyword

import me.rgunny.kachi.ai.application.port.dto.keyword.ExpandKeywordsCommand
import me.rgunny.kachi.ai.application.port.dto.keyword.ExpandKeywordsResult

/**
 * 원본 키워드를 관련 검색어로 확장하는 입력 포트
 */
interface ExpandKeywordsUseCase {

    suspend fun expand(command: ExpandKeywordsCommand): ExpandKeywordsResult
}
