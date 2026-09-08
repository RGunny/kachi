package me.rgunny.kachi.collector.application.exception

import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxId

/**
 * 복구 대상 outbox 행이 없을 때의 실패.
 */
class CollectorOutboxNotFoundException(
    id: CollectorOutboxId
) : CollectorException(
    errorCode = CollectorOutboxErrorCode.OUTBOX_NOT_FOUND,
    message = messageOf(CollectorOutboxErrorCode.OUTBOX_NOT_FOUND, "id=${id.value}")
)
