package me.rgunny.kachi.notification.worker.adapter.inbound.messaging.exception

import me.rgunny.kachi.notification.application.port.dto.DispatchNotificationResult

class RetryableDispatchMessageException(
    val result: DispatchNotificationResult,
) : RuntimeException(
    "retryable notification dispatch failure. notificationId=${result.notificationId.id}, status=${result.status}, failure=${result.failure?.code}"
)
