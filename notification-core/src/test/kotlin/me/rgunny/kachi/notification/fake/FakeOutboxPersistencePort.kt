package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.NotificationOutboxPersistencePort
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxId
import java.time.Instant

class FakeOutboxPersistencePort(
    private val publishable: List<NotificationOutbox> = emptyList(),
    private val stale: List<NotificationOutbox> = emptyList(),
    private val claimEnabled: Boolean = true,
) : NotificationOutboxPersistencePort {
    val saved = mutableListOf<NotificationOutbox>()

    override suspend fun save(outbox: NotificationOutbox): NotificationOutbox {
        saved += outbox
        return outbox
    }

    override suspend fun findById(outboxId: NotificationOutboxId): NotificationOutbox? {
        return (publishable + stale).firstOrNull { it.id == outboxId }
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
        outbox.markPublishing(now, claimedBy)
        return outbox
    }

    override suspend fun findStalePublishing(
        threshold: Instant,
        batchSize: Int,
    ): List<NotificationOutbox> {
        return stale.take(batchSize)
    }
}
