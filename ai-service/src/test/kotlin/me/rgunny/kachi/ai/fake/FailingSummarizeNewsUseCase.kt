package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizeNewsResult
import me.rgunny.kachi.ai.application.port.inbound.news.SummarizeNewsUseCase

/**
 * 항상 실패하는 뉴스 요약 유스케이스 fake.
 *
 * scheduler가 예외를 삼켜 다음 tick을 살려 두는지 확인하는 데 쓴다.
 */
class FailingSummarizeNewsUseCase : SummarizeNewsUseCase {
    var invokeCount = 0

    override suspend fun summarize(command: SummarizeNewsCommand): SummarizeNewsResult {
        invokeCount += 1
        throw IllegalStateException("news summary failed")
    }
}
