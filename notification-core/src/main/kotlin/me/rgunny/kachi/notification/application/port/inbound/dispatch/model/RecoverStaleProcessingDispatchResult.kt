package me.rgunny.kachi.notification.application.port.inbound.dispatch.model

import java.time.Instant

/**
 * PROCESSING 상태로 멈춘 dispatch 회수 결과.
 */
data class RecoverStaleProcessingDispatchResult(
    /**
     * visibility timeout 기준으로 조회된 stale PROCESSING 후보 수.
     */
    val staleProcessingFound: Int,
    /**
     * claim CAS 조건이 맞아 RETRY_WAIT 또는 DEAD로 실제 회수된 수.
     */
    val staleProcessingRecovered: Int,
    /**
     * 회수에 성공해 RETRY_WAIT로 전이된 수.
     */
    val recoveredToRetryWait: Int,
    /**
     * 회수에 성공해 DEAD로 전이된 수.
     */
    val recoveredToDead: Int,
    /**
     * 후보로 조회됐지만 claim CAS 조건 불일치 등으로 저장하지 않은 수.
     */
    val staleProcessingSkipped: Int,
    /**
     * stale PROCESSING recovery 주기를 처리하고 이 결과를 만든 시각.
     */
    val recoveryTickCompletedAt: Instant,
)
