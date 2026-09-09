package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.dlt

import java.time.Instant
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * notification dispatch DLT 메시지 MongoDB 저장 모델.
 *
 * 시간 필드는 원본 record, DLT record, 저장, 상태별 운영 종료 시각을 분리해 보관한다.
 */
@Document("notification_dlt_messages")
@CompoundIndexes(
    CompoundIndex(
        name = "ux_notification_dlt_original_position",
        def = "{'originalTopic': 1, 'originalPartition': 1, 'originalOffset': 1}",
        unique = true
    ),
    CompoundIndex(
        name = "idx_notification_dlt_status_dead_lettered_at",
        def = "{'status': 1, 'deadLetteredAt': -1}"
    )
)
data class NotificationDltMessageDocument(
    @Id
    val id: String,
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
    val discardedAt: Instant?,
    val discardReason: String?,
    val reprocessedAt: Instant?,
    val reprocessReason: String?,
    val status: String,
)
