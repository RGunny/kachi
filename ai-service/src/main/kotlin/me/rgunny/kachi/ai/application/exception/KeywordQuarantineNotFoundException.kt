package me.rgunny.kachi.ai.application.exception

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.run.AiRunTargetType

/**
 * 해제 대상 격리 기록이 없을 때의 실패.
 *
 * 기록이 없다는 것은 그 키워드가 한 번도 실패하지 않았다는 뜻이므로 해제할 대상 자체가 없다.
 */
class KeywordQuarantineNotFoundException(
    targetType: AiRunTargetType,
    keyword: AiKeyword
) : AiException(
    errorCode = AiQuarantineErrorCode.QUARANTINE_NOT_FOUND,
    message = messageOf(
        AiQuarantineErrorCode.QUARANTINE_NOT_FOUND,
        "targetType=$targetType, keyword=${keyword.value}"
    )
)
