package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.dto.keyword.ExpandKeywordsCommand
import me.rgunny.kachi.ai.application.port.dto.keyword.ExpandKeywordsResult
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsUseCase

/**
 * 항상 실패하는 키워드 확장 유스케이스 fake.
 *
 * scheduler가 예외를 삼켜 다음 tick을 살려 두는지 확인하는 데 사용한다.
 */
class FailingExpandKeywordsUseCase : ExpandKeywordsUseCase {
    var invokeCount = 0

    override suspend fun expand(command: ExpandKeywordsCommand): ExpandKeywordsResult {
        invokeCount += 1
        throw IllegalStateException("keyword expansion failed")
    }
}
