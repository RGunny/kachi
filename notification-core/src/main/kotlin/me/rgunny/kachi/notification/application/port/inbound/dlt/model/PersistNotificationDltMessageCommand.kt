package me.rgunny.kachi.notification.application.port.inbound.dlt.model

import java.time.Instant

/**
 * Kafka DLT record를 운영 저장소에 보관하기 위한 command.
 */
data class PersistNotificationDltMessageCommand(
    val originalTopic: String,
    val originalPartition: Int,
    val originalOffset: Long,
    val originalTimestamp: Instant?,
    val dltTopic: String,
    val dltPartition: Int,
    val dltOffset: Long,
    val consumerGroup: String?,
    val messageKey: String?,
    val payload: String,
    val exceptionFqcn: String?,
    val exceptionMessage: String?,
    val deadLetteredAt: Instant,
    val storedAt: Instant,
)
