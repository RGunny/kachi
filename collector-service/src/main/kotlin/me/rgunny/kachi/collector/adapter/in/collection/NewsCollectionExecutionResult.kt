package me.rgunny.kachi.collector.adapter.`in`.collection

import me.rgunny.kachi.collector.application.port.`in`.CollectionRunResult
import java.time.Instant

/**
 * 현재 인스턴스에서 실행 중인 뉴스 수집 작업의 최소 메타데이터다.
 *
 * CollectionRunId는 CollectNewsService 내부에서 생성되므로,
 * 중복 실행으로 건너뛴 요청에는 아직 알 수 없다.
 */
data class RunningNewsCollection(
    val startedAt: Instant
)

/**
 * 뉴스 수집 요청의 처리 상태 결과.
 *
 * 결과는 아래로 나뉜다.
 * - 실제 수집을 실행한 상태
 * - 이미 실행 중이라 요청을 건너뛴 상태
 *
 * sealed interface로 가능한 결과를 닫아
 * scheduler/API 같은 호출자가 when 분기에서 모든 결과를 명시적으로 처리할 수 있다.
 */
sealed interface NewsCollectionExecutionResult {

    /**
     * 중복 실행 방지 lock을 획득했고 수집 유스케이스가 실행된 결과다.
     */
    data class Started(
        val result: CollectionRunResult
    ) : NewsCollectionExecutionResult

    /**
     * 같은 인스턴스에서 다른 뉴스 수집이 이미 실행 중이라 이번 요청을 건너뛴 상태다.
     */
    data class AlreadyRunning(
        val runningCollection: RunningNewsCollection
    ) : NewsCollectionExecutionResult
}
