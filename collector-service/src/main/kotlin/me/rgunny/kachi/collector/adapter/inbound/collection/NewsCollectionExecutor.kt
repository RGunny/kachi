package me.rgunny.kachi.collector.adapter.inbound.collection

import me.rgunny.kachi.collector.application.port.inbound.collection.model.CollectNewsCommand
import me.rgunny.kachi.collector.application.port.inbound.collection.CollectNewsUseCase
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * scheduler와 internal API에서 들어온 뉴스 수집 요청을 받아 중복 실행을 막고 수집 유스케이스를 호출한다.
 *
 * 현재는 단일 인스턴스 실행을 가정하므로 JVM 내부 lock으로 중복 실행을 막는다.
 * 분산 실행이 필요해지면 이 클래스의 lock 획득/해제 책임을 distributed lock으로 교체한다.
 */
@Component
class NewsCollectionExecutor(
    private val collectNewsUseCase: CollectNewsUseCase,
    private val clock: Clock
) {
    private val runningCollection = AtomicReference<RunningNewsCollection?>(null)

    suspend fun execute(command: CollectNewsCommand): NewsCollectionExecutionResult {
        val currentCollection = runningCollectionOf(command)

        // 1. 이미 수집 중이면 유스케이스를 호출하지 않고 중복 실행 결과를 반환한다.
        if (!runningCollection.compareAndSet(null, currentCollection)) {
            return NewsCollectionExecutionResult.AlreadyRunning(
                runningCollection = runningCollection.get() ?: currentCollection
            )
        }

        // 2. lock을 획득한 요청만 실제 수집 유스케이스를 실행한다.
        return try {
            NewsCollectionExecutionResult.Started(collectNewsUseCase.collect(command))
        } finally {
            // 3. 성공/실패와 무관하게 다음 실행을 받을 수 있도록 lock을 해제한다.
            runningCollection.compareAndSet(currentCollection, null)
        }
    }

    private fun runningCollectionOf(command: CollectNewsCommand): RunningNewsCollection {
        return RunningNewsCollection(
            startedAt = Instant.now(clock)
        )
    }
}
