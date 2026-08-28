package me.rgunny.kachi.notification.application.port.outbound.recipient.model

/**
 * `(recipientId, channel)` 조회 결과. 주소가 있으면 [AvailableRecipient], 없으면 사유를 담은 [UnavailableRecipient]다.
 */
sealed interface ResolvedRecipient
