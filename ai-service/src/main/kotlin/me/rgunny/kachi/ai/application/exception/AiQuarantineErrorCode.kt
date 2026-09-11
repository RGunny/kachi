package me.rgunny.kachi.ai.application.exception

/**
 * 키워드 격리 운영 에러 코드.
 *
 * 격리 기록을 다루는 요청이 대상 기록을 찾지 못했거나, 찾은 기록이 해제할 수 있는 상태가 아닐 때 쓴다.
 */
enum class AiQuarantineErrorCode(
    override val code: String,
    override val message: String
) : AiErrorCode {
    QUARANTINE_NOT_FOUND(
        code = "QUARANTINE_NOT_FOUND",
        message = "키워드 격리 기록이 없습니다"
    ),
    QUARANTINE_NOT_RELEASABLE(
        code = "QUARANTINE_NOT_RELEASABLE",
        message = "격리 상태가 아닌 키워드는 해제할 수 없습니다"
    )
}
