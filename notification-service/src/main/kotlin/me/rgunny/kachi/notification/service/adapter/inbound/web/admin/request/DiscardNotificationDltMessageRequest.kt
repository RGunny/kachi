package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.request

/**
 * DLT 메시지 폐기 요청 DTO.
 */
data class DiscardNotificationDltMessageRequest(
    val reason: String = DEFAULT_REASON,
) {
    companion object {
        const val DEFAULT_REASON = "manual dlt discard"
    }
}
