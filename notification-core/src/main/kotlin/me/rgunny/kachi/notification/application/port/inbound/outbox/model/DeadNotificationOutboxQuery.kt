package me.rgunny.kachi.notification.application.port.inbound.outbox.model

data class DeadNotificationOutboxQuery(
    val batchSize: Int,
) {
    init {
        require(batchSize in 1..500) { "batchSize must be between 1 and 500" }
    }
}
