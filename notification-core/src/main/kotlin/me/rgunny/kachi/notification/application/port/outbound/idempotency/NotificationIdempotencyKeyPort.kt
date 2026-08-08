package me.rgunny.kachi.notification.application.port.outbound.idempotency

import me.rgunny.kachi.notification.domain.NotificationId
import java.time.Duration

/**
 * 외부 vendor 호출용 idempotency key 저장 port.
 *
 * 같은 알림을 재시도할 때 동일한 vendor idempotency key를 재사용하기 위해 사용한다.
 */
interface NotificationIdempotencyKeyPort {

    suspend fun getOrCreate(
        notificationId: NotificationId,
        ttl: Duration,
    ): String
}
