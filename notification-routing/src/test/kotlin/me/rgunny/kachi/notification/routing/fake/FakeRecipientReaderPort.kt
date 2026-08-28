package me.rgunny.kachi.notification.routing.fake

import me.rgunny.kachi.notification.routing.application.port.outbound.recipient.RecipientReaderPort
import me.rgunny.kachi.notification.routing.application.port.outbound.recipient.model.Recipient

/**
 * 고정 목록을 돌려주는 수신자 조회 대역. 물어본 키워드와 관리자 조회 횟수를 남긴다.
 */
class FakeRecipientReaderPort(
    private val subscribers: List<Recipient> = emptyList(),
    private val admins: List<Recipient> = emptyList(),
) : RecipientReaderPort {
    val requestedKeywords = mutableListOf<String>()
    var adminRequests = 0
        private set
    var failure: RuntimeException? = null

    override suspend fun findSubscribers(keyword: String): List<Recipient> {
        requestedKeywords += keyword
        failure?.let { throw it }
        return subscribers
    }

    override suspend fun findAdmins(): List<Recipient> {
        adminRequests += 1
        failure?.let { throw it }
        return admins
    }
}
