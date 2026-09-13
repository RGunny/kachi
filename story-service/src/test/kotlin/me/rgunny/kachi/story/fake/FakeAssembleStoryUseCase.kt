package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.inbound.assembly.AssembleStoryUseCase
import me.rgunny.kachi.story.application.port.inbound.assembly.model.AssembleStoryResult
import me.rgunny.kachi.story.application.port.inbound.assembly.model.AttachArticleCommand
import me.rgunny.kachi.story.domain.NewStoryLinkDecision
import me.rgunny.kachi.story.fixture.StoryTestFixture

/**
 * 받은 명령을 기록하고 정해 둔 결과나 실패를 돌려주는 조립 유스케이스.
 */
class FakeAssembleStoryUseCase : AssembleStoryUseCase {

    val commands: MutableList<AttachArticleCommand> = mutableListOf()
    var failure: Throwable? = null

    override suspend fun assemble(command: AttachArticleCommand): AssembleStoryResult {
        commands += command
        failure?.let { throw it }

        return AssembleStoryResult(
            newsId = command.newsId,
            storyId = StoryTestFixture.STORY_ID,
            decision = NewStoryLinkDecision(candidateStoryId = null, similarity = null),
            replayed = false
        )
    }
}
