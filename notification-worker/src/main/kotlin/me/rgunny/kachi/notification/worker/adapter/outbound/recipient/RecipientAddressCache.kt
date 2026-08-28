package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

import me.rgunny.kachi.notification.application.port.outbound.recipient.model.ResolvedRecipient
import me.rgunny.kachi.notification.domain.NotificationChannel
import java.time.Duration

/**
 * `(recipientId, channel)` 조회 결과를 잠시 들고 있는 캐시. 저장소 접근을 [CachedRecipientResolver]에서 떼어 낸 경계다.
 *
 * 읽기·쓰기 실패는 예외로 그대로 나간다. 삼키면 캐시 장애가 원천 부하로 바뀐다.
 */
interface RecipientAddressCache {

    suspend fun get(recipientId: String, channel: NotificationChannel): ResolvedRecipient?

    suspend fun put(recipientId: String, channel: NotificationChannel, resolved: ResolvedRecipient, ttl: Duration)
}
