package me.rgunny.kachi.notification.service.adapter.inbound.web.response

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
    INVALID_FORMAT(HttpStatus.BAD_REQUEST, "요청 본문을 해석할 수 없습니다"),
    INVALID_DOMAIN_INPUT(HttpStatus.BAD_REQUEST, "도메인 입력이 올바르지 않습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다"),
    DOMAIN_INVARIANT(HttpStatus.CONFLICT, "도메인 상태 전이 위반"),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "이미 처리된 요청입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),
}
