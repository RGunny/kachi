package me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram.dto

/**
 * TelegramSendMessageClient가 Telegram API 응답을 sender 분류에 필요한 값으로 변환한 내부 결과 모델.
 */
internal data class TelegramSendMessageResult(
    val statusCode: Int,
    val ok: Boolean,
    val errorCode: Int?,
    val description: String?,
    val retryAfterMillis: Long?,
)

