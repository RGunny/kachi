package me.rgunny.kachi.ai.application.exception

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineStatus

/**
 * 격리 상태가 아닌 기록을 해제하려 했을 때의 실패.
 */
class KeywordQuarantineNotReleasableException(
    keyword: AiKeyword,
    status: KeywordQuarantineStatus
) : AiException(
    errorCode = AiQuarantineErrorCode.QUARANTINE_NOT_RELEASABLE,
    message = messageOf(
        AiQuarantineErrorCode.QUARANTINE_NOT_RELEASABLE,
        "keyword=${keyword.value}, status=$status"
    )
)
