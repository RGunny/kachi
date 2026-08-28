package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

/**
 * 캐시에 저장하는 조회 결과의 JSON 형태. available이면 address, 아니면 reason이 있다.
 */
internal data class CachedResolvedRecipient(
    val available: Boolean,
    val address: String?,
    val reason: String?,
)
