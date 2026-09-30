package me.rgunny.kachi.story.adapter.inbound.outbox

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.port.inbound.outbox.RelayStoryOutboxUseCase
import me.rgunny.kachi.story.application.port.outbound.lock.model.StoryExecutionLock
import me.rgunny.kachi.story.application.port.outbound.lock.model.UnavailableExecutionLockOutcome
import me.rgunny.kachi.story.fake.BlockingRelayStoryOutboxUseCase
import me.rgunny.kachi.story.fake.FailingRelayStoryOutboxUseCase
import me.rgunny.kachi.story.fake.FakeExecutionLockPort
import me.rgunny.kachi.story.fake.RecordingRelayStoryOutboxUseCase
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryOutboxRelayExecutor")
class StoryOutboxRelayExecutorTest {

    @Test
    @DisplayName("실행 중인 relay가 있으면 중복 요청을 건너뛴다")
    fun skipWhenRelayIsAlreadyRunning() = runBlocking {
        val useCase = BlockingRelayStoryOutboxUseCase()
        val executor = executorOf(useCase)

        val firstExecution = async {
            executor.execute()
        }
        useCase.started.await()

        val secondExecution = executor.execute()
        useCase.release.complete(Unit)

        assertIs<AlreadyRunningStoryOutboxRelayExecution>(secondExecution)
        assertEquals(StoryTestFixture.NOW, secondExecution.holder.acquiredAt)
        assertIs<CompletedStoryOutboxRelayExecution>(firstExecution.await())
        assertEquals(1, useCase.executeCount)
    }

    @Test
    @DisplayName("실행 결과를 그대로 감싸 돌려준다")
    fun wrapRelayResult() = runBlocking {
        val useCase = RecordingRelayStoryOutboxUseCase()

        val execution = executorOf(useCase).execute()

        assertEquals(useCase.result, assertIs<CompletedStoryOutboxRelayExecution>(execution).result)
    }

    @Test
    @DisplayName("실행이 끝나면 다음 요청을 받을 수 있도록 lock을 돌려준다")
    fun releaseLockAfterExecution() = runBlocking {
        val useCase = RecordingRelayStoryOutboxUseCase()
        val executor = executorOf(useCase)

        executor.execute()
        executor.execute()

        assertEquals(2, useCase.invokeCount)
    }

    @Test
    @DisplayName("실행이 실패해도 lock을 돌려준다")
    fun releaseLockAfterFailure() = runBlocking {
        val useCase = FailingRelayStoryOutboxUseCase()
        val executor = executorOf(useCase)

        assertFailsWith<IllegalStateException> { executor.execute() }
        assertFailsWith<IllegalStateException> { executor.execute() }

        assertEquals(2, useCase.invokeCount)
    }

    @Test
    @DisplayName("lock을 확인할 수 없으면 relay를 실행하지 않는다")
    fun doNotRelayWhenLockIsUnavailable() = runBlocking {
        val useCase = RecordingRelayStoryOutboxUseCase()
        val cause = IllegalStateException("lock store unavailable")
        val lock = FakeExecutionLockPort(UnavailableExecutionLockOutcome(cause))

        val execution = StoryOutboxRelayExecutor(useCase, lock).execute()

        val unavailable = assertIs<UnavailableStoryOutboxRelayExecution>(execution)
        assertEquals(cause, unavailable.cause)
        assertEquals(0, useCase.invokeCount)
        assertEquals(StoryExecutionLock.OUTBOX_RELAY, lock.requestedTarget)
    }

    private fun executorOf(useCase: RelayStoryOutboxUseCase): StoryOutboxRelayExecutor {
        return StoryOutboxRelayExecutor(useCase, StoryTestFixture.executionLock())
    }
}
