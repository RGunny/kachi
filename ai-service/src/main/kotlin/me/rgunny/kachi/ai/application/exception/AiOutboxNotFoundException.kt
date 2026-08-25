package me.rgunny.kachi.ai.application.exception

import me.rgunny.kachi.ai.domain.outbox.AiOutboxId

/**
 * 복구 대상 outbox 행이 없을 때의 실패.
 */
class AiOutboxNotFoundException(
    id: AiOutboxId
) : AiException(
    errorCode = AiOutboxErrorCode.OUTBOX_NOT_FOUND,
    message = messageOf(AiOutboxErrorCode.OUTBOX_NOT_FOUND, "id=${id.value}")
)
