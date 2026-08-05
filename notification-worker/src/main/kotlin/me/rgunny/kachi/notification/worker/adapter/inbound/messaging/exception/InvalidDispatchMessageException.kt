package me.rgunny.kachi.notification.worker.adapter.inbound.messaging.exception

class InvalidDispatchMessageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
