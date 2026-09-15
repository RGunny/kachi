package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.inbound.outbox.RecoverStoryOutboxUseCase
import me.rgunny.kachi.story.application.port.inbound.outbox.model.RecoverStoryOutboxCommand
import me.rgunny.kachi.story.application.port.inbound.outbox.model.RecoverStoryOutboxResult

/**
 * 복구 명령을 기록하고 정해 둔 결과나 실패를 돌려주는 outbox 복구 유스케이스.
 */
class RecordingRecoverStoryOutboxUseCase : RecoverStoryOutboxUseCase {
    var result: RecoverStoryOutboxResult? = null
    var failure: Throwable? = null
    var lastCommand: RecoverStoryOutboxCommand? = null

    override suspend fun recover(command: RecoverStoryOutboxCommand): RecoverStoryOutboxResult {
        lastCommand = command

        failure?.let { throw it }

        return requireNotNull(result) { "복구 결과를 정하지 않았습니다" }
    }
}
