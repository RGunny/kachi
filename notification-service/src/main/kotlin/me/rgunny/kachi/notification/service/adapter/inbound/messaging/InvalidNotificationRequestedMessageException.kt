package me.rgunny.kachi.notification.service.adapter.inbound.messaging

class InvalidNotificationRequestedMessageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
