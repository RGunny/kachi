package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.inbound.keyword.model.ExpandKeywordsCommand
import me.rgunny.kachi.ai.application.port.inbound.keyword.model.ExpandKeywordsResult
import me.rgunny.kachi.ai.application.port.inbound.keyword.ExpandKeywordsUseCase
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.fixture.AiTestFixture

/**
 * 전달받은 command를 그대로 보관하는 키워드 확장 유스케이스 fake.
 *
 * scheduler가 설정값을 command로 옳게 옮겼는지 확인하는 데 사용한다.
 */
class RecordingExpandKeywordsUseCase : ExpandKeywordsUseCase {
    var invokeCount = 0
    var lastCommand: ExpandKeywordsCommand? = null

    override suspend fun expand(command: ExpandKeywordsCommand): ExpandKeywordsResult {
        invokeCount += 1
        lastCommand = command

        return ExpandKeywordsResult.from(
            AiTestFixture.completedRun(
                targetType = AiRunTargetType.KEYWORD_EXPANSION,
                requestedKeywords = command.keywords.size
            )
        )
    }
}
