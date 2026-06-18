package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

data class NotificationDispatchPayload(
    val notificationId: String,
    val requestId: String,
    val channel: String,
    val recipient: String,
    val message: String,
)
