package me.rgunny.kachi.story.config

import java.time.Clock
import me.rgunny.kachi.story.adapter.outbound.lock.InMemoryExecutionLockAdapter
import me.rgunny.kachi.story.adapter.outbound.lock.RoutingExecutionLockAdapter
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockScope
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 실행 lock의 범위를 구현에 연결한다. */
@Configuration
class ExecutionLockConfig {

    @Bean
    fun executionLockPort(clock: Clock): ExecutionLockPort {
        val inMemoryLock = InMemoryExecutionLockAdapter(clock)

        return RoutingExecutionLockAdapter(
            delegates = mapOf(
                ExecutionLockScope.INSTANCE to inMemoryLock,
                ExecutionLockScope.CLUSTER to inMemoryLock
            )
        )
    }
}
