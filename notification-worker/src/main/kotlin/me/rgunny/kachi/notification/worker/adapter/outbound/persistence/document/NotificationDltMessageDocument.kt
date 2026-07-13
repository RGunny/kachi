package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * notification dispatch DLT 메시지 MongoDB 저장 모델.
 */
@Document("notification_dlt_messages")
@CompoundIndexes(
    CompoundIndex(
        name = "ux_notification_dlt_original_position",
        def = "{'originalTopic': 1, 'originalPartition': 1, 'originalOffset': 1}",
        unique = true
    ),
    CompoundIndex(
        name = "idx_notification_dlt_status_failed_at",
        def = "{'status': 1, 'failedAt': -1}"
    )
)
data class NotificationDltMessageDocument(
    @Id
    val id: String,
    val originalTopic: String,
    val originalPartition: Int,
    val originalOffset: Long,
    val dltTopic: String,
    val dltPartition: Int,
    val dltOffset: Long,
    val consumerGroup: String?,
    val messageKey: String?,
    val payload: String,
    val exceptionFqcn: String?, // Fully Qualified Class Name
    val exceptionMessage: String?,
    val failedAt: Instant,
    val receivedAt: Instant,
    val status: String,
)
