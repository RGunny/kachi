package me.rgunny.kachi.story.adapter.outbound.lock

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutedExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockScope
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockTarget
import me.rgunny.kachi.story.application.port.outbound.lock.model.StoryExecutionLock
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RoutingExecutionLockAdapter")
class RoutingExecutionLockAdapterTest {

    @Test
    @DisplayName("실행 단위가 선언한 범위의 구현으로 넘긴다")
    fun routeByDeclaredScope() = runBlocking {
        val instanceLock = RecordingExecutionLockPort()
        val clusterLock = RecordingExecutionLockPort()
        val adapter = RoutingExecutionLockAdapter(
            delegates = mapOf(
                ExecutionLockScope.INSTANCE to instanceLock,
                ExecutionLockScope.CLUSTER to clusterLock
            )
        )

        adapter.withLock<Unit>(StoryExecutionLock.STORY_CLOSE) { }
        adapter.withLock<Unit>(StoryExecutionLock.OUTBOX_RELAY) { }

        assertEquals(listOf<ExecutionLockTarget>(StoryExecutionLock.STORY_CLOSE), clusterLock.targets)
        assertEquals(listOf<ExecutionLockTarget>(StoryExecutionLock.OUTBOX_RELAY), instanceLock.targets)
    }

    @Test
    @DisplayName("구현이 연결되지 않은 범위가 있으면 만들어지지 않는다")
    fun rejectUnmappedScope() {
        val error = assertFailsWith<IllegalArgumentException> {
            RoutingExecutionLockAdapter(
                delegates = mapOf(ExecutionLockScope.INSTANCE to RecordingExecutionLockPort())
            )
        }

        assertEquals(true, error.message?.contains(ExecutionLockScope.CLUSTER.name))
    }

    /**
     * 어떤 대상이 자기에게 왔는지만 기록하는 실행 lock.
     */
    private class RecordingExecutionLockPort : ExecutionLockPort {
        val targets = mutableListOf<ExecutionLockTarget>()

        override suspend fun <T> withLock(
            target: ExecutionLockTarget,
            action: suspend () -> T
        ): ExecutionLockOutcome<T> {
            targets += target

            return ExecutedExecutionLockOutcome(action())
        }
    }
}
