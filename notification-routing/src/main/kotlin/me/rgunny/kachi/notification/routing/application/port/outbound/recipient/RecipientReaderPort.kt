package me.rgunny.kachi.notification.routing.application.port.outbound.recipient

import me.rgunny.kachi.notification.routing.application.port.outbound.recipient.model.Recipient

/**
 * 수신자 조회 port.
 *
 * 구현체는 발송 가능한 (수신자, 채널)만 돌려준다. 없는 키워드, 관리자 없음은 빈 목록이다.
 * 조회 실패는 RecipientReaderException으로 던진다.
 */
interface RecipientReaderPort {

    /** 키워드 구독자 x 채널. */
    suspend fun findSubscribers(keyword: String): List<Recipient>

    /** 관리자 x 채널. */
    suspend fun findAdmins(): List<Recipient>
}
