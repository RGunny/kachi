package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.inbound.outbox.RelayStoryOutboxUseCase
import me.rgunny.kachi.story.application.port.inbound.outbox.model.RelayStoryOutboxResult
import me.rgunny.kachi.story.fixture.StoryTestFixture

/**
 * 호출 횟수만 기록하고 지정한 집계를 돌려주는 relay 유스케이스 fake.
 *
 * scheduler가 집계에 따라 로그를 가르는지 확인하는 데 쓴다.
 */
class RecordingRelayStoryOutboxUseCase(
    var result: RelayStoryOutboxResult = emptyResult()
) : RelayStoryOutboxUseCase {
    var invokeCount = 0

    override suspend fun relay(): RelayStoryOutboxResult {
        invokeCount += 1

        return result
    }

    companion object {

        fun emptyResult(): RelayStoryOutboxResult {
            return RelayStoryOutboxResult(
                processed = 0,
                published = 0,
                retried = 0,
                dead = 0,
                staleRecovered = 0,
                completedAt = StoryTestFixture.NOW
            )
        }
    }
}
