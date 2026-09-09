package me.rgunny.kachi.story.adapter.outbound.lock

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.port.outbound.lock.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutedExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.StoryExecutionLock
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("InMemoryExecutionLockAdapter")
class InMemoryExecutionLockAdapterTest {
    private val adapter = InMemoryExecutionLockAdapter(StoryTestFixture.CLOCK)

    @Test
    @DisplayName("먼저 들어온 요청만 실행하고 나중 요청은 쥔 시각과 함께 막는다")
    fun blockSecondRequestWhileFirstIsRunning() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()

        val first = async {
            adapter.withLock(StoryExecutionLock.STORY_CLOSE) {
                started.complete(Unit)
                complete.await()
                "실행됨"
            }
        }
        started.await()

        var secondActionCalled = false
        val second = adapter.withLock(StoryExecutionLock.STORY_CLOSE) { secondActionCalled = true }
        complete.complete(Unit)

        val blocked = assertIs<AlreadyHeldExecutionLockOutcome>(second)
        assertEquals(StoryTestFixture.NOW, blocked.holder.acquiredAt)
        assertEquals(false, secondActionCalled)
        assertEquals("실행됨", assertIs<ExecutedExecutionLockOutcome<String>>(first.await()).value)
    }

    @Test
    @DisplayName("실행이 끝나면 다음 요청이 lock을 얻는다")
    fun releaseLockAfterActionFinished() = runBlocking {
        val first = adapter.withLock(StoryExecutionLock.STORY_CLOSE) { 1 }
        val second = adapter.withLock(StoryExecutionLock.STORY_CLOSE) { 2 }

        assertEquals(1, assertIs<ExecutedExecutionLockOutcome<Int>>(first).value)
        assertEquals(2, assertIs<ExecutedExecutionLockOutcome<Int>>(second).value)
    }

    @Test
    @DisplayName("작업이 예외로 끝나도 lock을 풀고 예외를 그대로 올린다")
    fun releaseLockWhenActionThrows() = runBlocking {
        assertFailsWith<IllegalStateException> {
            adapter.withLock(StoryExecutionLock.STORY_CLOSE) { error("작업 실패") }
        }

        val next = adapter.withLock(StoryExecutionLock.STORY_CLOSE) { "다음 실행" }

        assertEquals("다음 실행", assertIs<ExecutedExecutionLockOutcome<String>>(next).value)
    }

    @Test
    @DisplayName("다른 실행 단위는 서로 막지 않는다")
    fun doNotBlockDifferentTarget() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()

        val close = async {
            adapter.withLock(StoryExecutionLock.STORY_CLOSE) {
                started.complete(Unit)
                complete.await()
            }
        }
        started.await()

        val relay = adapter.withLock(StoryExecutionLock.OUTBOX_RELAY) { "relay 실행" }
        complete.complete(Unit)
        close.await()

        assertEquals("relay 실행", assertIs<ExecutedExecutionLockOutcome<String>>(relay).value)
    }
}
