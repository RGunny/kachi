package me.rgunny.kachi.notification.application.port.outbound.recipient.model

/**
 * 발송 가능한 수신자. [address]는 채널별 형식의 수신 주소 문자열이다.
 */
data class AvailableRecipient(
    val address: String,
) : ResolvedRecipient {
    init {
        require(address.isNotBlank()) { "address must not be blank" }
    }
}
