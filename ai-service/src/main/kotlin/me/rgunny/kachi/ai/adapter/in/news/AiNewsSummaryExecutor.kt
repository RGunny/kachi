package me.rgunny.kachi.ai.adapter.`in`.news

import me.rgunny.kachi.ai.application.port.dto.news.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsUseCase
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * scheduler와 internal API에서 들어온 뉴스 요약 요청을 받아 중복 실행을 막고 유스케이스를 호출한다.
 *
 * 현재는 단일 인스턴스 실행을 가정하므로 JVM 내부 lock으로 중복 실행을 막는다.
 * 분산 실행이 필요해지면 이 클래스의 lock 획득/해제 책임을 distributed lock으로 교체한다.
 */
@Component
class AiNewsSummaryExecutor(
    private val summarizeNewsUseCase: SummarizeNewsUseCase,
    private val clock: Clock
) {
    private val runningSummary = AtomicReference<RunningAiNewsSummary?>(null)

    suspend fun execute(command: SummarizeNewsCommand): AiNewsSummaryExecutionResult {
        // 1. 현재 요청이 lock을 획득했을 때 기록할 실행 시작 메타데이터를 만든다.
        val currentSummary = RunningAiNewsSummary(
            startedAt = Instant.now(clock)
        )

        // 2. 이미 뉴스 요약이 실행 중이면 유스케이스를 호출하지 않고 중복 실행 결과를 반환한다.
        if (!runningSummary.compareAndSet(null, currentSummary)) {
            return AiNewsSummaryExecutionResult.AlreadyRunning(
                runningSummary = runningSummary.get() ?: currentSummary
            )
        }

        // 3. lock을 획득한 요청만 실제 뉴스 요약 유스케이스를 실행한다.
        return try {
            AiNewsSummaryExecutionResult.Started(summarizeNewsUseCase.summarize(command))
        } finally {
            // 4. 성공/실패와 무관하게 다음 실행을 받을 수 있도록 lock을 해제한다.
            runningSummary.compareAndSet(currentSummary, null)
        }
    }
}
