package me.rgunny.kachi.collector.adapter.`in`.scheduler

import jakarta.annotation.PostConstruct
import me.rgunny.kachi.collector.adapter.`in`.collection.NewsCollectionExecutionResult
import me.rgunny.kachi.collector.adapter.`in`.collection.NewsCollectionExecutor
import me.rgunny.kachi.collector.application.port.`in`.CollectNewsCommand
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 설정된 주기마다 뉴스 수집을 자동 실행하는 scheduler.
 *
 * 실제 중복 실행 방지와 유스케이스 호출은 NewsCollectionExecutor에 위임한다.
 */
@Component
@EnableConfigurationProperties(NewsCollectionSchedulerProperties::class)
class NewsCollectionScheduler(
    private val executor: NewsCollectionExecutor,
    private val properties: NewsCollectionSchedulerProperties
) {
    @PostConstruct
    fun logSchedulerProperties() {
        log.info(
            "News collection scheduler configured: enabled={}, initialDelay={}, fixedDelay={}",
            properties.enabled,
            properties.initialDelay,
            properties.fixedDelay
        )
    }

    @Scheduled(
        fixedDelayString = "\${kachi.collector.scheduler.news.fixed-delay:10m}",
        initialDelayString = "\${kachi.collector.scheduler.news.initial-delay:30s}"
    )
    suspend fun collectNews() {
        // 1. local/test처럼 자동 외부 호출을 피해야 하는 환경에서는 scheduler 실행을 건너뛴다.
        if (!properties.enabled) {
            return
        }

        // 2. @Scheduled suspend 함수에서 수집 유스케이스를 직접 호출한다.
        log.info("Scheduled news collection started")
        runCatching {
            executor.execute(CollectNewsCommand(keywords = emptyList()))
        }.onSuccess { result ->
            // 3. scheduler는 이미 실행 중인 수집을 실패로 보지 않고 이번 tick만 skip한다.
            when (result) {
                is NewsCollectionExecutionResult.AlreadyRunning ->
                    log.info(
                        "Skip scheduled news collection because another collection is running: startedAt={}",
                        result.runningCollection.startedAt
                    )

                is NewsCollectionExecutionResult.Started ->
                    log.info(
                        "Scheduled news collection finished: runId={}, status={}, collected={}, duplicated={}, failures={}",
                        result.result.id.value,
                        result.result.status,
                        result.result.collectedCount,
                        result.result.duplicateCount,
                        result.result.failureCount
                    )
            }
        }.onFailure { error ->
            // 4. scheduler 루프가 중단되지 않도록 예외는 로그로 남기고 삼킨다.
            log.warn("Scheduled news collection failed", error)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(NewsCollectionScheduler::class.java)
    }
}
