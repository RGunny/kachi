package me.rgunny.kachi.notification.fake

import java.time.Instant
import me.rgunny.kachi.notification.application.port.outbound.outbox.NotificationOutboxPersistencePort
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxId

class FakeOutboxPersistencePort(
    private val publishable: List<NotificationOutbox> = emptyList(),
    private val stale: List<NotificationOutbox> = emptyList(),
    private val dead: List<NotificationOutbox> = emptyList(),
    private val claimEnabled: Boolean = true,
) : NotificationOutboxPersistencePort {
    val saved = mutableListOf<NotificationOutbox>()

    /**
     * 저장된 행. 불변 aggregate라 전이 결과를 여기에 교체 저장해야 다음 조회가 새 상태를 본다.
     */
    private val storedById = (publishable + stale + dead).associateBy { it.id }.toMutableMap()

    /**
     * 다른 tick이 이 행을 회수해 claim이 바뀐 상황을 만든다.
     */
    fun replaceStored(outbox: NotificationOutbox) {
        storedById[outbox.id] = outbox
    }

    override suspend fun save(outbox: NotificationOutbox): NotificationOutbox {
        saved += outbox
        storedById[outbox.id] = outbox
        return outbox
    }

    override suspend fun findById(outboxId: NotificationOutboxId): NotificationOutbox? {
        return storedById[outboxId]
    }

    override suspend fun findDead(batchSize: Int): List<NotificationOutbox> {
        return dead.take(batchSize)
    }

    override suspend fun findPublishable(now: Instant, batchSize: Int): List<NotificationOutbox> {
        return publishable.take(batchSize)
    }

    override suspend fun claimPublishing(
        outboxId: NotificationOutboxId,
        claimedBy: String,
        now: Instant,
    ): NotificationOutbox? {
        if (!claimEnabled) {
            return null
        }
        val outbox = publishable.firstOrNull { it.id == outboxId } ?: return null
        val claimed = outbox.markPublishing(now, claimedBy)
        storedById[claimed.id] = claimed
        return claimed
    }

    override suspend fun findStalePublishing(
        threshold: Instant,
        batchSize: Int,
    ): List<NotificationOutbox> {
        return stale.take(batchSize)
    }
}
