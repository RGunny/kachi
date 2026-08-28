package me.rgunny.kachi.notification.application.port.outbound.recipient.model

/**
 * 주소가 없어 발송할 수 없는 수신자. 알림은 이 사유로 SUPPRESSED가 된다.
 */
data class UnavailableRecipient(
    val reason: RecipientUnavailableReason,
) : ResolvedRecipient
