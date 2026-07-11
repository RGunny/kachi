package me.rgunny.kachi.notification.application.port.dto

import java.time.Instant

/**
 * PROCESSING 상태로 멈춘 dispatch 회수 결과.
 */
data class RecoverStaleProcessingDispatchResult(
    val processed: Int,
    val retryWait: Int,
    val dead: Int,
    val handledAt: Instant,
)
