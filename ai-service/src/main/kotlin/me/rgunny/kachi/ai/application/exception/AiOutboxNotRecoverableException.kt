package me.rgunny.kachi.ai.application.exception

import me.rgunny.kachi.ai.domain.outbox.AiOutboxId
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus

/**
 * DEAD가 아닌 행을 복구하려 했을 때의 실패.
 *
 * 조회 시점에는 DEAD였어도 저장 조건에 걸려 실패할 수 있다. 그때는 다른 복구가 먼저 끝났다는 뜻이다.
 */
class AiOutboxNotRecoverableException(
    id: AiOutboxId,
    status: AiOutboxStatus
) : AiException(
    errorCode = AiOutboxErrorCode.OUTBOX_NOT_RECOVERABLE,
    message = messageOf(AiOutboxErrorCode.OUTBOX_NOT_RECOVERABLE, "id=${id.value}, status=$status")
)
