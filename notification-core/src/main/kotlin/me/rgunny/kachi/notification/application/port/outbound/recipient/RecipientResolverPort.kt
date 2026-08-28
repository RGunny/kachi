package me.rgunny.kachi.notification.application.port.outbound.recipient

import me.rgunny.kachi.notification.application.port.outbound.recipient.model.ResolvedRecipient
import me.rgunny.kachi.notification.domain.NotificationChannel

/**
 * 발송 직전에 `(recipientId, channel)`을 수신 주소로 바꾸는 port.
 *
 * 주소가 없는 경우(바인딩 없음·연결 전·해지)는 결과로 돌려주고,
 * 조회 자체가 실패한 경우(원천 장애·응답 지연)는 [me.rgunny.kachi.notification.exception.recipient.RecipientResolveException]으로 던진다.
 * 두 경우는 알림의 종착이 다르다. 전자는 발송하지 않고 끝내고, 후자는 재시도한다.
 */
interface RecipientResolverPort {

    suspend fun resolve(recipientId: String, channel: NotificationChannel): ResolvedRecipient
}
