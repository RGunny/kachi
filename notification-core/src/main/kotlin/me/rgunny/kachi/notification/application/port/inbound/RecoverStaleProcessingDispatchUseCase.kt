package me.rgunny.kachi.notification.application.port.inbound

import me.rgunny.kachi.notification.application.port.dto.RecoverStaleProcessingDispatchResult

/**
 * PROCESSING 상태로 오래 남은 dispatch를 회수하는 유스케이스.
 */
interface RecoverStaleProcessingDispatchUseCase {

    suspend fun recoverStaleProcessing(): RecoverStaleProcessingDispatchResult
}
