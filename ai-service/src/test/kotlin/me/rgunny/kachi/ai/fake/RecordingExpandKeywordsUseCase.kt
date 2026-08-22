package me.rgunny.kachi.ai.fake

import kotlinx.coroutines.CompletableDeferred
import me.rgunny.kachi.ai.application.port.inbound.keyword.model.ExpandKeywordsCommand
import me.rgunny.kachi.ai.application.port.inbound.keyword.model.ExpandKeywordsResult
import me.rgunny.kachi.ai.application.port.inbound.keyword.ExpandKeywordsUseCase
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.fixture.AiTestFixture

/**
 * 전달받은 command를 그대로 보관하는 키워드 확장 유스케이스 fake.
 *
 * scheduler가 설정값을 command로 옳게 옮겼는지 확인하는 데 사용한다.
 * [hold]를 걸면 실행이 끝나지 않아 중복 실행이 겹치는 순간을 만들 수 있다.
 */
class RecordingExpandKeywordsUseCase : ExpandKeywordsUseCase {
    var invokeCount = 0
    var lastCommand: ExpandKeywordsCommand? = null
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun expand(command: ExpandKeywordsCommand): ExpandKeywordsResult {
        invokeCount += 1
        lastCommand = command
        hold?.await()

        return ExpandKeywordsResult.from(
            AiTestFixture.completedRun(
                targetType = AiRunTargetType.KEYWORD_EXPANSION,
                requestedKeywords = command.keywords.size
            )
        )
    }
}
