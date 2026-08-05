package me.rgunny.kachi.notification.worker.adapter.outbound.idempotency

class IdempotencyKeyCreationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
