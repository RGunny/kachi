package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.request

/**
 * DEAD notification 수동 복구 요청 DTO.
 */
data class RecoverDeadNotificationRequest(
    val reason: String = DEFAULT_REASON,
) {
    companion object {
        const val DEFAULT_REASON = "manual notification recovery"
    }
}
