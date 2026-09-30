package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.inbound.story.RecordStoryArticleUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.model.RecordStoryArticleCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.RecordStoryArticleResult

/**
 * 받은 기사 기록 명령을 기록하고 지정한 결과를 돌려주는 fake.
 */
class RecordingRecordStoryArticleUseCase : RecordStoryArticleUseCase {

    val commands = mutableListOf<RecordStoryArticleCommand>()
    var failure: Throwable? = null

    override suspend fun record(command: RecordStoryArticleCommand): RecordStoryArticleResult {
        commands += command
        failure?.let { throw it }

        return RecordStoryArticleResult(storyId = command.storyId, replayed = false, summary = null)
    }
}
